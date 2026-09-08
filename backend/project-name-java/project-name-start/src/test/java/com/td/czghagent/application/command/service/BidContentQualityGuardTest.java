// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-09
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidContentQualityGuardTest {

    @Test
    void reviewSnapshotDetectsRepeatedPhaseRangeConflict() {
        BidGenerationSnapshot snapshot = snapshot();
        List<BidWorkspace.Chapter> chapters = List.of(
                chapter("chapter-1", "项目里程碑划分",
                        "<p>智能体开发阶段为第151-300天，集成测试为第301-380天。</p>"),
                chapter("chapter-2", "进度计划与节点控制",
                        "<p>智能体开发阶段为第151-360天，集成测试为第361-390天。</p>")
        );

        List<BidProductionState.ReviewIssue> issues =
                BidContentQualityGuard.reviewSnapshot(snapshot, chapters);

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("PROGRESS_INCONSISTENCY");
            assertThat(issue.chapterId()).isEqualTo("chapter-2");
            assertThat(issue.severity()).isEqualTo("WARN");
        });
    }

    @Test
    void reviewDoesNotBindLaterPhaseNamesToEarlierUnrelatedRange() {
        BidGenerationSnapshot snapshot = snapshot();
        List<BidWorkspace.Chapter> chapters = List.of(
                chapter("chapter-1", "需求阶段", "<p>需求分析阶段为第1-30天。</p>"),
                chapter("chapter-2", "后续工作",
                        "<p>第1-30天完成需求确认，随后进入试运行与验收准备。</p>")
        );

        List<BidProductionState.ReviewIssue> issues =
                BidContentQualityGuard.reviewSnapshot(snapshot, chapters);

        assertThat(issues).noneMatch(issue -> "PROGRESS_INCONSISTENCY".equals(issue.code()));
    }

    @Test
    void reviewRecognizesDirectPhaseRangesInBothDirections() {
        BidGenerationSnapshot snapshot = snapshot();
        List<BidWorkspace.Chapter> chapters = List.of(
                chapter("chapter-1", "里程碑基准",
                        "<p>试运行阶段第361-420天，验收阶段第421-500天。</p>"),
                chapter("chapter-2", "里程碑复述",
                        "<p>第361-420天：试运行；第421-500天为验收阶段。</p>")
        );

        List<BidProductionState.ReviewIssue> issues =
                BidContentQualityGuard.reviewSnapshot(snapshot, chapters);

        assertThat(issues).noneMatch(issue -> "PROGRESS_INCONSISTENCY".equals(issue.code()));
    }

    @Test
    void reviewDoesNotDoubleMatchNestedPhaseName() {
        BidGenerationSnapshot snapshot = snapshot();
        List<BidWorkspace.Chapter> chapters = List.of(
                chapter("chapter-1", "开发基准", "<p>智能体开发阶段为第151-300天。</p>"),
                chapter("chapter-2", "普通开发", "<p>开发阶段为第301-360天。</p>")
        );

        List<BidProductionState.ReviewIssue> issues =
                BidContentQualityGuard.reviewSnapshot(snapshot, chapters);

        assertThat(issues).noneMatch(issue -> "PROGRESS_INCONSISTENCY".equals(issue.code()));
    }

    @Test
    void reviewDraftDoesNotTreatBarePhaseCyclesAsProjectTotalDuration() {
        BidGenerationSnapshot snapshot = snapshot();
        BidGenerationUnit previous = unit(
                "unit-1", "chapter-1", "<p>项目总工期为500天。</p>");
        BidGenerationUnit current = unit("unit-2", "chapter-2", "");

        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewDraft(
                snapshot, current, List.of(previous, current),
                "<p>平台建设周期为170天，智能体实施周期为160天，均属于500天总工期内的阶段安排。</p>");

        assertThat(issues).noneMatch(issue -> "TOTAL_DURATION_CONFLICT".equals(issue.code()));
    }

    @Test
    void reviewDraftStillRejectsExplicitProjectImplementationCycleConflict() {
        BidGenerationSnapshot snapshot = snapshot();
        BidGenerationUnit previous = unit(
                "unit-1", "chapter-1", "<p>项目总工期为500天。</p>");
        BidGenerationUnit current = unit("unit-2", "chapter-2", "");

        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewDraft(
                snapshot, current, List.of(previous, current),
                "<p>项目实施周期为170天。</p>");

        assertThat(issues).filteredOn(issue -> "TOTAL_DURATION_CONFLICT".equals(issue.code()))
                .allSatisfy(issue -> assertThat(issue.severity()).isEqualTo("WARN"));
    }

    @Test
    void reviewDraftDetectsServiceTimeConflictAndBlindFirstPerson() {
        BidGenerationSnapshot snapshot = snapshot();
        BidGenerationUnit previous = unit(
                "unit-1", "chapter-1",
                "<p>服务承诺：所有问题1小时响应、8小时内解决。</p>");
        BidGenerationUnit current = unit("unit-2", "chapter-2", "");

        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewDraft(
                snapshot, current, List.of(previous, current),
                "<p>我方服务流程中，三级故障1小时内响应、24小时内解决。</p>");

        assertThat(issues).extracting(BidProductionState.ReviewIssue::code)
                .contains("RESPONSE_TIME_CONFLICT", "ANONYMOUS_COMPLIANCE_RISK");
        assertThat(issues).filteredOn(issue -> "RESPONSE_TIME_CONFLICT".equals(issue.code()))
                .allSatisfy(issue -> assertThat(issue.severity()).isEqualTo("WARN"));
    }

    @Test
    void globalCommitmentLedgerCarriesSourceAndPriorBaselines() {
        BidGenerationSnapshot snapshot = snapshot();
        BidGenerationUnit previous = unit(
                "unit-1", "chapter-1",
                "<p>智能体开发阶段为第151-300天。服务承诺：所有问题8小时内解决。</p>");
        BidGenerationUnit current = unit("unit-2", "chapter-2", "");

        String ledger = BidContentQualityGuard.globalCommitmentLedger(
                snapshot, List.of(previous, current), current);

        assertThat(ledger).contains("500天", "智能体开发", "第151-300天")
                .doesNotContain("8小时");
    }

    @Test
    void reviewClassifiesExternalEvidenceAndTieredSlaAsManualWarnings() {
        assertThat(BidContentReviewService.normalizeAiSeverity(
                "ERROR", "COVERAGE_MISSING_PLATFORM_SCREENSHOT", "缺少平台运行截图"))
                .isEqualTo("WARN");
        assertThat(BidContentReviewService.normalizeAiSeverity(
                "ERROR", "INVALID_TEAM_CERTIFICATE", "人员证书未提供"))
                .isEqualTo("WARN");
        assertThat(BidContentReviewService.normalizeAiSeverity(
                "ERROR", "INCONSISTENT_RESPONSE_TIME", "不同故障等级时限需要复核"))
                .isEqualTo("WARN");
        assertThat(BidContentReviewService.normalizeAiSeverity(
                "ERROR", "FORBIDDEN_FACT_VALUE", "正文使用了废弃口径"))
                .isEqualTo("ERROR");
    }

    @Test
    void normalizesExactProcessLanguageAndBlindFirstPersonDeterministically() {
        String normalized = BidContentQualityGuard.normalizeGeneratedLanguage(
                "BLIND",
                "<p>本章节响应说明我方编制过程，并列出对应段落和生成过程。</p>");

        assertThat(normalized)
                .contains("本章方案", "投标人", "实施过程", "对应要求", "处理过程")
                .doesNotContain("本章节响应", "我方", "编制过程", "对应段落", "生成过程");
        assertThat(BidContentQualityGuard.reviewDraft(
                snapshot(), unit("unit-current", "chapter-1", ""),
                List.of(unit("unit-current", "chapter-1", "")), normalized))
                .noneMatch(issue -> "PROCESS_EXPLANATION_LEAK".equals(issue.code())
                        || "ANONYMOUS_COMPLIANCE_RISK".equals(issue.code()));
    }

    @Test
    void normalizesEncodedSpacesAndCountsOnlyVisibleCharacters() {
        String normalized = BidContentQualityGuard.normalizeGeneratedLanguage(
                "OPEN", "<p>技术 方案</p>&#x20;");

        assertThat(normalized).isEqualTo("<p>技术 方案</p>");
        assertThat(BidContentQualityGuard.visibleCharacterCount(normalized)).isEqualTo(4);
    }

    @Test
    void reviewDetectsExactLongParagraphDuplicatedAcrossChapters() {
        String repeated = "运维人员按统一监控规则采集服务器资源、模型服务和网络链路指标，"
                + "告警触发后登记工单、核对影响范围、执行处置并保存复核记录，作为月度服务评价依据。";
        List<BidWorkspace.Chapter> chapters = List.of(
                chapter("chapter-1", "运行监控", "<p>" + repeated + "</p>"),
                chapter("chapter-2", "故障处置", "<p>" + repeated + "</p>"));

        List<BidProductionState.ReviewIssue> issues =
                BidContentQualityGuard.reviewSnapshot(snapshot(), chapters);

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("STYLE_DUPLICATE_PARAGRAPH");
            assertThat(issue.severity()).isEqualTo("ERROR");
            assertThat(issue.chapterId()).isEqualTo("chapter-2");
        });
    }

    @Test
    void reviewReportsDenseClichesAsEditorialWarning() {
        String content = "<p>随着信息技术的发展，本项目具有重要意义。</p>"
                + "<p>通过统一建设实现全面赋能并确保全面提升。</p>"
                + "<p>通过上述措施形成完善闭环，综上所述可形成完整闭环。</p>";

        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(chapter("chapter-1", "总体方案", content)));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("STYLE_CLICHE_DENSITY");
            assertThat(issue.severity()).isEqualTo("WARN");
        });
    }

    private static BidGenerationSnapshot snapshot() {
        return new BidGenerationSnapshot(
                "snapshot-1", "task-1", "bid-1", "owner-1", "hash",
                "测试技术标", "BLIND", 100, 1, 1,
                "解决方案契约", "项目概述：计划工期：500天。", "", "技术部分评分要求",
                "prompt-v1", LocalDateTime.now(),
                List.of(), List.of(
                outline("node-1", "chapter-1", "项目里程碑划分", 1),
                outline("node-2", "chapter-2", "进度计划与节点控制", 2)
        ), List.of(), List.of());
    }

    private static BidGenerationSnapshot.Outline outline(
            String nodeId, String chapterId, String title, int order
    ) {
        return new BidGenerationSnapshot.Outline(
                nodeId, chapterId, "READY", null, 3, title, 0, order,
                "", List.of(), List.of());
    }

    private static BidWorkspace.Chapter chapter(String id, String title, String content) {
        return new BidWorkspace.Chapter(
                id, "node-" + id, title, content, "READY", LocalDateTime.now(), 1);
    }

    private static BidGenerationUnit unit(String id, String chapterId, String content) {
        return new BidGenerationUnit(
                id, "task-1", "bid-1", chapterId, 0, "写作单元",
                id + "-key", content.isBlank() ? "PENDING" : "SUCCEEDED", 1,
                1000, content, "", "", "", null, LocalDateTime.now());
    }
}
