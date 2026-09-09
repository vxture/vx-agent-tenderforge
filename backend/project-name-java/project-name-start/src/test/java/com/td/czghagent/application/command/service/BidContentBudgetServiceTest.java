// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超页时的自动压缩。
 *
 * <p>这个类是「模型压缩过了」和「我们交出去一份数字被改掉的标书」之间<strong>唯一的
 * 闸门</strong>。它错在哪边都很贵：放松了，一次自动压缩会悄悄删掉工期承诺或改掉参数，
 * 而正文读起来依然通顺；收紧了，压缩永远不发生，标书带着超页交出去。
 *
 * <p>两种后果都不会有任何报错。压缩发生在冻结之前的最后一步，那时已经没有人
 * 会再逐字读一遍全文。
 */
class BidContentBudgetServiceTest {

    private static final String TASK = "task-1";
    private static final String BID = "bid-1";
    private static final String CHAPTER = "chapter-1";

    private final BidProductionRepository repository = mock(BidProductionRepository.class);
    private final BidAiExecutionService ai = mock(BidAiExecutionService.class);
    private final BidContentBudgetService service = new BidContentBudgetService(repository, ai);
    private final List<String> events = new ArrayList<>();

    // ── 什么时候该压 ────────────────────────────────────────────────────────

    /** 允许的页数是目标的 120%——排版密度已经调过了，剩下的只能靠删字。 */
    @Test
    void allowsTwentyPercentOverTheTargetPageCount() {
        assertThat(BidContentBudgetService.maximumAllowedPages(100)).isEqualTo(120);
        assertThat(BidContentBudgetService.maximumAllowedPages(60)).isEqualTo(72);
    }

    /**
     * 两条独立的触发条件：字数总量超 120%，<strong>或者</strong>实际页数超上限。
     *
     * <p>只看字数会漏掉表格和图排出来的页；只看页数会在排版尚未产出页数时
     * 完全不设防。
     */
    @Test
    void triggersOnEitherTheCharacterTotalOrTheRenderedPageCount() {
        BidContentBudgetService.BudgetAssessment overCharacters =
                new BidContentBudgetService.BudgetAssessment(1000, 1300, List.of());
        BidContentBudgetService.BudgetAssessment withinCharacters =
                new BidContentBudgetService.BudgetAssessment(1000, 1100, List.of());

        assertThat(service.needsCompaction(overCharacters, rendered(null), 100)).isTrue();
        assertThat(service.needsCompaction(withinCharacters, rendered(121), 100)).isTrue();
        assertThat(service.needsCompaction(withinCharacters, rendered(120), 100))
                .as("正好在上限上不算超").isFalse();
        assertThat(service.needsCompaction(withinCharacters, rendered(null), 100)).isFalse();
    }

    /** 预算为 0（还没生成任何单元）时不该被判成超量。 */
    @Test
    void doesNotTreatAnEmptyBudgetAsAnOverrun() {
        BidContentBudgetService.BudgetAssessment empty =
                new BidContentBudgetService.BudgetAssessment(0, 500, List.of());

        assertThat(service.needsCompaction(empty, rendered(null), 100)).isFalse();
    }

    // ── 预算归集 ────────────────────────────────────────────────────────────

    /**
     * 同一章的多个写作单元预算要相加。
     *
     * <p>取其中一个的话，长章节会被判成大幅超量，然后被压缩到它本来就该有的
     * 长度的一半。
     */
    @Test
    void sumsEveryWritingUnitOfTheSameChapter() {
        BidContentBudgetService.BudgetAssessment assessment = service.assess(
                List.of(unit(CHAPTER, 800, true), unit(CHAPTER, 700, true)),
                workspace(chapter(CHAPTER, "<p>" + "字".repeat(1000) + "</p>")));

        assertThat(assessment.totalBudget()).isEqualTo(1500);
        assertThat(assessment.chapters()).singleElement().satisfies(item -> {
            assertThat(item.budgetCharacters()).isEqualTo(1500);
            assertThat(item.visibleCharacters()).isEqualTo(1000);
            assertThat(item.aiGenerated()).isTrue();
        });
    }

    /** 人工新增、没有对应写作单元的章节标为非 AI 生成——压缩不许碰它。 */
    @Test
    void marksAChapterWithoutAnyUnitAsNotAiGenerated() {
        BidContentBudgetService.BudgetAssessment assessment = service.assess(
                List.of(unit(CHAPTER, 800, true)),
                workspace(chapter(CHAPTER, "<p>a</p>"), chapter("manual", "<p>b</p>")));

        assertThat(assessment.chapters()).extracting(
                        BidContentBudgetService.ChapterBudget::aiGenerated)
                .containsExactly(true, false);
    }

    // ── 压缩的安全闸门 ──────────────────────────────────────────────────────

    /**
     * 比原文还长的候选稿一律不收。
     *
     * <p>模型经常把「压缩」理解成「改写」，返回一份一样长甚至更长的稿子。
     * 收下它等于白付一次调用，而且把原文换成了一份没人审过的新文本。
     *
     * <p>（这条同时被「必须变短」和「不得超过目标上限」两条规则挡住——
     * 在本夹具的量级下两者重叠，它断言的是结果而不是某一条规则。）
     */
    @Test
    void refusesACandidateLongerThanTheOriginal() {
        givenChapterNeedsCompaction();
        givenSavingWouldSucceed();
        givenModelReturns(body(2100));

        assertGateRejected();
    }

