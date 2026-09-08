// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import com.td.czghagent.domain.model.BidProductionRules;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class BidContentFinalizationService {
    private static final int MAX_AUTO_REVISION_ROUNDS = 4;
    private final BidRepository bidRepository;
    private final BidProductionRepository productionRepository;
    private final BidContentReviewService reviewService;
    private final BidAiExecutionService aiExecutionService;
    private final BidStyleEditorialService styleEditorialService;
    private final BidLayoutProcessor layoutProcessor;

    BidContentFinalizationService(BidRepository bidRepository,
                                  BidProductionRepository productionRepository,
                                  BidContentReviewService reviewService,
                                  BidAiExecutionService aiExecutionService,
                                  BidStyleEditorialService styleEditorialService,
                                  BidLayoutProcessor layoutProcessor) {
        this.bidRepository = bidRepository;
        this.productionRepository = productionRepository;
        this.reviewService = reviewService;
        this.aiExecutionService = aiExecutionService;
        this.styleEditorialService = styleEditorialService;
        this.layoutProcessor = layoutProcessor;
    }

    /**
     * 自动完成正文复审、修订、冻结和正式排版。
     *
     * <p><b>Preconditions:</b> 所有正文写作单元已经成功或跳过。</p>
     * <p><b>Side Effects:</b> 保存自动修订版本、审查问题、冻结正文并创建 DOCX 成果。</p>
     * <p><b>Error Semantics:</b> 四轮自动修订并完成末次复核后仍有阻断问题，
     * 或排版 QA 失败时终止整个生成任务。</p>
     */
    void complete(String taskId, String ownerId, BidGenerationSnapshot snapshot,
                  List<BidGenerationUnit> units) {
        BidWorkspace initial = loadWorkspace(snapshot.bidId(), ownerId);
        if (layoutSucceeded(initial)) {
            finishTask(taskId, snapshot.bidId());
            return;
        }
        productionRepository.appendEvent(
                taskId, snapshot.bidId(), null, "AUTO_REVIEW_STARTED", "正文生成完成，开始自动审查");
        styleEditorialService.polish(taskId, ownerId, snapshot, initial);
        reviewAndRevise(taskId, ownerId, snapshot, units);
        BidWorkspace reviewed = loadWorkspace(snapshot.bidId(), ownerId);
        freezeContent(taskId, reviewed);
        runLayout(taskId, ownerId, loadWorkspace(snapshot.bidId(), ownerId));
        finishTask(taskId, snapshot.bidId());
    }

    private void reviewAndRevise(String taskId, String ownerId,
                                 BidGenerationSnapshot snapshot,
                                 List<BidGenerationUnit> units) {
        for (int reviewRound = 1;
             reviewRound <= MAX_AUTO_REVISION_ROUNDS + 1;
             reviewRound++) {
            BidWorkspace workspace = loadWorkspace(snapshot.bidId(), ownerId);
            List<BidProductionState.ReviewIssue> issues = reviewService.reviewSnapshotStrict(
                    taskId, snapshot, workspace.chapters(), units);
            productionRepository.replaceReviewIssues(snapshot.bidId(), issues);
            List<BidProductionState.ReviewIssue> errors = blockingIssues(issues);
            if (errors.isEmpty()) {
                productionRepository.appendEvent(taskId, snapshot.bidId(), null,
                        "AUTO_REVIEW_PASSED", "第 " + reviewRound + " 轮自动审查通过");
                return;
            }
            productionRepository.appendEvent(taskId, snapshot.bidId(), null,
                    "AUTO_REVIEW_ISSUES", "第 " + reviewRound + " 轮审查发现 "
                            + errors.size() + " 项阻断问题");
            if (reviewRound > MAX_AUTO_REVISION_ROUNDS) {
                throw new BusinessException(
                        "BID_AUTO_REVIEW_FAILED", "自动审查仍有阻断问题：" + issueSummary(errors), 502);
            }
            reviseChapters(taskId, ownerId, snapshot, workspace, errors, reviewRound);
        }
    }

    private void reviseChapters(String taskId, String ownerId,
                                BidGenerationSnapshot snapshot, BidWorkspace workspace,
                                List<BidProductionState.ReviewIssue> issues, int round) {
        Map<String, List<BidProductionState.ReviewIssue>> byChapter = new LinkedHashMap<>();
        issues.stream().filter(issue -> issue.chapterId() != null && !issue.chapterId().isBlank())
                .forEach(issue -> byChapter.computeIfAbsent(
                        issue.chapterId(), ignored -> new ArrayList<>()).add(issue));
        if (byChapter.isEmpty()) {
            throw new BusinessException(
                    "BID_AUTO_REVIEW_UNRESOLVED", "自动审查发现全局阻断问题，无法定位修订章节", 502);
        }
        Map<String, BidWorkspace.Chapter> chapters = workspace.chapters().stream().collect(
                java.util.stream.Collectors.toMap(BidWorkspace.Chapter::id, item -> item));
        for (Map.Entry<String, List<BidProductionState.ReviewIssue>> entry : byChapter.entrySet()) {
            BidWorkspace.Chapter chapter = chapters.get(entry.getKey());
            if (chapter == null || chapter.content().isBlank()) {
                throw new BusinessException(
                        "BID_AUTO_REVIEW_CHAPTER_MISSING", "自动修订对应章节不存在或正文为空", 502);
            }
            TenderAiGateway.RevisionCandidate candidate = aiExecutionService.revise(
                    snapshot.bidId(), taskId, snapshot.id(), "auto-review-revision-v1",
                    new TenderAiGateway.RevisionRequest(
                            taskId + "-auto-review-" + round + "-" + chapter.id(),
                            "REWRITE", chapter.content(), "", "",
                            BidContentQualityGuard.reviewInstruction(entry.getValue()),
                            BidContentQualityGuard.protectedFacts(snapshot), frozenDictionary(snapshot)));
            if (candidate.html() == null || candidate.html().isBlank()) {
                throw new BusinessException(
                        "BID_AUTO_REVIEW_SAVE_FAILED", "自动修订未返回有效正文", 502);
            }
            String revisedHtml = BidContentQualityGuard.normalizeGeneratedLanguage(
                    snapshot.biddingMode(), candidate.html());
            if (!productionRepository.applyReviewedChapter(
                    snapshot.bidId(), chapter.id(), chapter.revision(), revisedHtml)) {
                throw new BusinessException(
                        "BID_AUTO_REVIEW_SAVE_FAILED", "自动修订正文保存失败", 409);
            }
            productionRepository.saveChapterVersion(
                    snapshot.bidId(), chapter.id(), "AUTO_REVIEW", revisedHtml,
                    contentHash(chapter, revisedHtml), candidate.changeSummary(), ownerId);
            productionRepository.appendEvent(taskId, snapshot.bidId(), chapter.id(),
                    "AUTO_REVISION_COMPLETED", "第 " + round + " 轮自动修订已完成：" + chapter.title());
        }
    }

    private void freezeContent(String taskId, BidWorkspace workspace) {
        BidProductionRules.validateContentFreeze(
                workspace.chapters(), workspace.production().reviewIssues());
        String contentHash = BidProductionRules.contentHash(workspace.chapters());
        if (!"FROZEN".equals(workspace.production().contentStatus())
                || !contentHash.equals(workspace.production().contentHash())) {
            productionRepository.freezeContent(workspace.bid().id(), contentHash);
        }
        productionRepository.appendEvent(
                taskId, workspace.bid().id(), null, "CONTENT_FROZEN", "正文审查通过，已自动冻结成稿");
    }

    private void runLayout(String taskId, String ownerId, BidWorkspace workspace) {
        BidProductionState.LayoutJob latest = workspace.production().layoutJob();
        if (layoutMatchesContent(
                latest, workspace.production().contentStatus(),
                workspace.production().contentHash())) {
            return;
        }
        boolean currentJobRunning = latest != null
                && List.of("PENDING", "RUNNING").contains(latest.status())
                && java.util.Objects.equals(
                latest.inputHash(), workspace.production().contentHash());
        String jobId = currentJobRunning
                ? latest.id() : productionRepository.createLayoutJob(
                workspace.bid().id(), ownerId, workspace.production().contentHash(),
                workspace.bid().targetPages());
        productionRepository.appendEvent(
                taskId, workspace.bid().id(), null, "LAYOUT_STARTED", "自动审查通过，开始正式排版");
        try {
            layoutProcessor.process(jobId, workspace.bid().id(), ownerId);
        } catch (RuntimeException exception) {
            layoutProcessor.fail(jobId, workspace.bid().id(), exception.getMessage());
            throw exception;
        }
        productionRepository.appendEvent(
                taskId, workspace.bid().id(), null, "LAYOUT_COMPLETED", "正式排版和质量检查已完成");
    }

    private void finishTask(String taskId, String bidId) {
        bidRepository.completeGenerationTask(taskId, bidId);
        productionRepository.appendEvent(
                taskId, bidId, null, "TASK_COMPLETED", "正文编写、自动审查和正式排版全部完成");
    }

    private List<BidProductionState.ReviewIssue> blockingIssues(
            List<BidProductionState.ReviewIssue> issues) {
        return issues.stream().filter(issue -> "OPEN".equals(issue.status()))
                .filter(issue -> "ERROR".equals(issue.severity())).toList();
    }

    private String issueSummary(List<BidProductionState.ReviewIssue> issues) {
        String value = issues.stream().limit(4)
                .map(issue -> issue.code() + "：" + issue.message())
                .collect(java.util.stream.Collectors.joining("；"));
        return value.substring(0, Math.min(700, value.length()));
    }

    private String revisionInstruction(List<BidProductionState.ReviewIssue> issues) {
        String details = issues.stream().map(issue ->
                "问题：" + issue.message() + "；修改要求：" + issue.suggestion())
                .collect(java.util.stream.Collectors.joining("\n"));
        return "仅修复下列审查问题，保持章节结构、事实、表格及其他合格内容不变。"
                + "正文不得出现‘对应段落’或生成过程说明；确需说明追踪关系时使用‘对应要求：具体要求内容’。\n"
                + details;
    }

    private List<String> protectedFacts(BidGenerationSnapshot snapshot) {
        List<String> result = new ArrayList<>();
        snapshot.facts().forEach(fact -> result.add(fact.name() + "：" + fact.value()));
        snapshot.requirements().forEach(item -> result.add(item.title() + "：" + item.description()));
        return List.copyOf(result);
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

    private BidWorkspace loadWorkspace(String bidId, String ownerId) {
        BidDocument bid = bidRepository.findBid(bidId, ownerId).orElseThrow(() ->
                new BusinessException("BID_NOT_FOUND", "正文最终化对应标书不存在", 404));
        return bidRepository.loadWorkspace(bid);
    }

    private boolean layoutSucceeded(BidWorkspace workspace) {
        return layoutMatchesContent(
                workspace.production().layoutJob(), workspace.production().contentStatus(),
                workspace.production().contentHash())
                && !workspace.exports().isEmpty();
    }

    static boolean layoutMatchesContent(
            BidProductionState.LayoutJob layoutJob,
            String contentStatus,
            String contentHash
    ) {
        return layoutJob != null
                && "FROZEN".equals(contentStatus)
                && contentHash != null
                && !contentHash.isBlank()
                && "SUCCEEDED".equals(layoutJob.status())
                && contentHash.equals(layoutJob.inputHash());
    }
}
