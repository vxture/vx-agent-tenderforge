// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidContentReviewServiceTest {
    @Test
    void reviewsEachTopLevelLaneBeforeOneGlobalReview() {
        BidAiExecutionService ai = mock(BidAiExecutionService.class);
        when(ai.reviewLane(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> review("一级技术域审查完成"));
        when(ai.reviewGlobal(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> review("全文一致性审查完成"));
        BidContentReviewService service = new BidContentReviewService(
                mock(BidRepository.class), mock(BidProductionRepository.class), ai,
                new BidRelevantContextSelector());

        List<com.td.czghagent.domain.model.BidProductionState.ReviewIssue> issues =
                service.reviewSnapshotStrict(
                        "task", snapshot(), chapters(), units());

        assertThat(issues).isEmpty();
        ArgumentCaptor<TenderAiGateway.ReviewRequest> laneRequests =
                ArgumentCaptor.forClass(TenderAiGateway.ReviewRequest.class);
        verify(ai, times(2)).reviewLane(
                anyString(), anyString(), anyString(), laneRequests.capture());
        assertThat(laneRequests.getAllValues())
                .allMatch(request -> "LANE".equals(request.payload().get("reviewMode")));
        ArgumentCaptor<TenderAiGateway.ReviewRequest> globalRequest =
                ArgumentCaptor.forClass(TenderAiGateway.ReviewRequest.class);
        verify(ai).reviewGlobal(anyString(), anyString(), anyString(), globalRequest.capture());
        assertThat(globalRequest.getValue().payload().get("reviewMode")).isEqualTo("GLOBAL");
        assertThat((List<?>) globalRequest.getValue().payload().get("laneReviews")).hasSize(2);
        assertThat(globalRequest.getValue().payload())
                .containsKey("solutionContract")
                .doesNotContainKeys("requirements", "writingBible", "chapters");
    }

    @Test
    void boundsTheTotalLaneExcerptForLargeOutlines() {
        assertThat(BidContentReviewService.laneExcerptLimit(300) * 300)
                .isLessThanOrEqualTo(60_000);
        assertThat(BidContentReviewService.laneExcerptLimit(2)).isEqualTo(6_000);
    }

    @Test
    void demotesReviewIssuesWithUnknownChapterReferencesToWarnings() {
        BidAiExecutionService ai = mock(BidAiExecutionService.class);
        when(ai.reviewLane(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> new TenderAiGateway.Review(
                        true,
                        List.of(new TenderAiGateway.ReviewIssue(
                                "ERROR", "SOLUTION_LOGIC_GAP", "invented-chapter-id",
                                "无法定位的问题", "请人工确认", List.of())),
                        new TenderAiGateway.ReviewCoverage(0, 0, List.of()),
                        List.of(), "技术域审查完成"));
        when(ai.reviewGlobal(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> review("全文一致性审查完成"));

        BidContentReviewService service = new BidContentReviewService(
                mock(BidRepository.class), mock(BidProductionRepository.class), ai,
                new BidRelevantContextSelector());

        List<com.td.czghagent.domain.model.BidProductionState.ReviewIssue> issues =
                service.reviewSnapshotStrict("task", snapshot(), chapters(), units());

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("AI_REVIEW_INVALID_CHAPTER_REFERENCE");
            assertThat(issue.severity()).isEqualTo("WARN");
            assertThat(issue.chapterId()).isNull();
        });
        assertThat(issues).noneMatch(issue -> "invented-chapter-id".equals(issue.chapterId()));
    }

    @Test
    void acceptsOutlineNodeReferenceAndNormalizesItToChapterId() {
        BidAiExecutionService ai = mock(BidAiExecutionService.class);
        when(ai.reviewLane(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> new TenderAiGateway.Review(
                        true,
                        List.of(new TenderAiGateway.ReviewIssue(
                                "ERROR", "SOLUTION_LOGIC_GAP", "leaf-a",
                                "实现机制不完整", "补充实施动作", List.of())),
                        new TenderAiGateway.ReviewCoverage(0, 0, List.of()),
                        List.of(), "技术域审查完成"));
        when(ai.reviewGlobal(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> review("全文一致性审查完成"));

        BidContentReviewService service = new BidContentReviewService(
                mock(BidRepository.class), mock(BidProductionRepository.class), ai,
                new BidRelevantContextSelector());

        List<com.td.czghagent.domain.model.BidProductionState.ReviewIssue> issues =
                service.reviewSnapshotStrict("task", snapshot(), chapters(), units());

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("SOLUTION_LOGIC_GAP");
            assertThat(issue.severity()).isEqualTo("ERROR");
            assertThat(issue.chapterId()).isEqualTo("chapter-a");
        });
    }

    private TenderAiGateway.Review review(String summary) {
        return new TenderAiGateway.Review(
                true, List.of(), new TenderAiGateway.ReviewCoverage(0, 0, List.of()),
                List.of(), summary);
    }

    private BidGenerationSnapshot snapshot() {
        return new BidGenerationSnapshot(
                "snapshot", "task", "bid", "owner", "hash", "测试技术标", "BLIND",
                80, 1, 1, "解决方案契约", "写作总纲", "术语台账", "承诺台账",
                "chapter-draft-v8", LocalDateTime.now(), List.of(),
                List.of(
                        outline("root-a", null, null, 1, 0),
                        outline("branch-a", null, "root-a", 2, 1),
                        outline("leaf-a", "chapter-a", "branch-a", 3, 2),
                        outline("root-b", null, null, 1, 3),
                        outline("branch-b", null, "root-b", 2, 4),
                        outline("leaf-b", "chapter-b", "branch-b", 3, 5)),
                List.of(), List.of());
    }

    private BidGenerationSnapshot.Outline outline(
            String id, String chapterId, String parentId, int level, int order
    ) {
        return new BidGenerationSnapshot.Outline(
                id, chapterId, "READY", parentId, level, id, level == 1 ? 40 : 0,
                order, id + "任务", List.of(id), List.of());
    }

    private List<BidWorkspace.Chapter> chapters() {
        return List.of(
                chapter("chapter-a", "技术域A", "<p>技术域A正文包含实施动作和验收方法。</p>"),
                chapter("chapter-b", "技术域B", "<p>技术域B正文包含实施动作和验收方法。</p>"));
    }

    private BidWorkspace.Chapter chapter(String id, String title, String content) {
        return new BidWorkspace.Chapter(
                id, "leaf-" + id.substring(id.length() - 1), title, content,
                "READY", LocalDateTime.now(), 1);
    }

    private List<BidGenerationUnit> units() {
        return List.of(unit("unit-a", "chapter-a"), unit("unit-b", "chapter-b"));
    }

    private BidGenerationUnit unit(String id, String chapterId) {
        return new BidGenerationUnit(
                id, "task", "bid", chapterId, 0, "写作单元", "key-" + id,
                "SUCCEEDED", 1, 800, "<p>正文</p>", "hash", "章节摘要",
                "", "", LocalDateTime.now());
    }
}
