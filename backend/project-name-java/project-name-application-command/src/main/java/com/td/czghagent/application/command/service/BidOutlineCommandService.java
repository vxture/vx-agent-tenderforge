package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.port.BidOutlineOrchestrator;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
class BidOutlineCommandService {
    private final BidRepository bidRepository;
    private final BidOutlineOrchestrator outlineOrchestrator;
    private final BidProductionService productionService;
    private final BidCommandSupport support;

    BidOutlineCommandService(BidRepository bidRepository,
                             BidOutlineOrchestrator outlineOrchestrator,
                             BidProductionService productionService,
                             BidCommandSupport support) {
        this.bidRepository = bidRepository;
        this.outlineOrchestrator = outlineOrchestrator;
        this.productionService = productionService;
        this.support = support;
    }

    BidWorkspace generateOutline(String bidId, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        if (!"FROZEN".equals(workspace.production().interpretationStatus())) {
            throw new BusinessException(
                    "BID_INTERPRETATION_NOT_FROZEN", "请先确认并冻结招标文件解读", 409);
        }
        if (!BidProductionRules.hasCompleteInterpretation(workspace.criteria())) {
            throw new BusinessException(
                    "BID_CRITERIA_REQUIRED", "请先完成项目概述和技术部分评分要求解读", 400);
        }
        if (workspace.outlineTask() != null
                && Set.of("PENDING", "RUNNING").contains(workspace.outlineTask().status())) {
            return workspace;
        }
        if (workspace.generationTask() != null
                && Set.of("PENDING", "RUNNING").contains(workspace.generationTask().status())) {
            throw new BusinessException(
                    "BID_CONTENT_GENERATION_RUNNING", "正文生成期间不能重新生成目录", 409);
        }
        if (workspace.production().layoutJob() != null
                && Set.of("PENDING", "RUNNING").contains(
                workspace.production().layoutJob().status())) {
            throw new BusinessException(
                    "BID_LAYOUT_RUNNING", "标书排版期间不能重新生成目录", 409);
        }
        String taskId = bidRepository.createOutlineTask(bidId);
        if (taskId == null) {
            return support.workspace(bidId, context);
        }
        try {
            String workflowRunId = outlineOrchestrator.start(
                    taskId, bidId, context.user().id(), context.traceId(), context.ipAddress());
            bidRepository.assignOutlineWorkflowRun(taskId, workflowRunId);
        } catch (RuntimeException exception) {
            bidRepository.failOutlineTask(taskId, bidId, safeMessage(exception));
            throw new BusinessException("BID_OUTLINE_START_FAILED", "目录生成任务启动失败", 503);
        }
        support.audit(context, bidId, "BID_OUTLINE_GENERATE_SUBMIT", "提交目录异步生成任务");
        return support.workspace(bidId, context);
    }

    @Transactional
    BidWorkspace saveOutline(String bidId, List<BidCommandService.OutlineInput> inputs,
                             boolean confirm, long revision, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        if (bid.revision() != revision) {
            throw support.conflict();
        }
        BidWorkspace current = bidRepository.loadWorkspace(bid);
        List<BidWorkspace.OutlineNode> nodes = normalizeOutline(inputs, current.outline());
        if (sameOutline(current.outline(), nodes)) {
            return confirm
                    ? productionService.freezeOutline(bidId, bid.revision(), context)
                    : current;
        }
        bidRepository.replaceOutline(bidId, nodes, false);
        productionService.markOutlineReview(bidId);
        support.audit(context, bidId, "BID_OUTLINE_UPDATE",
                confirm ? "确认标书目录" : "保存标书目录");
        if (confirm) {
            BidDocument updated = support.requireBid(bidId, context);
            return productionService.freezeOutline(bidId, updated.revision(), context);
        }
        return support.workspace(bidId, context);
    }

