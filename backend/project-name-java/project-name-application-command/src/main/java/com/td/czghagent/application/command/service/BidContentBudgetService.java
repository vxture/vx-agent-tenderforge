// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
class BidContentBudgetService {
    private static final double MATERIAL_AGGREGATE_RATIO = 1.20d;
    private static final double SETTLED_TARGET_RATIO = 1.15d;
    private static final int MAX_COMPACTION_CHAPTERS = 8;
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?is)<table\\b[^>]*>.*?</table>");
    private static final Pattern MEASURE_PATTERN = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:%|天|小时|分钟|万元|元|个|套|台|GB|MB|TB|Mbps|Gbps)"
    );

    private final BidProductionRepository productionRepository;
    private final BidAiExecutionService aiExecutionService;

    BidContentBudgetService(BidProductionRepository productionRepository,
                            BidAiExecutionService aiExecutionService) {
        this.productionRepository = productionRepository;
        this.aiExecutionService = aiExecutionService;
    }

    BudgetAssessment assess(List<BidGenerationUnit> units, BidWorkspace workspace) {
        Map<String, Integer> budgets = new LinkedHashMap<>();
        units.forEach(unit -> budgets.merge(unit.chapterId(), unit.wordBudget(), Integer::sum));
        List<ChapterBudget> chapters = workspace.chapters().stream()
                .map(chapter -> new ChapterBudget(
                        chapter.id(), chapter.title(), budgets.getOrDefault(chapter.id(), 0),
                        BidContentQualityGuard.visibleCharacterCount(chapter.content()),
                        units.stream().anyMatch(unit -> unit.chapterId().equals(chapter.id())
                                && unit.succeeded())))
                .toList();
        int totalBudget = units.stream().mapToInt(BidGenerationUnit::wordBudget).sum();
        int totalVisible = chapters.stream().mapToInt(ChapterBudget::visibleCharacters).sum();
        return new BudgetAssessment(totalBudget, totalVisible, chapters);
    }

    void recordAssessment(String taskId, String bidId, BudgetAssessment assessment) {
        String message = assessment.totalVisibleCharacters() <= assessment.totalBudget()
                ? "全文正文共" + assessment.totalVisibleCharacters() + "字，未用预算已抵消局部超量"
                : "全文正文共" + assessment.totalVisibleCharacters() + "字，预算"
                + assessment.totalBudget() + "字，已完成跨章节篇幅调剂";
        productionRepository.appendEvent(
                taskId, bidId, null, "CONTENT_BUDGET_SETTLED", message);
    }

    boolean needsCompaction(BudgetAssessment assessment,
                            BidDocumentExporter.RenderedDocument rendered,
                            int targetPages) {
        boolean aggregateOver = assessment.totalBudget() > 0
                && assessment.totalVisibleCharacters()
                > Math.round(assessment.totalBudget() * MATERIAL_AGGREGATE_RATIO);
        boolean pagesOver = rendered.actualPages() != null
                && rendered.actualPages() > maximumAllowedPages(targetPages);
        return aggregateOver || pagesOver;
    }

    /**
     * 压缩篇幅贡献最高的AI章节，任一候选不合格时保留原文并继续处理其他章节。
     *
     * <p><b>Preconditions:</b> 正文已完成首轮自动审查，预排版已尝试密度调整。</p>
     * <p><b>Side Effects:</b> 可能保存BUDGET_COMPACTION版本、预算状态和任务事件。</p>
     * <p><b>Error Semantics:</b> 模型、保护校验或乐观锁失败均不覆盖原文，也不阻断冻结。</p>
     */
    boolean compact(String taskId, String ownerId, BidGenerationSnapshot snapshot,
                    List<BidGenerationUnit> units, BidWorkspace workspace,
                    BidDocumentExporter.RenderedDocument rendered) {
        BudgetAssessment assessment = assess(units, workspace);
        List<CompactionPlan> plans = plans(assessment, rendered, workspace.bid().targetPages());
        if (plans.isEmpty()) {
            productionRepository.appendEvent(taskId, snapshot.bidId(), null,
                    "CONTENT_COMPACTION_SKIPPED", "超页内容无法安全定位到AI生成章节，已保留原文");
            return false;
        }
        productionRepository.appendEvent(taskId, snapshot.bidId(), null,
                "CONTENT_COMPACTION_STARTED", "篇幅结算需要压缩 " + plans.size() + " 个高冗余章节");
        boolean changed = false;
        for (CompactionPlan plan : plans) {
            changed |= compactChapter(taskId, ownerId, snapshot, workspace, plan);
        }
        return changed;
    }

    private boolean compactChapter(String taskId, String ownerId, BidGenerationSnapshot snapshot,
                                   BidWorkspace workspace, CompactionPlan plan) {
        BidWorkspace.Chapter chapter = workspace.chapters().stream()
                .filter(item -> item.id().equals(plan.chapterId())).findFirst().orElse(null);
        if (chapter == null || chapter.content().isBlank()) {
            return false;
        }
        try {
            TenderAiGateway.RevisionCandidate candidate = aiExecutionService.revise(
                    snapshot.bidId(), taskId, snapshot.id(), "budget-compaction-v1",
                    new TenderAiGateway.RevisionRequest(
                            taskId + "-compact-" + chapter.id(), "COMPACT", chapter.content(), "", "",
                            instruction(plan), protectedFragments(snapshot, chapter.content()),
                            frozenDictionary(snapshot)));
            String revised = BidContentQualityGuard.normalizeGeneratedLanguage(
                    snapshot.biddingMode(), candidate.html());
            if (!acceptableCandidate(snapshot, workspace, chapter, revised, plan.targetCharacters())) {
                recordSkipped(taskId, snapshot.bidId(), chapter.id(), "候选稿未通过事实或深度保护校验");
                return false;
            }
            if (!productionRepository.applyCompactedChapter(
                    taskId, snapshot.bidId(), chapter.id(), chapter.revision(), revised,
                    contentHash(chapter, revised), candidate.changeSummary(), ownerId)) {
                recordSkipped(taskId, snapshot.bidId(), chapter.id(), "正文保存冲突");
                return false;
            }
        } catch (RuntimeException exception) {
            recordSkipped(taskId, snapshot.bidId(), chapter.id(), "压缩调用失败，已保留原文");
            return false;
        }
        productionRepository.appendEvent(taskId, snapshot.bidId(), chapter.id(),
                "CONTENT_COMPACTION_COMPLETED", "已保留技术内容并压缩《" + chapter.title() + "》");
        return true;
    }

    private List<CompactionPlan> plans(BudgetAssessment assessment,
                                       BidDocumentExporter.RenderedDocument rendered,
                                       int targetPages) {
        int desiredTotal = Math.round((float) (assessment.totalBudget() * SETTLED_TARGET_RATIO));
        if (rendered.actualPages() != null && rendered.actualPages() > maximumAllowedPages(targetPages)) {
            double pageRatio = maximumAllowedPages(targetPages) / (double) rendered.actualPages();
            desiredTotal = Math.min(desiredTotal,
                    Math.round((float) (assessment.totalVisibleCharacters() * pageRatio * 0.98d)));
        }
        int remainingReduction = Math.max(0, assessment.totalVisibleCharacters() - desiredTotal);
        List<CompactionPlan> result = new ArrayList<>();
        List<ChapterBudget> candidates = assessment.chapters().stream()
                .filter(ChapterBudget::aiGenerated)
                .filter(item -> item.visibleCharacters() > item.budgetCharacters() * 1.05d)
                .sorted(Comparator.comparingInt(ChapterBudget::excessCharacters).reversed())
                .toList();
        for (ChapterBudget candidate : candidates) {
            if (remainingReduction <= 0 || result.size() >= MAX_COMPACTION_CHAPTERS) {
                break;
            }
            int floor = Math.max(1, Math.round(candidate.budgetCharacters() * 0.90f));
            int reduction = Math.min(remainingReduction, candidate.visibleCharacters() - floor);
            if (reduction <= 0) {
                continue;
            }
            result.add(new CompactionPlan(
                    candidate.chapterId(), candidate.visibleCharacters() - reduction));
            remainingReduction -= reduction;
        }
        return List.copyOf(result);
    }

    private boolean acceptableCandidate(BidGenerationSnapshot snapshot, BidWorkspace workspace,
                                        BidWorkspace.Chapter original, String revised,
                                        int targetCharacters) {
        int originalLength = BidContentQualityGuard.visibleCharacterCount(original.content());
        int revisedLength = BidContentQualityGuard.visibleCharacterCount(revised);
        if (revised.isBlank() || revisedLength >= originalLength
                || revisedLength > Math.max(targetCharacters + 100, Math.round(targetCharacters * 1.05f))
                || revisedLength < Math.round(targetCharacters * 0.80f)) {
            return false;
        }
        if (!tables(original.content()).equals(tables(revised))
                || !containsAll(revised, protectedFragments(snapshot, original.content()))) {
            return false;
        }
        return errorCodes(snapshot, workspace.chapters(), original.id(), revised)
                .stream().allMatch(errorCodes(
                        snapshot, workspace.chapters(), original.id(), original.content())::contains);
    }

    private Set<String> errorCodes(BidGenerationSnapshot snapshot,
                                   List<BidWorkspace.Chapter> chapters,
                                   String chapterId, String replacement) {
        List<BidWorkspace.Chapter> revised = chapters.stream().map(chapter ->
                chapter.id().equals(chapterId)
                        ? new BidWorkspace.Chapter(
                        chapter.id(), chapter.outlineNodeId(), chapter.title(), replacement,
                        chapter.generationStatus(), chapter.updatedAt(), chapter.revision())
                        : chapter).toList();
        Set<String> result = new LinkedHashSet<>();
        BidContentQualityGuard.reviewSnapshot(snapshot, revised).stream()
                .filter(issue -> "ERROR".equals(issue.severity()))
                .map(BidProductionState.ReviewIssue::code).forEach(result::add);
        return result;
    }

    private List<String> protectedFragments(BidGenerationSnapshot snapshot, String html) {
        String visible = html.replaceAll("<[^>]+>", " ");
        Set<String> result = new LinkedHashSet<>();
        snapshot.facts().stream().map(BidGenerationSnapshot.Fact::value)
                .filter(value -> value != null && !value.isBlank() && visible.contains(value))
                .forEach(result::add);
        snapshot.requirements().stream().map(BidGenerationSnapshot.Requirement::description)
                .filter(value -> value != null && !value.isBlank() && visible.contains(value))
                .forEach(result::add);
        Matcher matcher = MEASURE_PATTERN.matcher(visible);
        while (matcher.find()) {
            result.add(matcher.group().replaceAll("\\s+", ""));
        }
        return result.stream().limit(120).toList();
    }

    private boolean containsAll(String html, List<String> fragments) {
        String visible = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", "");
        return fragments.stream().allMatch(fragment ->
                visible.contains(fragment.replaceAll("\\s+", "")));
    }

    private List<String> tables(String html) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(html);
        while (matcher.find()) {
            result.add(matcher.group().replaceAll("\\s+", " ").trim());
        }
        return List.copyOf(result);
    }

    private String instruction(CompactionPlan plan) {
        return "将本章可见字符压缩至约" + plan.targetCharacters()
                + "字。只删除重复背景、同义表述、机械总结和无信息过渡句；"
                + "保留全部评分响应、技术判断、实施动作、接口与异常处理、交付物、验证方法、"
                + "数字口径和表格。不得降低技术深度，不得新增事实。";
    }

    private TenderAiGateway.FrozenDictionary frozenDictionary(BidGenerationSnapshot snapshot) {
        List<TenderAiGateway.Metric> metrics = new ArrayList<>();
        List<TenderAiGateway.Term> terms = new ArrayList<>();
        List<TenderAiGateway.FixedFact> facts = new ArrayList<>();
        snapshot.facts().forEach(fact -> {
            if ("METRIC".equals(fact.type())) {
                metrics.add(new TenderAiGateway.Metric(fact.name(), fact.value(), fact.sourceLocator()));
            } else if ("TERM".equals(fact.type())) {
                terms.add(new TenderAiGateway.Term(fact.value(), fact.forbiddenValues()));
            } else {
                facts.add(new TenderAiGateway.FixedFact(fact.name(), fact.value(), fact.sourceLocator()));
            }
        });
        return new TenderAiGateway.FrozenDictionary(metrics, terms, facts);
    }

    private String contentHash(BidWorkspace.Chapter chapter, String content) {
        return BidProductionRules.contentHash(List.of(new BidWorkspace.Chapter(
                chapter.id(), chapter.outlineNodeId(), chapter.title(), content,
                "READY", chapter.updatedAt(), chapter.revision() + 1)));
    }

    private void recordSkipped(String taskId, String bidId, String chapterId, String reason) {
        productionRepository.appendEvent(taskId, bidId, chapterId,
                "CONTENT_COMPACTION_SKIPPED", reason + "，已保留原文");
    }

    static int maximumAllowedPages(int targetPages) {
        return targetPages + targetPages * 20 / 100;
    }

    record BudgetAssessment(
            int totalBudget, int totalVisibleCharacters, List<ChapterBudget> chapters
    ) {
    }

    record ChapterBudget(
            String chapterId, String title, int budgetCharacters,
            int visibleCharacters, boolean aiGenerated
    ) {
        int excessCharacters() {
            return visibleCharacters - budgetCharacters;
        }
    }

    private record CompactionPlan(String chapterId, int targetCharacters) {
    }
}
