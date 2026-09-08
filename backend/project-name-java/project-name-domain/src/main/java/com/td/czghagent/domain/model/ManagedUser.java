// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

public record ManagedUser(
        String id,
        String username,
        String displayName,
        String roleCode,
        String avatarUrl,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long revision
) {
}
