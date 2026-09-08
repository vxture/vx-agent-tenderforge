// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class BidOutlineContextFactory {
    private static final int MAX_OUTLINE_REFERENCE_CHARACTERS = 50_000;

    private final BidRepository bidRepository;
    private final BidAssetIngestionService assetIngestionService;
    private final BidCommandSupport support;

    BidOutlineContextFactory(
            BidRepository bidRepository,
            BidAssetIngestionService assetIngestionService,
            BidCommandSupport support
    ) {
        this.bidRepository = bidRepository;
        this.assetIngestionService = assetIngestionService;
        this.support = support;
    }

    /**
     * Build the immutable input for one independently retryable outline stage.
     *
     * Preconditions: the task belongs to the bid owner and still targets the current input revision.
     * Side Effects: selected outline assets may be parsed once through the ingestion service.
     * Error Semantics: BusinessException identifies ownership, state, or stale-input failures.
     */
    Context load(String taskId, String bidId, String ownerId, String traceId, String ipAddress) {
        OperationContext operation = operation(ownerId, traceId, ipAddress);
        BidDocument bid = support.requireBid(bidId, operation);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        validateTask(taskId, workspace);
        validateInterpretation(workspace);
        TenderAiGateway.OutlineRequest request = new TenderAiGateway.OutlineRequest(
                traceId, bid.title(), bid.targetPages(), bid.biddingMode(),
                BidProductionRules.effectiveCriteria(workspace).stream()
                        .map(this::toAiCriterion).toList(),
                referenceSummaries(workspace, operation));
        return new Context(request, operation);
    }

    List<BidWorkspace.OutlineNode> toNodes(TenderAiGateway.OutlinePlan plan) {
        Map<String, String> ids = new HashMap<>();
        plan.nodes().forEach(node -> ids.put(node.nodeKey(), UUID.randomUUID().toString()));
        List<BidWorkspace.OutlineNode> nodes = new ArrayList<>();
        for (int index = 0; index < plan.nodes().size(); index++) {
            TenderAiGateway.OutlineNode node = plan.nodes().get(index);
            String title = support.safe(node.title());
            if (title.isBlank() || title.length() > 200) {
                throw new BusinessException(
                        "BID_AI_OUTLINE_INVALID", "AI 返回的目录标题不合法", 502);
            }
            nodes.add(new BidWorkspace.OutlineNode(
                    ids.get(node.nodeKey()),
                    node.parentKey() == null ? null : ids.get(node.parentKey()),
                    node.level(), title, node.level() == 1 ? node.plannedPages() : 0, index, 0,
                    support.safe(node.taskBrief()), support.safeList(node.mustKeywords()),
                    support.safeList(node.scoringPointIds())));
        }
        return nodes;
    }

    private void validateInterpretation(BidWorkspace workspace) {
        if (!"FROZEN".equals(workspace.production().interpretationStatus())) {
            throw new BusinessException(
                    "BID_INTERPRETATION_NOT_FROZEN", "请先确认并冻结招标文件解读", 409);
        }
        if (!BidProductionRules.hasCompleteInterpretation(workspace.criteria())) {
            throw new BusinessException(
                    "BID_CRITERIA_REQUIRED", "请先完成项目概述和技术部分评分要求解读", 400);
        }
    }

    private void validateTask(String taskId, BidWorkspace workspace) {
        BidWorkspace.OutlineTask task = workspace.outlineTask();
        if (task == null || !task.id().equals(taskId)
                || workspace.bid().revision() != task.inputRevision() + 1) {
            throw new BusinessException(
                    "BID_OUTLINE_INPUT_CHANGED", "目录生成期间上游内容发生变化，请重新生成", 409);
        }
    }

    private TenderAiGateway.Criterion toAiCriterion(BidWorkspace.Criterion item) {
        return new TenderAiGateway.Criterion(
                item.id(), item.type(), item.title(), item.description(), item.score(),
                item.sourceLocator(), item.sourceExcerpt());
    }

    private List<TenderAiGateway.ReferenceSummary> referenceSummaries(
            BidWorkspace workspace, OperationContext context) {
        List<TenderAiGateway.ReferenceSummary> references = new ArrayList<>();
        for (String assetId : workspace.selectedAssetIds()) {
            BidRepository.AssetRecord asset = bidRepository
                    .findAsset(assetId, context.user().id()).orElse(null);
            if (asset != null && "OUTLINE".equals(asset.category())) {
                references.add(referenceSummary(asset));
            }
        }
        return references;
    }

    private TenderAiGateway.ReferenceSummary referenceSummary(BidRepository.AssetRecord asset) {
        List<BidReferenceChunk> chunks = assetIngestionService.ensureIngested(asset);
        StringBuilder summary = new StringBuilder();
        for (BidReferenceChunk chunk : chunks) {
            appendWithinLimit(summary, "[" + chunk.sourceLocator() + "] " + chunk.content() + '\n');
            if (summary.length() >= MAX_OUTLINE_REFERENCE_CHARACTERS) {
                break;
            }
        }
        return new TenderAiGateway.ReferenceSummary(
                asset.id(), asset.category(), asset.originalFileName(), summary.toString());
    }

    private void appendWithinLimit(StringBuilder target, String value) {
        int remaining = MAX_OUTLINE_REFERENCE_CHARACTERS - target.length();
        if (remaining > 0) {
            target.append(value, 0, Math.min(remaining, value.length()));
        }
    }

    private OperationContext operation(String ownerId, String traceId, String ipAddress) {
        return new OperationContext(
                new CurrentUser(ownerId, "", "", "PLANNER", null), traceId, ipAddress);
    }

    record Context(TenderAiGateway.OutlineRequest request, OperationContext operation) {
    }
}
