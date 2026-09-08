// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.repository;

public interface AuditRepository {

    void append(
            String userId,
            String actionCode,
            String targetType,
            String targetId,
            String resultCode,
            String detailSummary,
            String traceId,
            String ipAddress
    );
}
