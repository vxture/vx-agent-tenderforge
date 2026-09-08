// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

/**
 * 最小可重试正文写作单元。
 */
public record BidGenerationUnit(
        String id,
        String taskId,
        String bidId,
        String chapterId,
        int unitIndex,
        String unitTitle,
        String idempotencyKey,
        String status,
        int attemptCount,
        int wordBudget,
        Integer visibleCharacters,
        Double budgetVarianceRatio,
        String budgetStatus,
        LocalDateTime compactedAt,
        String content,
        String contentHash,
        String summary,
        String previousSummary,
        String errorMessage,
        LocalDateTime updatedAt
) {
    public BidGenerationUnit(
            String id, String taskId, String bidId, String chapterId, int unitIndex,
            String unitTitle, String idempotencyKey, String status, int attemptCount,
            int wordBudget, String content, String contentHash, String summary,
            String previousSummary, String errorMessage, LocalDateTime updatedAt
    ) {
        this(id, taskId, bidId, chapterId, unitIndex, unitTitle, idempotencyKey,
                status, attemptCount, wordBudget, null, null, "PENDING", null,
                content, contentHash, summary, previousSummary, errorMessage, updatedAt);
    }

    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }
}