    /**
     * 一份和原文一样长的候选稿，即使落在目标上限之内也要拒。
     *
     * <p>章节只略微超量时，目标上限（目标 + 100）会高于原文长度——那时
     * <b>只有「必须变短」这一条</b>拦得住。模型把「压缩」做成「同长度改写」
     * 是常见结果，收下它等于用一份没人审过的新文本换掉原文，而篇幅一点没减。
     *
     * <p>第一版这条用的是一份比原文更长的稿子，于是实际被上限拦下——
     * 把「必须变短」整个关掉，测试照样全绿。
     */
    @Test
    void refusesASameLengthRewriteEvenWhenItFitsTheCeiling() {
        currentWorkspace = workspace(chapter(CHAPTER, body(2000)));
        stubEvents();
        givenSavingWouldSucceed();
        givenModelReturns(body(2000));

        // 预算 1700 → 目标 1955，上限 2055：一份 2000 字的稿子落在上限之内。
        assertThat(service.compact(TASK, "owner", snapshot(),
                List.of(unit(CHAPTER, 1700, true)), currentWorkspace, rendered(null))).isFalse();
        verify(repository, never()).applyCompactedChapter(
                anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any(), anyString());
    }

    /**
     * 压过头也要拒。
     *
     * <p>低于目标 80% 意味着模型不是在删冗余，是在删内容。而删掉的那部分
     * 恰恰可能是某条评分的响应——正文依然通顺，只是不再回答那道题。
     */
    @Test
    void refusesACandidateThatCompactedTooFar() {
        givenChapterNeedsCompaction();
        givenSavingWouldSucceed();
        givenModelReturns(body(300));

        assertGateRejected();
    }

    /**
     * 表格必须<strong>一字不差地保留</strong>。
     *
     * <p>表格里装的是参数、工期和配置清单——评审逐格对照的东西。
     * 压缩时动表格是这套机制能造成的最大伤害。
     */
    @Test
    void refusesACandidateThatTouchedATable() {
        givenChapterNeedsCompaction("<table><tr><td>响应时间</td><td>2小时</td></tr></table>");
        givenSavingWouldSucceed();
        // 差异<b>只在表格本身</b>：多一个属性。可见文字、受保护数字、列数全都
        // 一模一样，于是能拦下它的只剩表格比对那一条。
        //
        // 第一版这里把「2小时」改成了「4小时」，那实际是被「受保护片段」拦的：
        // 把表格比对整个关掉，测试照样全绿。反向验证抓到的就是这个。
        givenModelReturns("<p>" + "字".repeat(1087) + "</p>"
                + "<table class=\"grid\"><tr><td>响应时间</td><td>2小时</td></tr></table>");

        assertGateRejected();
    }

    /**
     * 受保护的数字口径必须全部还在。
     *
     * <p>「7×24 小时」「2 小时响应」「99.9%」这类承诺一旦在压缩里消失，
     * 标书就不再响应对应的评分项，而没有任何一处会提示它不见了。
     */
    @Test
    void refusesACandidateThatDroppedAProtectedMeasurement() {
        givenChapterNeedsCompaction("<p>应急响应时间为2小时，可用率99.9%。</p>");
        // 同样落在可接受区间内：被挡是因为「2小时」和「99.9%」不见了，不是因为长度。
        givenSavingWouldSucceed();
        givenModelReturns("<p>" + "字".repeat(1080) + "，提供及时的应急响应。</p>");

        assertGateRejected();
    }

    /** 合格的候选稿才写回，并留下完成事件。 */
    @Test
    void acceptsACandidateThatShrankWhileKeepingEverythingProtected() {
        givenChapterNeedsCompaction("<p>应急响应时间为2小时。</p>");
        givenModelReturns("<p>应急响应时间为2小时。" + "字".repeat(1090) + "</p>");
        when(repository.applyCompactedChapter(
                anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any(), anyString())).thenReturn(true);

        assertThat(compact()).isTrue();
        assertThat(events).anyMatch(event -> event.contains("CONTENT_COMPACTION_COMPLETED"));
    }

    // ── 失败一律保留原文 ────────────────────────────────────────────────────

