// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@Service
class BidContentReviewService {
    private final BidRepository bidRepository;
    private final BidProductionRepository productionRepository;
    private final BidAiExecutionService aiExecutionService;

    BidContentReviewService(BidRepository bidRepository,
                            BidProductionRepository productionRepository,
                            BidAiExecutionService aiExecutionService) {
        this.bidRepository = bidRepository;
        this.productionRepository = productionRepository;
        this.aiExecutionService = aiExecutionService;
    }

    void reviewNow(String bidId, String ownerId, String requestId) {
        BidWorkspace workspace = loadWorkspace(bidId, ownerId);
        if (workspace.chapters().isEmpty()) {
            throw new BusinessException("BID_CONTENT_INCOMPLETE", "正文尚未生成", 409);
        }
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>(
                BidContentQualityGuard.reviewWorkspace(workspace));
        issues.addAll(forbiddenFactIssues(workspace.chapters(), workspace.production().frozenFacts()));
        try {
            TenderAiGateway.Review review = aiExecutionService.review(
                    bidId, null, null, "review-v1", new TenderAiGateway.ReviewRequest(
                            safe(requestId, "manual") + "-review", liveReviewPayload(workspace)));
            appendValidatedAiIssues(
                    issues, review, workspace.chapters(), workspace.production().frozenFacts());
        } catch (BusinessException exception) {
            addReviewUnavailable(issues);
        }
        productionRepository.replaceReviewIssues(bidId, dedupe(issues));
    }

    List<BidProductionState.ReviewIssue> reviewSnapshotStrict(
            String taskId, BidGenerationSnapshot snapshot,
            List<BidWorkspace.Chapter> chapters, List<BidGenerationUnit> units
    ) {
        List<BidProductionState.FrozenFact> facts = frozenFacts(snapshot);
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>(
                BidContentQualityGuard.reviewSnapshot(snapshot, chapters));
        issues.addAll(forbiddenFactIssues(chapters, facts));
        TenderAiGateway.Review result = aiExecutionService.review(
                snapshot.bidId(), taskId, snapshot.id(), "review-v2",
                new TenderAiGateway.ReviewRequest(
                        taskId + "-review", snapshotReviewPayload(snapshot, chapters, units)));
        appendValidatedAiIssues(issues, result, chapters, facts);
        return dedupe(issues);
    }

    private List<BidProductionState.ReviewIssue> forbiddenFactIssues(
            List<BidWorkspace.Chapter> chapters, List<BidProductionState.FrozenFact> facts
    ) {
        List<BidProductionState.ReviewIssue> issues = new ArrayList<>();
        BidProductionRules.findInternalContentLeaks(chapters).forEach(leak -> issues.add(
                issue(leak.chapterId(), "ERROR", leak.code(),
                        "章节正文出现系统内部信息：" + leak.token(), "删除内部标识并改为评审语言")));
        for (BidWorkspace.Chapter chapter : chapters) {
            facts.forEach(fact -> fact.forbiddenValues().stream()
                    .filter(value -> !value.isBlank() && chapter.content().contains(value))
                    .forEach(value -> issues.add(issue(
                            chapter.id(), "ERROR", "FORBIDDEN_FACT_VALUE",
                            "章节出现已废弃口径：" + value, "按冻结口径“" + fact.value() + "”修改"))));
        }
        return issues;
    }

    private void appendValidatedAiIssues(
            List<BidProductionState.ReviewIssue> issues, TenderAiGateway.Review review,
            List<BidWorkspace.Chapter> chapters, List<BidProductionState.FrozenFact> facts
    ) {
        for (TenderAiGateway.ReviewIssue item : review.issues()) {
            String chapterId = resolveChapterId(item.chapterId(), chapters);
            if (chapterId == null) {
                issues.add(issue(null, "WARN", "AI_REVIEW_UNLOCATABLE",
                        "AI审校问题未能定位到当前正文章节，已转人工复核："
                                + safe(item.message(), "未提供问题描述"),
                        safe(item.suggestion(), "请人工确认对应章节和修订口径")));
                continue;
            }
            if (BidProductionRules.aiIssueHasForbiddenValueEvidence(
                    chapterId, item.message(), item.suggestion(), chapters, facts)) {
                issues.add(issue(chapterId, normalizeAiSeverity(
                                item.severity(), item.code(), item.message()), item.code(),
                        item.message(), item.suggestion()));
            }
        }
    }

