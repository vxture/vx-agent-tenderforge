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
 *
 * <p>{@code orgName}、{@code workspaceName}、{@code email}、{@code phone} 只用于渲染（门禁页身份块），
 * 不作身份键、不作过滤条件。
 */
public record CurrentUser(
        String id,
        String username,
        String displayName,
        String roleCode,
        String avatarUrl,
        TenantScope tenant,
        String orgName,
        String workspaceName,
        String email,
        String phone
) {

    /**
     * 不带组织名、工作空间名与联系方式的调用者。
     *
     * <p>系统身份与测试构造的调用者没有这些可给，就是 null。
     */
    public CurrentUser(String id, String username, String displayName, String roleCode,
                       String avatarUrl, TenantScope tenant) {
        this(id, username, displayName, roleCode, avatarUrl, tenant, null, null, null, null);
    }

    /** 带组织名与工作空间名、不带联系方式的调用者。 */
    public CurrentUser(String id, String username, String displayName, String roleCode,
                       String avatarUrl, TenantScope tenant, String orgName, String workspaceName) {
        this(id, username, displayName, roleCode, avatarUrl, tenant, orgName, workspaceName, null, null);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }
}
