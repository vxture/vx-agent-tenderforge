// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

public record BidExport(
        String id, String bidId, int version, String fileName,
        long fileSize, String layoutJobId, String qaStatus, LocalDateTime createdAt
) {
}
