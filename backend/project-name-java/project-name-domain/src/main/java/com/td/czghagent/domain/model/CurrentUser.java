// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

/**
 * 已认证的调用者。
 *
 * <p>{@link TenantScope} 与身份绑在一起而不是各自传递：每一处「拿到了用户」的地方
 * 同时就拿到了它属于哪个工作空间，于是仓储层可以无条件地按租户过滤。
 * 分开传的版本里，总有一条路径会只带用户不带租户，而那条路径不会报错。
 */
public record CurrentUser(
        String id,
        String username,
        String displayName,
        String roleCode,
        String avatarUrl,
        TenantScope tenant
) {
    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }
}
