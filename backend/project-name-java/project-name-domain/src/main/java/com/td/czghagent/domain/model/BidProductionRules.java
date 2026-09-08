// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.domain.model;

import com.td.czghagent.domain.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BidProductionRules {
    private static final Pattern UUID_TOKEN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"
    );
    private static final Pattern INTERNAL_FIELD_TOKEN = Pattern.compile(
            "(?i)\\b(?:biddingMode|chapterId|criterionId|outlineNodeId|sourceLocator|generationStatus)\\s*[:=]"
    );

    private BidProductionRules() {
    }

    public static void validateInterpretationFreeze(List<BidWorkspace.Criterion> criteria) {
        if (!hasCompleteInterpretation(criteria)) {
            throw new BusinessException(
                    "BID_CRITERIA_REQUIRED", "项目概述和技术部分评分要求都必须填写", 400);
        }
    }

    public static void validateOutlineFreeze(
            List<BidWorkspace.OutlineNode> outline,
            List<BidWorkspace.Criterion> criteria
    ) {
        if (outline.isEmpty()) {
            throw new BusinessException("BID_OUTLINE_INVALID", "目录不能为空", 400);
        }
        Set<String> parentIds = new HashSet<>();
        outline.stream().map(BidWorkspace.OutlineNode::parentId)
                .filter(value -> value != null).forEach(parentIds::add);
        List<BidWorkspace.OutlineNode> leaves = outline.stream()
                .filter(node -> !parentIds.contains(node.id())).toList();
        List<BidWorkspace.OutlineNode> roots = outline.stream()
                .filter(node -> node.level() == 1).toList();
        boolean invalidPageBudget = roots.isEmpty()
                || roots.stream().anyMatch(node -> node.plannedPages() < 1)
                || outline.stream().anyMatch(node -> node.level() > 1 && node.plannedPages() != 0);
        if (leaves.isEmpty() || leaves.stream().anyMatch(node -> node.level() != 3)
                || invalidPageBudget) {
            throw new BusinessException(
                    "BID_OUTLINE_INVALID", "目录叶子必须为三级，且仅一级章节可以规划页数", 400);
        }
    }

    public static void validateContentFreeze(
            List<BidWorkspace.Chapter> chapters,
            List<BidProductionState.ReviewIssue> issues
    ) {
        if (chapters.isEmpty() || chapters.stream().anyMatch(item -> item.content().isBlank())) {
            throw new BusinessException("BID_CONTENT_INCOMPLETE", "正文仍有未完成章节", 409);
        }
        boolean blocking = issues.stream().anyMatch(item ->
                "ERROR".equals(item.severity()) && "OPEN".equals(item.status()));
        if (blocking) {
            throw new BusinessException("BID_REVIEW_ERRORS_OPEN", "请先处理全部阻断级成稿问题", 409);
        }
    }

    public static String interpretationHash(List<BidWorkspace.Criterion> criteria) {
        List<String> values = new ArrayList<>();
        criteria.stream().sorted(Comparator.comparingInt(BidWorkspace.Criterion::sortOrder))
                .forEach(item -> values.add(String.join("\u001f", safe(item.type()), safe(item.title()),
                        safe(item.description()), String.valueOf(item.score()), safe(item.sourceLocator()),
                        safe(item.sourceExcerpt()), safe(item.scope()), safe(item.confidence()))));
        return sha256(values);
    }

    public static String outlineHash(List<BidWorkspace.OutlineNode> outline) {
        List<String> values = new ArrayList<>();
        outline.stream().sorted(Comparator.comparingInt(BidWorkspace.OutlineNode::sortOrder))
                .forEach(node -> values.add(String.join("\u001f", node.id(), safe(node.parentId()),
                        String.valueOf(node.level()), safe(node.title()), String.valueOf(node.plannedPages()),
                        safe(node.taskBrief()), String.join("\u001e", node.mustKeywords()),
                        String.join("\u001e", node.scoringPointIds()))));
        return sha256(values);
    }

    public static String contentHash(List<BidWorkspace.Chapter> chapters) {
        List<String> values = chapters.stream()
                .map(item -> item.id() + "\u001f" + safe(item.content()).trim())
                .toList();
        return sha256(values);
    }

    public static String generationSnapshotHash(String interpretationHash, String outlineHash) {
        return sha256(List.of(safe(interpretationHash), safe(outlineHash)));
    }

    public static String generationSnapshotHash(
            BidWorkspace workspace,
            List<BidReferenceChunk> referenceChunks,
            String promptVersion
    ) {
        List<String> values = new ArrayList<>();
        values.add(safe(workspace.production().interpretationHash()));
        values.add(safe(workspace.production().outlineHash()));
        values.add(workspace.bid().title());
        values.add(workspace.bid().biddingMode());
        values.add(String.valueOf(workspace.bid().targetPages()));
        values.add(safe(promptVersion));
        referenceChunks.stream().sorted(Comparator.comparing(BidReferenceChunk::assetId)
                        .thenComparingInt(BidReferenceChunk::chunkIndex))
                .forEach(chunk -> values.add(chunk.id() + "\u001f" + chunk.contentHash()));
        return sha256(values);
    }

    public static List<BidWorkspace.Criterion> effectiveCriteria(BidWorkspace workspace) {
        return workspace.criteria().stream()
                .filter(item -> Set.of("PROJECT_OVERVIEW", "TECHNICAL_SCORING")
                        .contains(item.type()))
                .sorted(Comparator.comparingInt(BidWorkspace.Criterion::sortOrder))
                .toList();
    }

    public static boolean hasCompleteInterpretation(List<BidWorkspace.Criterion> criteria) {
        return criteria.size() == 2
                && hasSingleNonBlank(criteria, "PROJECT_OVERVIEW")
                && hasSingleNonBlank(criteria, "TECHNICAL_SCORING");
    }

    private static boolean hasSingleNonBlank(
            List<BidWorkspace.Criterion> criteria, String type
    ) {
        List<BidWorkspace.Criterion> matches = criteria.stream()
                .filter(item -> type.equals(item.type())).toList();
        return matches.size() == 1 && !safe(matches.getFirst().description()).isBlank();
    }

    public static boolean isScoringCriterion(BidWorkspace.Criterion criterion) {
        return "SCORING".equals(criterion.type()) && criterion.score() != null;
    }

    public static boolean aiIssueHasForbiddenValueEvidence(
            String chapterId,
            String message,
            String suggestion,
            List<BidWorkspace.Chapter> chapters,
            List<BidProductionState.FrozenFact> frozenFacts
    ) {
        String issueText = safe(message) + " " + safe(suggestion);
        Set<String> referencedForbiddenValues = new HashSet<>();
        frozenFacts.forEach(fact -> fact.forbiddenValues().stream()
                .filter(value -> value != null && !value.isBlank() && issueText.contains(value))
                .forEach(referencedForbiddenValues::add));
        if (referencedForbiddenValues.isEmpty()) {
            return true;
        }
        return chapters.stream()
                .filter(chapter -> chapterId == null || chapterId.isBlank() || chapter.id().equals(chapterId))
                .anyMatch(chapter -> referencedForbiddenValues.stream()
                        .anyMatch(value -> safe(chapter.content()).contains(value)));
    }

    /**
     * Detects implementation identifiers that must never appear in reviewer-visible bid prose.
     *
     * <p><b>Preconditions:</b> chapter content may contain trusted editor HTML.</p>
     * <p><b>Side Effects:</b> none.</p>
     * <p><b>Error Semantics:</b> returns an empty list when no visible leak exists.</p>
     */
    public static List<ContentLeak> findInternalContentLeaks(List<BidWorkspace.Chapter> chapters) {
        List<ContentLeak> leaks = new ArrayList<>();
        for (BidWorkspace.Chapter chapter : chapters) {
            String visibleText = safe(chapter.content()).replaceAll("<[^>]+>", " ");
            addFirstLeak(leaks, chapter.id(), "INTERNAL_IDENTIFIER_LEAK", UUID_TOKEN.matcher(visibleText));
            addFirstLeak(leaks, chapter.id(), "INTERNAL_FIELD_LEAK", INTERNAL_FIELD_TOKEN.matcher(visibleText));
        }
        return List.copyOf(leaks);
    }

    private static void addFirstLeak(List<ContentLeak> leaks, String chapterId,
                                     String code, Matcher matcher) {
        if (matcher.find()) {
            leaks.add(new ContentLeak(chapterId, code, matcher.group()));
        }
    }

    private static String sha256(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            values.forEach(value -> digest.update((value + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKey(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", "");
    }

    private static BidWorkspace.Criterion normalizeCriterion(BidWorkspace.Criterion criterion) {
        if (!"SCORING".equals(criterion.type()) || criterion.score() != null) {
            return criterion;
        }
        return new BidWorkspace.Criterion(
                criterion.id(), "FACT", criterion.title(), criterion.description(), null,
                criterion.sourceExcerpt(), criterion.sourceLocator(), criterion.scope(),
                criterion.confidence(), criterion.sortOrder(), criterion.manuallyEdited());
    }

    public record ContentLeak(String chapterId, String code, String token) {
    }
}
