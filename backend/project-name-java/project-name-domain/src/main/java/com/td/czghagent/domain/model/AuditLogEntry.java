// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

public record AuditLogEntry(
        String id,
        String userId,
        String username,
        String targetName,
        String actionCode,
        String targetType,
        String targetId,
        String resultCode,
        String detailSummary,
        String traceId,
        String ipAddress,
        LocalDateTime createdAt
) {
}
