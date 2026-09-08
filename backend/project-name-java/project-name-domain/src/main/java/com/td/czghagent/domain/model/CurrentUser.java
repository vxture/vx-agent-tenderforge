// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

public record CurrentUser(
        String id,
        String username,
        String displayName,
        String roleCode,
        String avatarUrl
) {
    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }
}