    /**
     * Evidence that must come from the bidder cannot be fabricated by text
     * generation. Keep it visible for manual completion, but never send the
     * document through futile automatic rewrite rounds. Response-time
     * differences are also review-only because SLA values are commonly tiered
     * by incident severity.
     */
    static String normalizeAiSeverity(String severity, String code, String message) {
        String normalizedCode = safe(code, "").toUpperCase(Locale.ROOT);
        String normalizedMessage = safe(message, "");
        boolean manualEvidence = List.of(
                        "SCREENSHOT", "CERTIFICATE", "AUTHORIZATION", "PROOF",
                        "EVIDENCE", "LICENSE", "QUALIFICATION", "ATTACHMENT")
                .stream().anyMatch(normalizedCode::contains)
                || List.of("截图", "证书", "授权", "证明材料", "资质", "附件")
                .stream().anyMatch(normalizedMessage::contains);
        boolean tieredSla = normalizedCode.contains("RESPONSE_TIME")
                || normalizedCode.contains("SERVICE_TIME")
                || normalizedCode.contains("SLA");
        boolean advisoryCoverage = List.of(
                        "COVERAGE", "SCORING", "MISSING_REQUIREMENT", "INCOMPLETE_RESPONSE")
                .stream().anyMatch(normalizedCode::contains)
                || List.of("评分", "覆盖", "响应不充分", "响应不完整", "未体现")
                .stream().anyMatch(normalizedMessage::contains);
        return manualEvidence || tieredSla || advisoryCoverage
                ? "WARN" : safe(severity, "WARN").toUpperCase(Locale.ROOT);
    }

    private List<BidProductionState.ReviewIssue> dedupe(
            List<BidProductionState.ReviewIssue> issues
    ) {
        Map<String, BidProductionState.ReviewIssue> unique = new java.util.LinkedHashMap<>();
        for (BidProductionState.ReviewIssue issue : issues) {
            String key = String.join("|",
                    safe(issue.chapterId(), ""), issue.severity(), issue.code(),
                    issue.message(), issue.suggestion());
            unique.putIfAbsent(key, issue);
        }
        return List.copyOf(unique.values());
    }

    private Map<String, Object> snapshotReviewPayload(
            BidGenerationSnapshot snapshot, List<BidWorkspace.Chapter> chapters,
            List<BidGenerationUnit> units
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("writingBible", snapshot.writingBible());
        payload.put("termRegistry", snapshot.termRegistry());
        payload.put("commitmentRegistry", snapshot.commitmentRegistry());
        payload.put("requirements", snapshot.requirements());
        Map<String, BidWorkspace.Chapter> chapterById = chapters.stream().collect(
                java.util.stream.Collectors.toMap(BidWorkspace.Chapter::id, item -> item));
        payload.put("chapters", snapshot.outline().stream().filter(node -> node.chapterId() != null)
                .map(node -> chapterReviewPayload(node, chapterById.get(node.chapterId()), units))
                .toList());
        return payload;
    }

    private Map<String, Object> chapterReviewPayload(
            BidGenerationSnapshot.Outline node, BidWorkspace.Chapter chapter,
            List<BidGenerationUnit> units
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("chapterId", node.chapterId());
        result.put("title", node.title());
        result.put("summary", units.stream()
                .filter(unit -> node.chapterId().equals(unit.chapterId()))
                .map(BidGenerationUnit::summary).filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining("；")));
        String content = chapter == null ? "" : safe(chapter.content(), "");
        result.put("contentExcerpt", content.substring(0, Math.min(16_000, content.length())));
        return result;
    }

    private Map<String, Object> liveReviewPayload(BidWorkspace workspace) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("criteria", workspace.criteria());
        payload.put("chapters", workspace.chapters().stream().map(chapter -> Map.of(
                "chapterId", chapter.id(), "title", chapter.title(),
                "contentExcerpt", chapter.content().substring(0, Math.min(12_000, chapter.content().length()))
        )).toList());
        return payload;
    }

    private List<BidProductionState.FrozenFact> frozenFacts(BidGenerationSnapshot snapshot) {
        return snapshot.facts().stream().map(fact -> new BidProductionState.FrozenFact(
                UUID.randomUUID().toString(), fact.type(), fact.name(), fact.value(),
                fact.sourceLocator(), fact.forbiddenValues(), fact.sortOrder())).toList();
    }

    private BidWorkspace loadWorkspace(String bidId, String ownerId) {
        BidDocument bid = bidRepository.findBid(bidId, ownerId).orElseThrow();
        return bidRepository.loadWorkspace(bid);
    }

    private void addReviewUnavailable(List<BidProductionState.ReviewIssue> issues) {
        issues.add(issue(null, "WARNING", "AI_REVIEW_UNAVAILABLE",
                "AI 全文一致性审查暂不可用", "可先人工审查，模型恢复后重新执行"));
    }

    private BidProductionState.ReviewIssue issue(
            String chapterId, String severity, String code, String message, String suggestion
    ) {
        return new BidProductionState.ReviewIssue(
                UUID.randomUUID().toString(), chapterId, severity, code, message, suggestion, "OPEN");
    }

    private String resolveChapterId(String candidate, List<BidWorkspace.Chapter> chapters) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        List<BidWorkspace.Chapter> direct = chapters.stream()
                .filter(chapter -> candidate.equals(chapter.id())).toList();
        if (direct.size() == 1) {
            return direct.getFirst().id();
        }
        List<BidWorkspace.Chapter> byOutline = chapters.stream()
                .filter(chapter -> candidate.equals(chapter.outlineNodeId())).toList();
        return byOutline.size() == 1 ? byOutline.getFirst().id() : null;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