    private boolean sameOutline(List<BidWorkspace.OutlineNode> current,
                                List<BidWorkspace.OutlineNode> updated) {
        if (current.size() != updated.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            BidWorkspace.OutlineNode left = current.get(index);
            BidWorkspace.OutlineNode right = updated.get(index);
            if (!Objects.equals(left.id(), right.id())
                    || !Objects.equals(left.parentId(), right.parentId())
                    || left.level() != right.level()
                    || !support.safe(left.title()).equals(support.safe(right.title()))
                    || left.plannedPages() != right.plannedPages()
                    || left.sortOrder() != right.sortOrder()
                    || !Objects.equals(left.taskBrief(), right.taskBrief())
                    || !Objects.equals(left.mustKeywords(), right.mustKeywords())
                    || !Objects.equals(left.scoringPointIds(), right.scoringPointIds())) {
                return false;
            }
        }
        return true;
    }

    private List<BidWorkspace.OutlineNode> normalizeOutline(
            List<BidCommandService.OutlineInput> inputs,
            List<BidWorkspace.OutlineNode> existing) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("BID_OUTLINE_INVALID", "目录不能为空", 400);
        }
        Map<String, String> ids = new HashMap<>();
        inputs.forEach(input -> ids.put(input.clientId(),
                support.validUuid(input.clientId())
                        ? input.clientId() : UUID.randomUUID().toString()));
        Map<String, BidWorkspace.OutlineNode> existingById = new HashMap<>();
        existing.forEach(node -> existingById.put(node.id(), node));
        List<BidWorkspace.OutlineNode> nodes = new ArrayList<>();
        Set<String> clientIds = new HashSet<>();
        for (int index = 0; index < inputs.size(); index++) {
            BidCommandService.OutlineInput input = inputs.get(index);
            if (input.clientId() == null || !clientIds.add(input.clientId())
                    || input.level() < 1 || input.level() > 3) {
                throw new BusinessException(
                        "BID_OUTLINE_INVALID", "目录层级或标识不合法", 400);
            }
            String title = input.title() == null ? "" : input.title().trim();
            if (title.isBlank() || title.length() > 200 || input.plannedPages() < 0
                    || input.plannedPages() > 2000) {
                throw new BusinessException(
                        "BID_OUTLINE_INVALID", "目录标题或计划页数不合法", 400);
            }
            String parentId = input.parentClientId() == null
                    ? null : ids.get(input.parentClientId());
            if (input.level() == 1 && parentId != null
                    || input.level() > 1 && parentId == null) {
                throw new BusinessException(
                        "BID_OUTLINE_INVALID", "目录父子关系不合法", 400);
            }
            BidWorkspace.OutlineNode previous = existingById.get(input.clientId());
            String taskBrief = input.taskBrief() == null
                    ? previous == null ? "" : previous.taskBrief()
                    : support.safe(input.taskBrief());
            List<String> mustKeywords = input.mustKeywords() == null
                    ? previous == null ? List.of() : previous.mustKeywords()
                    : support.safeList(input.mustKeywords());
            List<String> scoringPointIds = input.scoringPointIds() == null
                    ? previous == null ? List.of() : previous.scoringPointIds()
                    : support.safeList(input.scoringPointIds());
            nodes.add(new BidWorkspace.OutlineNode(
                    ids.get(input.clientId()), parentId, input.level(), title,
                    input.level() == 1 ? input.plannedPages() : 0,
                    index, previous == null ? 0 : previous.revision(),
                    taskBrief, mustKeywords, scoringPointIds));
        }
        Map<String, Integer> levels = new HashMap<>();
        inputs.forEach(input -> levels.put(input.clientId(), input.level()));
        for (BidCommandService.OutlineInput input : inputs) {
            if (input.parentClientId() != null
                    && levels.getOrDefault(input.parentClientId(), 0) != input.level() - 1) {
                throw new BusinessException(
                        "BID_OUTLINE_INVALID", "目录层级必须连续", 400);
            }
        }
        boolean invalidRootBudget = inputs.stream().anyMatch(input ->
                input.level() == 1 && input.plannedPages() < 1);
        if (invalidRootBudget) {
            throw new BusinessException(
                    "BID_OUTLINE_INVALID", "一级章节计划页数必须大于0", 400);
        }
        return nodes;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank() ? "目录生成任务启动失败" : message;
        return value.substring(0, Math.min(500, value.length()));
    }
}
