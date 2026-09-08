// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-11
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对确定性质检命中的机械化表达执行一次非阻断的保守编辑。
 */
@Service
class BidStyleEditorialService {
    private static final int CONTEXT_CHARACTERS = 1_400;
    private final BidProductionRepository productionRepository;
    private final BidAiExecutionService aiExecutionService;

    BidStyleEditorialService(BidProductionRepository productionRepository,
                             BidAiExecutionService aiExecutionService) {
        this.productionRepository = productionRepository;
        this.aiExecutionService = aiExecutionService;
    }

    /**
     * 编辑存在风格告警的章节；任一风格编辑失败均保留原文并继续主流程。
     *
     * <p><b>Preconditions:</b> 所有生成单元已经组装为章节正文。</p>
     * <p><b>Side Effects:</b> 可能保存 STYLE_EDIT 章节版本和任务事件。</p>
     * <p><b>Error Semantics:</b> 模型或乐观锁失败不抛出，原文继续进入合规审查。</p>
     */
    void polish(String taskId, String ownerId, BidGenerationSnapshot snapshot,
                BidWorkspace workspace) {
        Map<String, List<BidProductionState.ReviewIssue>> issues = styleIssues(snapshot, workspace);
        if (issues.isEmpty()) {
            productionRepository.appendEvent(taskId, snapshot.bidId(), null,
                    "STYLE_REVIEW_PASSED", "正文专业表达检查通过，无需额外编辑");
            return;
        }
        productionRepository.appendEvent(taskId, snapshot.bidId(), null,
                "STYLE_EDIT_STARTED", "检测到 " + issues.size() + " 个章节需要专业表达编辑");
        Map<String, BidWorkspace.Chapter> chapters = new LinkedHashMap<>();
        workspace.chapters().forEach(chapter -> chapters.put(chapter.id(), chapter));
        List<String> order = chapterOrder(snapshot);
        for (Map.Entry<String, List<BidProductionState.ReviewIssue>> entry : issues.entrySet()) {
            BidWorkspace.Chapter chapter = chapters.get(entry.getKey());
            if (chapter == null || chapter.content().isBlank()) {
                continue;
            }
            editChapter(taskId, ownerId, snapshot, chapter, chapters, order, entry.getValue());
        }
    }

    private void editChapter(
            String taskId, String ownerId, BidGenerationSnapshot snapshot,
            BidWorkspace.Chapter chapter, Map<String, BidWorkspace.Chapter> chapters,
            List<String> order, List<BidProductionState.ReviewIssue> issues
    ) {
        int index = order.indexOf(chapter.id());
        String before = neighbor(chapters, order, index - 1, true);
        String after = neighbor(chapters, order, index + 1, false);
        try {
            TenderAiGateway.RevisionCandidate candidate = aiExecutionService.revise(
                    snapshot.bidId(), taskId, snapshot.id(), "professional-style-edit-v1",
                    new TenderAiGateway.RevisionRequest(
                            taskId + "-style-" + chapter.id(), "POLISH", chapter.content(),
                            before, after, BidContentQualityGuard.styleRevisionInstruction(issues),
                            protectedFacts(snapshot), frozenDictionary(snapshot)));
            String revised = BidContentQualityGuard.normalizeGeneratedLanguage(
                    snapshot.biddingMode(), candidate.html());
            if (revised.isBlank() || !productionRepository.applyReviewedChapter(
                    snapshot.bidId(), chapter.id(), chapter.revision(), revised)) {
                recordSkipped(taskId, snapshot.bidId(), chapter.id(), "正文保存冲突，已保留原文");
                return;
            }
            productionRepository.saveChapterVersion(
                    snapshot.bidId(), chapter.id(), "STYLE_EDIT", revised,
                    contentHash(chapter, revised), candidate.changeSummary(), ownerId);
            productionRepository.appendEvent(taskId, snapshot.bidId(), chapter.id(),
                    "STYLE_EDIT_COMPLETED", "已完成专业表达编辑：" + chapter.title());
        } catch (RuntimeException exception) {
            recordSkipped(taskId, snapshot.bidId(), chapter.id(), "编辑调用失败，已保留原文");
        }
    }

    private Map<String, List<BidProductionState.ReviewIssue>> styleIssues(
            BidGenerationSnapshot snapshot, BidWorkspace workspace
    ) {
        Map<String, List<BidProductionState.ReviewIssue>> result = new LinkedHashMap<>();
        BidContentQualityGuard.reviewSnapshot(snapshot, workspace.chapters()).stream()
                .filter(issue -> issue.code().startsWith("STYLE_"))
                .filter(issue -> issue.chapterId() != null && !issue.chapterId().isBlank())
                .forEach(issue -> result.computeIfAbsent(
                        issue.chapterId(), ignored -> new ArrayList<>()).add(issue));
        return result;
    }

    private List<String> chapterOrder(BidGenerationSnapshot snapshot) {
        return snapshot.outline().stream().filter(node -> node.chapterId() != null)
                .sorted(Comparator.comparingInt(BidGenerationSnapshot.Outline::sortOrder))
                .map(BidGenerationSnapshot.Outline::chapterId).toList();
    }

    private String neighbor(
            Map<String, BidWorkspace.Chapter> chapters, List<String> order,
            int index, boolean tail
    ) {
        if (index < 0 || index >= order.size()) {
            return "";
        }
        BidWorkspace.Chapter chapter = chapters.get(order.get(index));
        String content = chapter == null ? "" : chapter.content();
        if (content.length() <= CONTEXT_CHARACTERS) {
            return content;
        }
        return tail ? content.substring(content.length() - CONTEXT_CHARACTERS)
                : content.substring(0, CONTEXT_CHARACTERS);
    }

    private List<String> protectedFacts(BidGenerationSnapshot snapshot) {
        List<String> result = new ArrayList<>(BidContentQualityGuard.protectedFacts(snapshot));
        snapshot.requirements().forEach(item -> result.add(
                item.title() + "：" + item.description()));
        return result.stream().limit(80).toList();
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
                facts.add(new TenderAiGateway.FixedFact(
                        fact.name(), fact.value(), fact.sourceLocator()));
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
                "STYLE_EDIT_SKIPPED", reason);
    }
}
