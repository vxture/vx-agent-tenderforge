// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.util.List;
import java.util.Locale;

/**
 * 从平台 access token 里读出来的声明。
 *
 * <p>token 里<strong>有</strong>的：{@code active_org}、{@code active_workspace}、
 * 治理 {@code roles} —— 四层模型的投影。
 *
 * <p>token 里<strong>没有</strong>的：<strong>entitlement，永远不会有</strong>。
 * token 是签发即冻结的快照，而权益会变；冻进去就只能等过期才失效。
 * 权益走 C2 实时查（{@code GET /platform/entitlements}），别在这里找。
 */
public record PlatformClaims(
        String subject,
        String displayName,
        String email,
        String picture,
        String orgId,
        String workspaceId,
        List<String> roles
) {

    /**
     * 角色前缀分隔符。
     *
     * <p>平台签发的角色是 <strong>带 scope 前缀</strong>的串，形如
     * {@code workspace:owner}、{@code org:admin}。裸比对 {@code "owner"} 必然漏——
     * 这是本仓从 vxtpl 的实测教训里抄来的一条，已铸成下面的 {@link #hasRole}。
     */
    private static final String SCOPE_SEPARATOR = ":";

    public PlatformClaims {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * 判断是否持有某个 scope 下的角色。
     *
     * <p>必须同时给出 scope 和角色名。只给角色名的重载<strong>刻意不提供</strong>：
     * 那个签名会让调用点写出看起来正确的裸比对，而它在 {@code workspace:owner}
     * 面前静默返回 false。
     */
    public boolean hasRole(String scope, String role) {
        String expected = (scope + SCOPE_SEPARATOR + role).toLowerCase(Locale.ROOT);
        return roles.stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).equals(expected));
    }

    /**
     * 租户轴。
     *
     * <p>{@code null} 表示 token 没有携带工作空间——这不是异常，
     * 用户可能还没有被开通到任何工作空间。调用方必须把它当成
     * 「暂时无法进入业务面」，而不是兜底到某个默认空间。
     */
    public TenantScope tenant() {
        if (orgId == null || workspaceId == null) {
            return null;
        }
        return new TenantScope(orgId, workspaceId);
    }

    /**
     * 投影成调用者身份。
     *
     * <p><strong>平台从不签发 {@code admin} 角色</strong>（vxtpl 实测教训），
     * 所以本地的管理员判定不能指望它。这里把角色映射成本地 roleCode 时，
     * 只认工作空间所有者，其余一律是普通编制员——本地授权将来由
     * 「workspace 内业务角色表」接管，而不是由 token 里的治理角色直接决定。
     */
    public CurrentUser toCurrentUser() {
        String roleCode = hasRole("workspace", "owner") ? "ADMIN" : "PLANNER";
        return new CurrentUser(subject, subject, displayName, roleCode, picture, tenant());
    }
}