    /**
     * 模型调用失败不覆盖原文，也<strong>不阻断冻结</strong>。
     *
     * <p>压缩是锦上添花的一步：它失败的正确后果是标书长一点，
     * 而不是标书交不出去。
     */
    @Test
    void keepsTheOriginalWhenTheModelCallFails() {
        givenChapterNeedsCompaction();
        when(ai.revise(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("模型不可用"));

        assertThat(compact()).isFalse();
        assertThat(events).anyMatch(event -> event.contains("已保留原文"));
    }

    /** 乐观锁冲突同样保留原文——有人正在手工编辑这一章。 */
    @Test
    void keepsTheOriginalWhenSomeoneElseEditedTheChapter() {
        givenChapterNeedsCompaction("<p>应急响应时间为2小时。</p>");
        givenModelReturns("<p>应急响应时间为2小时。" + "字".repeat(1090) + "</p>");
        when(repository.applyCompactedChapter(
                anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any(), anyString())).thenReturn(false);

        assertThat(compact()).isFalse();
        assertThat(events).anyMatch(event -> event.contains("正文保存冲突"));
    }

    /**
     * 没有可安全定位的 AI 章节时，记事件并放行。
     *
     * <p>超页而全是人工章节——这时压缩无处下手。放行而不是失败：
     * 这一步阻断冻结的话，用户手写的长章节会让整份标书卡死。
     */
    @Test
    void skipsWhenThereIsNoAiGeneratedChapterToCompact() {
        BidWorkspace workspace = workspace(chapter("manual", "<p>" + "字".repeat(5000) + "</p>"));
        stubEvents();

        boolean changed = service.compact(TASK, "owner", snapshot(), List.of(), workspace,
                rendered(200));

        assertThat(changed).isFalse();
        assertThat(events).anyMatch(event -> event.contains("CONTENT_COMPACTION_SKIPPED"));
        verify(ai, never()).revise(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private boolean compact() {
        return service.compact(TASK, "owner", snapshot(),
                List.of(unit(CHAPTER, 1000, true)), currentWorkspace, rendered(200));
    }

    private BidWorkspace currentWorkspace;

    private void givenChapterNeedsCompaction() {
        givenChapterNeedsCompaction("");
    }

    private void givenChapterNeedsCompaction(String protectedHtml) {
        currentWorkspace = workspace(chapter(CHAPTER, protectedHtml + body(2000)));
        stubEvents();
    }

    /**
     * 让保存<b>会成功</b>。
     *
     * <p>不打桩的话 Mockito 默认返回 false，于是「闸门拦下了」和「保存失败了」
     * 产生同样的结果——断言分不出两者。实测确认过：那时候把三条保护规则
     * 逐一关掉，测试照样全绿。
     */
    private void givenSavingWouldSucceed() {
        when(repository.applyCompactedChapter(
                anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any(), anyString())).thenReturn(true);
    }

    /** 闸门拦下：调过模型、没走到保存、跳过原因指向保护校验。 */
    private void assertGateRejected() {
        assertThat(compact()).isFalse();
        verify(ai).revise(anyString(), anyString(), anyString(), anyString(), any());
        verify(repository, never()).applyCompactedChapter(
                anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any(), anyString());
        assertThat(events).anyMatch(event -> event.contains("事实或深度保护校验"));
    }

    private void givenModelReturns(String html) {
        when(ai.revise(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new TenderAiGateway.RevisionCandidate(
                        List.of(), "已压缩", List.of(), List.of(), html));
    }

    private void stubEvents() {
        org.mockito.Mockito.doAnswer(invocation -> {
            events.add(invocation.getArgument(3) + ":" + invocation.getArgument(4));
            return null;
        }).when(repository).appendEvent(anyString(), anyString(), any(), anyString(), anyString());
    }

    private static String body(int characters) {
        return "<p>" + "字".repeat(characters) + "</p>";
    }

    private static BidDocumentExporter.RenderedDocument rendered(Integer actualPages) {
        return new BidDocumentExporter.RenderedDocument(
                new byte[0], actualPages, "PASSED", "");
    }

    private static BidGenerationUnit unit(String chapterId, int budget, boolean succeeded) {
        return new BidGenerationUnit(
                "unit-" + chapterId + "-" + budget, TASK, BID, chapterId, 0, "写作单元",
                "key-" + chapterId + "-" + budget, succeeded ? "SUCCEEDED" : "PENDING",
                1, budget, "", "", "", "", "", LocalDateTime.now());
    }

    private static BidWorkspace.Chapter chapter(String id, String content) {
        return new BidWorkspace.Chapter(
                id, "node-" + id, "章节" + id, content, "READY", LocalDateTime.now(), 1L);
    }

    private static BidWorkspace workspace(BidWorkspace.Chapter... chapters) {
        BidDocument bid = new BidDocument(
                BID, "owner", new TenantScope("org-1", "ws-1"), "TA-1", "SCORING_CRITERIA",
                "测试技术标", 100, "OPEN", "CONTENT", "GENERATING", false, null,
                LocalDateTime.now(), LocalDateTime.now(), 1);
        BidProductionState production = new BidProductionState(
                "FROZEN", 1, "hash", "FROZEN", 1, "hash",
                "DRAFT", 0, null, null, List.of(), List.of(), List.of(), List.of(), null);
        return new BidWorkspace(bid, null, List.of(), List.of(), List.of(chapters),
                null, null, List.of(), List.of(), production);
    }

    private static BidGenerationSnapshot snapshot() {
        return new BidGenerationSnapshot(
                "snapshot", TASK, BID, "owner", "hash", "测试技术标", "OPEN",
                100, 1, 1, "解决方案契约", "写作总纲", "术语台账", "承诺台账",
                "chapter-draft-v8", LocalDateTime.now(), List.of(), List.of(),
                List.of(), List.of());
    }
}
