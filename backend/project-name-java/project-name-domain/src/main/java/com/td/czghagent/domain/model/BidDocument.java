// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.model;

import com.td.czghagent.domain.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.Set;

public record BidDocument(
        String id,
        String ownerId,
        String code,
        String writingMethod,
        String title,
        int targetPages,
        String biddingMode,
        String workflowStep,
        String status,
        boolean contentStale,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long revision
) {
    private static final Set<String> BIDDING_MODES = Set.of("OPEN", "BLIND");

    public static void validateSetup(String title, int targetPages, String biddingMode) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.length() < 2 || normalizedTitle.length() > 160) {
            throw new BusinessException("BID_TITLE_INVALID", "标书标题长度必须为2至160个字符", 400);
        }
        if (targetPages < 20 || targetPages > 2000) {
            throw new BusinessException("BID_TARGET_PAGES_INVALID", "预设页数必须为20至2000页", 400);
        }
        if (!BIDDING_MODES.contains(biddingMode)) {
            throw new BusinessException("BID_MODE_INVALID", "投标方式必须为明标或暗标", 400);
        }
    }

    public static void validateWritingMethod(String writingMethod) {
        if (!"SCORING_CRITERIA".equals(writingMethod)) {
            throw new BusinessException("BID_WRITING_METHOD_UNAVAILABLE", "编写专项章节将在后续版本开放", 400);
        }
    }
}
