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

    // ── 内部标识不得进入交付物 ──────────────────────────────────────────────

    /**
     * 正文里出现内部 UUID 就阻断。
     *
     * <p>这类字符串是章节 id、快照 id 这些只有系统认识的东西。它进不了任何
     * 校验的眼睛——语法通顺、上下文合理——但会原样出现在交给评审的 DOCX 里。
     * 评审看到一串十六进制，第一反应是这份标书是机器拼的。
     */
    @Test
    void blocksAnInternalUuidThatLeakedIntoTheProse() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(chapter("chapter-1", "实施方案",
                        "<p>本方案覆盖 85f1f06b-2295-4bc0-8889-87a7e15d18da 所述范围。</p>")));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("INTERNAL_IDENTIFIER_LEAK");
            assertThat(issue.severity()).isEqualTo("ERROR");
            assertThat(issue.message()).contains("85f1f06b");
        });
    }

    /** 普通的连字符编号不是 UUID——误报会让正常的设备型号被判成泄漏。 */
    @Test
    void leavesOrdinaryHyphenatedCodesAlone() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(chapter("chapter-1", "设备清单",
                        "<p>采用 SW-2024-A1 型交换机与 RJ-45 接口。</p>")));

        assertThat(issues).noneMatch(issue -> "INTERNAL_IDENTIFIER_LEAK".equals(issue.code()));
    }

    /**
     * 接口字段名带冒号出现在正文里同样阻断。
     *
     * <p>模型偶尔会把输入契约的字段名照抄进正文——{@code chapterId:} 之类。
     * 它读起来像一句技术说明，而评审只会看到我们把内部数据结构印在了标书上。
     */
    @Test
    void blocksAnInternalFieldNameCopiedIntoTheProse() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(chapter("chapter-1", "实施方案",
                        "<p>按 biddingMode: BLIND 的要求匿名编写。</p>")));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("INTERNAL_FIELD_LEAK");
            assertThat(issue.severity()).isEqualTo("ERROR");
        });
    }

    /** 字段名后面没有冒号或等号时不算泄漏——「响应模式」这类中文表述里可能出现同形词。 */
    @Test
    void doesNotFlagAFieldNameThatIsNotBeingAssigned() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(chapter("chapter-1", "实施方案",
                        "<p>本项目采用暗标编写方式，不出现投标人信息。</p>")));

        assertThat(issues).noneMatch(issue -> "INTERNAL_FIELD_LEAK".equals(issue.code()));
    }

    // ── 没有段落标签时不能集体失明 ──────────────────────────────────────────

    /**
     * 正文没有 {@code <p>} 标签时，整段仍要进入所有检查。
     *
     * <p>模型偶尔会返回裸文本或只用 {@code <div>}。段落切分拿不到东西就返回空列表，
     * 于是<b>所有段落级检查一次都不执行</b>——泄漏、第一人称、套话全部放行，
     * 而审查结果显示「无问题」。这条兜底是那种情况下唯一的防线。
     */
    @Test
    void stillInspectsProseThatCarriesNoParagraphTags() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(chapter("chapter-1", "实施方案",
                        "我方将按 85f1f06b-2295-4bc0-8889-87a7e15d18da 所述范围实施。")));

        assertThat(issues)
                .as("裸文本同样要被查出内部标识")
                .anyMatch(issue -> "INTERNAL_IDENTIFIER_LEAK".equals(issue.code()));
    }

    /**
     * 段落级检查在没有 {@code <p>} 标签时也要工作。
     *
     * <p>模型偶尔返回裸文本或只用 {@code <div>}。段落切分拿不到东西就返回空列表，
     * 于是<b>重复段落检测一次都不执行</b>——两章一模一样的长段落会原样交付，
     * 而审查结果显示「无问题」。整段兜底是那种情况下唯一的防线。
     *
     * <p>（内部标识那一条走的是整章文本，不经过段落切分——所以它证明不了
     * 这个兜底。实测确认过：把兜底关掉，上面那条照样绿。）
     */
    @Test
    void stillDetectsADuplicateParagraphWhenThereAreNoParagraphTags() {
        // 重复段落规则的门槛是归一化后不少于 60 字——短句重复在标书里是正常的。
        String repeated = "本项目采用统一的技术架构与接口契约，在各建设阶段保持一致的实施口径"
                + "与验收标准，由项目管理办公室按周复核进度与质量记录，"
                + "对偏差项形成整改清单并跟踪闭环，确保交付质量可控可验证可追溯。";

        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(
                        chapter("chapter-1", "总体架构", repeated),
                        chapter("chapter-2", "实施方案", repeated)));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("STYLE_DUPLICATE_PARAGRAPH");
            assertThat(issue.severity()).isEqualTo("ERROR");
        });
    }

    // ── 服务时限的跨章一致性 ────────────────────────────────────────────────

    /**
     * 前文承诺了统一时限，后文出现更长的时限就告警。
     *
     * <p>标书里写着「所有故障 2 小时内解决」，另一章又写「4 小时内恢复」——
     * 评审会当场按最不利的口径理解，而两处单独看都像正常的技术描述。
     */
    @Test
    void warnsWhenALaterChapterPromisesALongerResolutionThanTheGlobalCommitment() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(
                        chapter("chapter-1", "运维服务方案",
                                "<p>承诺所有故障均在2小时内解决，提供7×24小时服务。</p>"),
                        chapter("chapter-2", "故障处置流程",
                                "<p>故障发生后8小时内完成修复并闭环。</p>")));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("RESPONSE_TIME_CONFLICT");
            assertThat(issue.chapterId()).isEqualTo("chapter-2");
            assertThat(issue.severity()).isEqualTo("WARN");
        });
    }

    /**
     * 分级 SLA 不该被判成冲突。
     *
     * <p>只要后文时限没有显著超过前文基准，就不告警——否则一份写清了
     * 一级 2 小时、二级 4 小时的规范 SLA 会被判成自相矛盾，
     * 而那是标书里最专业的写法之一。
     */
    @Test
    void staysQuietWhenTheLaterCommitmentIsWithinTheBaseline() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(
                        chapter("chapter-1", "运维服务方案",
                                "<p>承诺所有故障均在8小时内解决。</p>"),
                        chapter("chapter-2", "故障处置流程",
                                "<p>一级故障2小时内修复，二级故障4小时内恢复。</p>")));

        assertThat(issues).noneMatch(issue -> "RESPONSE_TIME_CONFLICT".equals(issue.code()));
    }

    /**
     * 没有「所有／统一／承诺」这类词时不建立全局基准。
     *
     * <p>一句「本次演练在2小时内完成恢复」讲的是一次演练，不是对全项目的承诺。
     * 把它当基准会让后面每一处正常的时限描述都被判成冲突——告警一多，
     * 真正的冲突就被淹掉了。
     */
    /**
     * 服务段落里的时限即使没有「所有／统一／承诺」也会成为基准。
     *
     * <p>一句「运维团队 2 小时内完成故障修复」没有全局措辞，但它确实是一条
     * 服务承诺。不把它当基准的话，后文写 24 小时也不会有任何提示，
     * 而评审会把两处一起读。
     */
    @Test
    void treatsAServiceParagraphDurationAsABaselineEvenWithoutGlobalWording() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(
                        chapter("chapter-1", "运维服务方案",
                                "<p>运维团队在2小时内完成故障修复。</p>"),
                        chapter("chapter-2", "故障处置流程",
                                "<p>问题在24小时内处置完毕。</p>")));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("RESPONSE_TIME_CONFLICT");
            assertThat(issue.chapterId()).isEqualTo("chapter-2");
        });
    }

    @Test
    void doesNotTurnAnIncidentalDurationIntoAGlobalBaseline() {
        List<BidProductionState.ReviewIssue> issues = BidContentQualityGuard.reviewSnapshot(
                snapshot(), List.of(
                        chapter("chapter-1", "演练安排",
                                "<p>本次切换演练在2小时内完成恢复。</p>"),
                        chapter("chapter-2", "运维服务方案",
                                "<p>故障发生后24小时内完成修复。</p>")));

        assertThat(issues).noneMatch(issue -> "RESPONSE_TIME_CONFLICT".equals(issue.code()));
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
