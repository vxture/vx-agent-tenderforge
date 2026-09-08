// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.BidWorkspaceViews;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.BidGenerationOrchestrator;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
class BidContentCommandService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BidContentCommandService.class);
    private final BidRepository bidRepository;
    private final FileStorage fileStorage;
    private final BidAiExecutionService aiExecutionService;
    private final BidDocumentExporter exporter;
    private final BidProductionRepository productionRepository;
    private final BidGenerationOrchestrator generationOrchestrator;
    private final BidGenerationService generationService;
    private final BidCommandSupport support;

    BidContentCommandService(BidRepository bidRepository, FileStorage fileStorage,
                             BidAiExecutionService aiExecutionService,
                             BidDocumentExporter exporter,
                             BidProductionRepository productionRepository,
                             BidGenerationOrchestrator generationOrchestrator,
                             BidGenerationService generationService,
                             BidCommandSupport support) {
        this.bidRepository = bidRepository;
        this.fileStorage = fileStorage;
        this.aiExecutionService = aiExecutionService;
        this.exporter = exporter;
        this.productionRepository = productionRepository;
        this.generationOrchestrator = generationOrchestrator;
        this.generationService = generationService;
        this.support = support;
    }

    BidWorkspace startGeneration(String bidId, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        if (workspace.generationTask() != null
                && Set.of("PENDING", "RUNNING").contains(workspace.generationTask().status())) {
            return workspace;
        }
        if (!canStartGeneration(bid.status())
                || workspace.chapters().isEmpty()) {
            throw new BusinessException("BID_STEP_NOT_READY", "请先确认目录", 409);
        }
        if (!"FROZEN".equals(workspace.production().outlineStatus())) {
            throw new BusinessException("BID_OUTLINE_NOT_FROZEN", "请先冻结标书目录", 409);
        }
        BidGenerationService.GenerationLaunch launch;
        try {
            launch = generationService.createTask(workspace);
        } catch (BusinessException exception) {
            if ("BID_GENERATION_RUNNING".equals(exception.getErrorCode())) {
                return support.workspace(bidId, context);
            }
            throw exception;
        }
        try {
            String workflowRunId = generationOrchestrator.start(
                    launch.taskId(), bidId, context.user().id(),
                    launch.snapshotId(), launch.snapshotHash(), 0);
            productionRepository.assignWorkflowRun(
                    launch.taskId(), workflowRunId, launch.snapshotHash());
        } catch (RuntimeException exception) {
            generationService.fail(launch.taskId(), bidId, exception.getMessage());
            throw new BusinessException(
                    "BID_GENERATION_START_FAILED", "正文生成任务启动失败", 502);
        }
        support.audit(context, bidId, "BID_CONTENT_GENERATE", "启动标书正文生成");
        return support.workspace(bidId, context);
    }

    /**
     * 暂停当前正文任务并保留所有已经成功的生成单元。
     *
     * <p><b>Preconditions:</b> 当前用户拥有标书，最新任务处于等待、运行或暂停状态。</p>
     * <p><b>Side Effects:</b> 先持久化暂停状态，再尽力终止对应工作流并记录审计。</p>
     * <p><b>Error Semantics:</b> 已暂停请求幂等；没有活动任务时返回业务冲突。</p>
     */
    BidWorkspace pauseGeneration(String bidId, OperationContext context) {
        support.requireBid(bidId, context);
        BidRepository.GenerationTaskExecution execution = bidRepository.pauseGenerationTask(bidId);
        stopExecution(execution);
        if (execution.changed()) {
            support.audit(context, bidId, "BID_CONTENT_PAUSE", "暂停正文生成并保留已完成内容");
        }
        return support.workspace(bidId, context);
    }

    /**
     * 从同一不可变快照继续当前任务的未完成生成单元。
     *
     * <p><b>Preconditions:</b> 最新任务处于暂停或失败状态且仍有完整快照。</p>
     * <p><b>Side Effects:</b> 重置未完成单元、递增任务重试次数并启动新的工作流执行。</p>
     * <p><b>Error Semantics:</b> 重复继续请求幂等；启动失败时任务回到可再次继续的暂停态。</p>
     */
    BidWorkspace resumeGeneration(String bidId, OperationContext context) {
        support.requireBid(bidId, context);
        BidRepository.GenerationTaskExecution execution = bidRepository.resumeGenerationTask(bidId);
        if (!execution.changed()) {
            return support.workspace(bidId, context);
        }
        String workflowRunId = null;
        try {
            workflowRunId = generationOrchestrator.start(
                    execution.taskId(), bidId, context.user().id(),
                    execution.snapshotId(), execution.snapshotHash(), execution.retryCount());
            productionRepository.assignWorkflowRun(
                    execution.taskId(), workflowRunId, execution.snapshotHash());
        } catch (RuntimeException exception) {
            BidRepository.GenerationTaskExecution paused = bidRepository.pauseGenerationTask(bidId);
            if (workflowRunId != null) {
                stopExecution(new BidRepository.GenerationTaskExecution(
                        execution.taskId(), bidId, "PAUSED", execution.snapshotId(),
                        execution.snapshotHash(), workflowRunId, execution.retryCount(), true));
            }
            LOGGER.warn("正文任务续跑启动失败，任务已恢复为暂停态：taskId={}",
                    paused.taskId(), exception);
            throw new BusinessException(
                    "BID_GENERATION_RESUME_FAILED", "正文生成任务继续失败，进度仍已保留", 502);
        }
        support.audit(context, bidId, "BID_CONTENT_RESUME", "继续正文任务的未完成部分");
        return support.workspace(bidId, context);
    }

    static boolean canStartGeneration(String bidStatus) {
        return Set.of("OUTLINE_READY", "CONTENT_READY", "EXPORTED", "COMPLETED", "FAILED")
                .contains(bidStatus);
    }

    private void stopExecution(BidRepository.GenerationTaskExecution execution) {
        try {
            generationOrchestrator.stop(
                    execution.taskId(), execution.workflowRunId(), execution.retryCount());
        } catch (RuntimeException exception) {
            LOGGER.warn("正文任务已暂停，但工作流终止请求失败：taskId={}",
                    execution.taskId(), exception);
        }
    }

    BidWorkspace reviewContent(String bidId, OperationContext context) {
        support.requireBid(bidId, context);
        generationService.reviewNow(bidId, context.user().id(), context.traceId());
        support.audit(context, bidId, "BID_CONTENT_REVIEW", "重新执行成稿一致性审查");
        return support.workspace(bidId, context);
    }

    @Transactional
    BidWorkspaceViews.ChapterDetail saveChapter(
            String bidId, String chapterId, String content,
            long revision, OperationContext context) {
        support.requireBid(bidId, context);
        String normalized = content == null ? "" : content.trim();
        if (normalized.length() > 1_000_000) {
            throw new BusinessException(
                    "BID_CHAPTER_TOO_LONG", "单章正文不能超过100万字符", 400);
        }
        if (!bidRepository.updateChapter(bidId, chapterId, normalized, revision)) {
            throw support.conflict();
        }
        productionRepository.markContentReview(bidId, "正文已调整，请重新执行成稿审查");
        BidWorkspaceViews.ChapterDetail updated = bidRepository
                .findChapterDetail(bidId, chapterId)
                .orElseThrow(() -> new BusinessException(
                        "BID_CHAPTER_NOT_FOUND", "正文章节不存在", 404));
        BidWorkspace.Chapter chapter = updated.chapter();
        String contentHash = BidProductionRules.contentHash(List.of(chapter));
        productionRepository.saveChapterVersion(
                bidId, chapterId, "MANUAL", normalized, contentHash,
                null, context.user().id());
        support.audit(context, bidId, "BID_CHAPTER_UPDATE", "人工保存正文章节");
        return updated;
    }

    BidCommandService.SectionRevisionCandidate reviseChapter(
            String bidId, String chapterId, String mode, String selectedHtml,
            String beforeContext, String afterContext, String instruction,
            long revision, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        BidWorkspace.Chapter chapter = workspace.chapters().stream()
                .filter(item -> item.id().equals(chapterId)).findFirst()
                .orElseThrow(() -> new BusinessException(
                        "BID_CHAPTER_NOT_FOUND", "正文章节不存在", 404));
        if (chapter.revision() != revision) {
            throw support.conflict();
        }
        String normalizedMode = support.safe(mode).toUpperCase(Locale.ROOT);
        if (!Set.of("REWRITE", "REPHRASE", "POLISH").contains(normalizedMode)
                || selectedHtml == null || selectedHtml.isBlank()
                || selectedHtml.length() > 200_000) {
            throw new BusinessException(
                    "BID_REVISION_INVALID", "请选择有效正文并指定处理方式", 400);
        }
        List<String> protectedFacts = workspace.criteria().stream()
                .filter(item -> Set.of("PROJECT_OVERVIEW", "TECHNICAL_SCORING")
                        .contains(item.type()))
                .map(item -> item.title() + "：" + item.description()).toList();
        TenderAiGateway.RevisionCandidate candidate = aiExecutionService.revise(bidId,
                new TenderAiGateway.RevisionRequest(
                        context.traceId(), normalizedMode, selectedHtml,
                        support.safe(beforeContext), support.safe(afterContext),
                        support.safe(instruction), protectedFacts, frozenDictionary(workspace)));
        support.audit(context, bidId, "BID_SECTION_AI_CANDIDATE",
                "生成局部AI编辑候选，未覆盖正文");
        return new BidCommandService.SectionRevisionCandidate(
                UUID.randomUUID().toString(), normalizedMode, revision, candidate.html(),
                candidate.changeSummary(), support.safeList(candidate.preservedFacts()),
                support.safeList(candidate.warnings()));
    }

    @Transactional
    BidExport createExport(String bidId, OperationContext context) {
        BidDocument bid = support.requireBid(bidId, context);
        BidWorkspace workspace = bidRepository.loadWorkspace(bid);
        if (!"FROZEN".equals(workspace.production().contentStatus()) || bid.contentStale()) {
            throw new BusinessException(
                    "BID_CONTENT_NOT_FROZEN", "请先完成成稿审查并冻结正文", 409);
        }
        if (workspace.chapters().isEmpty()
                || workspace.chapters().stream().noneMatch(chapter -> !chapter.content().isBlank())) {
            throw new BusinessException(
                    "BID_STEP_NOT_READY", "正文尚未生成，不能导出", 409);
        }
        int version = bidRepository.nextExportVersion(bidId);
        byte[] content = exporter.renderDocx(bid, workspace.outline(), workspace.chapters());
        String fileName = safeFileName(bid.title()) + "-V"
                + String.format(Locale.ROOT, "%02d", version) + ".docx";
        StoredFile stored = fileStorage.store(
                "bids/" + bidId + "/exports", fileName,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);
        String exportId = UUID.randomUUID().toString();
        try {
            bidRepository.insertExport(new BidRepository.ExportRecord(
                    exportId, bidId, version, fileName, stored.objectKey(), stored.size(),
                    context.user().id(), null, "NOT_CHECKED"));
            support.audit(context, bidId, "BID_EXPORT_CREATE", "生成标书DOCX成果V" + version);
        } catch (RuntimeException exception) {
            fileStorage.delete(stored.objectKey());
            throw exception;
        }
        return bidRepository.listExports(bidId).stream()
                .filter(item -> item.id().equals(exportId)).findFirst().orElseThrow();
    }

    private TenderAiGateway.FrozenDictionary frozenDictionary(BidWorkspace workspace) {
        List<TenderAiGateway.Metric> metrics = new ArrayList<>();
        List<TenderAiGateway.Term> terms = new ArrayList<>();
        List<TenderAiGateway.FixedFact> fixedFacts = new ArrayList<>();
        workspace.production().frozenFacts().forEach(fact -> {
            if ("METRIC".equals(fact.type())) {
                metrics.add(new TenderAiGateway.Metric(
                        fact.name(), fact.value(), fact.sourceLocator()));
            } else if ("TERM".equals(fact.type())) {
                terms.add(new TenderAiGateway.Term(fact.value(), fact.forbiddenValues()));
            } else {
                fixedFacts.add(new TenderAiGateway.FixedFact(
                        fact.name(), fact.value(), fact.sourceLocator()));
            }
        });
        return new TenderAiGateway.FrozenDictionary(metrics, terms, fixedFacts);
    }

    private String safeFileName(String title) {
        String cleaned = title.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return cleaned.isBlank() ? "投标文件" : cleaned;
    }
}
