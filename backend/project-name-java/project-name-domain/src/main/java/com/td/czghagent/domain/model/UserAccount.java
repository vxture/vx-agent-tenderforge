// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

public record UserAccount(
        String id,
        String username,
        String passwordHash,
        String displayName,
        String roleCode,
        String avatarUrl,
        String avatarRevision,
        boolean enabled
) {
    public CurrentUser toCurrentUser() {
        String safeAvatarUrl = avatarUrl == null || avatarUrl.isBlank()
                ? null : "/api/account/avatar?v=" + avatarRevision;
        return new CurrentUser(id, username, displayName, roleCode, safeAvatarUrl);
    }
}
