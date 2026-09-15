package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BidContentFinalizationServiceTest {

    private static final String TASK = "task-1";
    private static final String OWNER = "owner-1";
    private static final String BID = "bid-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 10, 0);

    private BidRepository bidRepository;
    private BidProductionRepository productionRepository;
    private BidContentReviewService reviewService;
    private BidAiExecutionService aiExecutionService;
    private BidStyleEditorialService styleEditorialService;
    private BidLayoutProcessor layoutProcessor;
    private BidContentFinalizationService service;
    private final AtomicReference<BidWorkspace> stored = new AtomicReference<>();

    private final BidDocument bid = new BidDocument(BID, OWNER, new TenantScope("org-1", "ws-1"), "B-1",
            "SCORING_CRITERIA", "智慧园区投标文件", 120, "OPEN", "CONTENT", "GENERATING",
            false, null, NOW, NOW, 3);
    private final BidWorkspace.Chapter chapter = new BidWorkspace.Chapter(
            "c1", "n1", "总体设计", "<p>总体设计正文</p>", "READY", NOW, 7);
    private final BidGenerationSnapshot snapshot = new BidGenerationSnapshot("snap-1", TASK, BID, OWNER, "hash",
            "智慧园区投标文件", "OPEN", 120, 1, 1, "", "", "", "", "v1", NOW,
            List.of(), List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        productionRepository = mock(BidProductionRepository.class);
        reviewService = mock(BidContentReviewService.class);
        aiExecutionService = mock(BidAiExecutionService.class);
        styleEditorialService = mock(BidStyleEditorialService.class);
        layoutProcessor = mock(BidLayoutProcessor.class);
        service = new BidContentFinalizationService(bidRepository, productionRepository, reviewService,
                aiExecutionService, styleEditorialService, layoutProcessor);
        when(bidRepository.findBidForTask(BID, OWNER)).thenReturn(Optional.of(bid));
        when(bidRepository.loadWorkspace(bid)).thenAnswer(invocation -> stored.get());
        // 冻结改写仓储里的成稿状态，之后再读工作区看到的是冻结后的哈希——和真实仓储一致。
        doAnswer(invocation -> {
            BidWorkspace current = stored.get();
            stored.set(workspace(production("FROZEN", invocation.getArgument(1), List.of(),
                    current.production().layoutJob()), current.exports()));
            return null;
        }).when(productionRepository).freezeContent(anyString(), anyString());
        when(productionRepository.createLayoutJob(anyString(), anyString(), anyString(), anyInt())).thenReturn("layout-new");
        when(productionRepository.applyReviewedChapter(anyString(), anyString(), anyLong(), anyString())).thenReturn(true);
    }

    @Test
    void reusesOnlySuccessfulLayoutForCurrentFrozenContentHash() {
        BidProductionState.LayoutJob current = layout("SUCCEEDED", "current-hash");
        BidProductionState.LayoutJob stale = layout("SUCCEEDED", "old-hash");

        assertThat(BidContentFinalizationService.layoutMatchesContent(
                current, "FROZEN", "current-hash")).isTrue();
        assertThat(BidContentFinalizationService.layoutMatchesContent(
                stale, "FROZEN", "current-hash")).isFalse();
        assertThat(BidContentFinalizationService.layoutMatchesContent(
                current, "GENERATING", "current-hash")).isFalse();
        assertThat(BidContentFinalizationService.layoutMatchesContent(
                current, "FROZEN", null)).isFalse();
    }

    /** 重放或重试时成稿与排版都已就绪：只收尾任务，不再审一遍、不再排一遍版。 */
    @Test
    void anAlreadyLaidOutFrozenContentOnlyClosesTheTask() {
        String hash = hashOf(chapter);
        stored.set(workspace(
                production("FROZEN", hash, List.of(), layout("SUCCEEDED", hash)), List.of(export())));

        service.complete(TASK, OWNER, snapshot, units());

        verify(bidRepository).completeGenerationTask(TASK, BID);
        verifyNoInteractions(reviewService, aiExecutionService, styleEditorialService, layoutProcessor);
    }

    @Test
    void aCleanReviewFreezesTheContentLaysItOutAndClosesTheTask() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any())).thenReturn(List.of());

        service.complete(TASK, OWNER, snapshot, units());

        verify(styleEditorialService).polish(eq(TASK), eq(OWNER), eq(snapshot), any());
        verify(productionRepository).appendEvent(TASK, BID, null, "AUTO_REVIEW_PASSED", "第 1 轮自动审查通过");
        verify(productionRepository).freezeContent(BID, hashOf(chapter));
        verify(productionRepository).createLayoutJob(BID, OWNER, hashOf(chapter), 120);
        verify(layoutProcessor).process("layout-new", BID, OWNER);
        verify(bidRepository).completeGenerationTask(TASK, BID);
        verifyNoInteractions(aiExecutionService);
    }

    @Test
    void warningsAndResolvedErrorsDoNotBlock() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any())).thenReturn(List.of(
                issue("c1", "WARNING", "OPEN"), issue("c1", "ERROR", "RESOLVED")));

        service.complete(TASK, OWNER, snapshot, units());

        verifyNoInteractions(aiExecutionService);
        verify(bidRepository).completeGenerationTask(TASK, BID);
    }

    /** 阻断问题按章节交给模型修订，修订版本另存一版，下一轮复审通过后照常冻结排版。 */
    @Test
    void revisesTheChapterWithABlockingIssueThenPassesOnTheNextRound() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any()))
                .thenReturn(List.of(issue("c1", "ERROR", "OPEN")))
                .thenReturn(List.of());
        when(aiExecutionService.revise(eq(BID), eq(TASK), eq("snap-1"), eq("auto-review-revision-v1"), any()))
                .thenReturn(candidate("<p>修订后的总体设计</p>"));

        service.complete(TASK, OWNER, snapshot, units());

        ArgumentCaptor<TenderAiGateway.RevisionRequest> request = ArgumentCaptor.forClass(TenderAiGateway.RevisionRequest.class);
        verify(aiExecutionService).revise(eq(BID), eq(TASK), eq("snap-1"), eq("auto-review-revision-v1"), request.capture());
        assertThat(request.getValue().requestId()).isEqualTo("task-1-auto-review-1-c1");
        assertThat(request.getValue().mode()).isEqualTo("REWRITE");
        assertThat(request.getValue().selectedHtml()).isEqualTo("<p>总体设计正文</p>");
        verify(productionRepository).applyReviewedChapter(eq(BID), eq("c1"), eq(7L), anyString());
        verify(productionRepository).saveChapterVersion(eq(BID), eq("c1"), eq("AUTO_REVIEW"), anyString(),
                anyString(), eq("改了"), eq(OWNER));
        verify(bidRepository).completeGenerationTask(TASK, BID);
    }

    /** 四轮修订后仍有阻断问题：终止任务并说出问题，绝不带着问题冻结或排版。 */
    @Test
    void givesUpAfterFourRevisionRoundsWithoutFreezingOrLayingOut() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any()))
                .thenReturn(List.of(issue("c1", "ERROR", "OPEN")));
        when(aiExecutionService.revise(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(candidate("<p>还是不行</p>"));

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> service.complete(TASK, OWNER, snapshot, units()));

        assertThat(failure.getErrorCode()).isEqualTo("BID_AUTO_REVIEW_FAILED");
        assertThat(failure.getMessage()).contains("SCORE_MISSING");
        verify(reviewService, times(5)).reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any());
        verify(aiExecutionService, times(4)).revise(anyString(), anyString(), anyString(), anyString(), any());
        verify(productionRepository, never()).freezeContent(anyString(), anyString());
        verifyNoInteractions(layoutProcessor);
        verify(bidRepository, never()).completeGenerationTask(anyString(), anyString());
    }

    @Test
    void aBlockingIssueThatNamesNoChapterCannotBeRevised() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any()))
                .thenReturn(List.of(issue(null, "ERROR", "OPEN")));

        assertThat(errorCodeOf(() -> service.complete(TASK, OWNER, snapshot, units())))
                .isEqualTo("BID_AUTO_REVIEW_UNRESOLVED");
        verifyNoInteractions(aiExecutionService);
    }

    @Test
    void aBlockingIssueOnAChapterThatNoLongerExistsStops() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any()))
                .thenReturn(List.of(issue("gone", "ERROR", "OPEN")));

        assertThat(errorCodeOf(() -> service.complete(TASK, OWNER, snapshot, units())))
                .isEqualTo("BID_AUTO_REVIEW_CHAPTER_MISSING");
        verifyNoInteractions(aiExecutionService);
    }

    @Test
    void aRevisionWithNoHtmlIsNotSaved() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any()))
                .thenReturn(List.of(issue("c1", "ERROR", "OPEN")));
        when(aiExecutionService.revise(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(candidate("  "));

        assertThat(errorCodeOf(() -> service.complete(TASK, OWNER, snapshot, units())))
                .isEqualTo("BID_AUTO_REVIEW_SAVE_FAILED");
        verify(productionRepository, never()).applyReviewedChapter(anyString(), anyString(), anyLong(), anyString());
    }

    /** 用户在修订期间改了这一章：乐观锁挡住保存，不覆盖用户的改动。 */
    @Test
    void aConcurrentEditToTheChapterStopsTheRevisionFromOverwritingIt() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any()))
                .thenReturn(List.of(issue("c1", "ERROR", "OPEN")));
        when(aiExecutionService.revise(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(candidate("<p>修订</p>"));
        when(productionRepository.applyReviewedChapter(anyString(), anyString(), anyLong(), anyString())).thenReturn(false);

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> service.complete(TASK, OWNER, snapshot, units()));

        assertThat(failure.getErrorCode()).isEqualTo("BID_AUTO_REVIEW_SAVE_FAILED");
        assertThat(failure.getHttpStatus()).isEqualTo(409);
        verify(productionRepository, never()).saveChapterVersion(anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyString());
    }

    @Test
    void contentAlreadyFrozenWithTheSameHashIsNotFrozenAgain() {
        String hash = hashOf(chapter);
        stored.set(workspace(production("FROZEN", hash, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any())).thenReturn(List.of());

        service.complete(TASK, OWNER, snapshot, units());

        verify(productionRepository, never()).freezeContent(anyString(), anyString());
        verify(productionRepository).appendEvent(TASK, BID, null, "CONTENT_FROZEN", "正文审查通过，已自动冻结成稿");
    }

    /** 同一份成稿的排版任务已经在跑：接着用它，不再建第二个。 */
    @Test
    void reusesALayoutJobAlreadyRunningForTheSameContent() {
        String hash = hashOf(chapter);
        stored.set(workspace(
                production("FROZEN", hash, List.of(), layout("RUNNING", hash)), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any())).thenReturn(List.of());

        service.complete(TASK, OWNER, snapshot, units());

        verify(productionRepository, never()).createLayoutJob(anyString(), anyString(), anyString(), anyInt());
        verify(layoutProcessor).process("layout-1", BID, OWNER);
    }

    @Test
    void aLayoutFailureMarksTheLayoutJobFailedAndDoesNotCloseTheTask() {
        stored.set(workspace(production("GENERATING", null, List.of(), null), List.of()));
        when(reviewService.reviewSnapshotStrict(eq(TASK), eq(snapshot), any(), any())).thenReturn(List.of());
        doThrow(new IllegalStateException("字体缺失")).when(layoutProcessor).process("layout-new", BID, OWNER);

        assertThat(catchThrowableOfType(IllegalStateException.class,
                () -> service.complete(TASK, OWNER, snapshot, units()))).hasMessage("字体缺失");

        verify(layoutProcessor).fail("layout-new", BID, "字体缺失");
        verify(bidRepository, never()).completeGenerationTask(anyString(), anyString());
    }

    @Test
    void aBidThatNoLongerExistsIsNotFound() {
        when(bidRepository.findBidForTask(BID, OWNER)).thenReturn(Optional.empty());

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> service.complete(TASK, OWNER, snapshot, units()));

        assertThat(failure.getErrorCode()).isEqualTo("BID_NOT_FOUND");
        assertThat(failure.getHttpStatus()).isEqualTo(404);
        verify(productionRepository, never()).appendEvent(anyString(), anyString(), isNull(), anyString(), anyString());
    }

    private static List<BidGenerationUnit> units() {
        return List.of();
    }

    private String hashOf(BidWorkspace.Chapter item) {
        return BidProductionRules.contentHash(List.of(item));
    }

    private BidWorkspace workspace(BidProductionState production, List<BidExport> exports) {
        return new BidWorkspace(bid, null, List.of(), List.of(), List.of(chapter), null, null,
                List.of(), exports, production);
    }

    private BidProductionState production(String contentStatus, String contentHash,
                                          List<BidProductionState.ReviewIssue> issues,
                                          BidProductionState.LayoutJob layoutJob) {
        return new BidProductionState("FROZEN", 1, "i-hash", "FROZEN", 1, "o-hash",
                contentStatus, 1, contentHash, null, List.of(), List.of(), List.of(), issues, layoutJob);
    }

    private BidProductionState.ReviewIssue issue(String chapterId, String severity, String status) {
        return new BidProductionState.ReviewIssue("issue-1", chapterId, severity, "SCORE_MISSING",
                "未响应评分项", "补写响应", status);
    }

    private TenderAiGateway.RevisionCandidate candidate(String html) {
        return new TenderAiGateway.RevisionCandidate(List.of(), "改了", List.of(), List.of(), html);
    }

    private BidExport export() {
        return new BidExport("exp-1", BID, 1, "成果.docx", 1024, "layout-1", "PASSED", NOW);
    }

    private BidProductionState.LayoutJob layout(String status, String inputHash) {
        return new BidProductionState.LayoutJob(
                "layout-1", status, null, inputHash, 80, 77,
                "PASSED", "质量检查通过", null, LocalDateTime.now(), LocalDateTime.now());
    }

    private static String errorCodeOf(Runnable action) {
        return catchThrowableOfType(BusinessException.class, action::run).getErrorCode();
    }
}
