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
    /**
     * 投影成调用者身份。
     *
     * <p>租户轴此刻只能是过渡值：本地账号体系里没有组织与工作空间。
     * 接通 OIDC 后，租户轴改由 access token 的 {@code active_org} /
     * {@code active_workspace} 声明提供，本方法随本地账号体系一起退役——
     * 那时唯一要改的就是这一处，因为查询路径从现在起就已经在按租户过滤了。
     */
    public CurrentUser toCurrentUser() {
        String safeAvatarUrl = avatarUrl == null || avatarUrl.isBlank()
                ? null : "/api/account/avatar?v=" + avatarRevision;
        return new CurrentUser(
                id, username, displayName, roleCode, safeAvatarUrl, TenantScope.local(id)
        );
    }
}
