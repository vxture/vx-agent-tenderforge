// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

public record BidSummary(
        String id, String code, String title, int targetPages, String biddingMode,
        String workflowStep, String status, boolean contentStale,
        int completedChapters, int totalChapters, boolean hasExport,
        String layoutStatus, String qaStatus, Integer actualPages, Integer latestExportVersion,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
