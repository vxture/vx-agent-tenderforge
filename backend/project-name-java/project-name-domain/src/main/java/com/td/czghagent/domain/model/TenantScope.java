// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.util.Objects;

/**
 * 租户轴：一次操作属于哪个组织的哪个工作空间。
 *
 * <p>平台四层模型在产品这一侧的投影。产品<strong>只持引用，不复制平台主数据</strong>
 * ——这里存的是标识，不是组织和空间的副本。
 *
 * <p><strong>它必须永远有值。</strong>可空的租户键会让一次忘记加过滤的查询静默返回
 * 全部租户的行，而那个响应看起来完全正常：数据是对的、格式是对的、只是不该给这个人看。
 * 所以平台身份接通之前也不留空，而是用一个显式的过渡值把查询路径先撑起来。
 */
public record TenantScope(String orgId, String workspaceId) {

    /**
     * 过渡值前缀。
     *
     * <p>刻意不是 UUID：平台签发的 workspace 是 UUID，所以这个前缀不可能与真实值冲突，
     * 而且肉眼一看就知道「这一行还没接上平台身份」。接通 OIDC 后由会话提供真实值，
     * 届时库里还带这个前缀的行就是需要迁移的那些——它自己标记了自己。
     */
    public static final String LOCAL_PREFIX = "local:";

    public TenantScope {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(workspaceId, "workspaceId");
    }

    /**
     * 本地登录用户的过渡租户轴：一个用户一个工作空间。
     *
     * <p>这不是一个业务决定，是一个占位——本地账号体系里没有组织和空间的概念，
     * 而查询路径需要一个键才能从第一天起就带上过滤条件。
     */
    public static TenantScope local(String userId) {
        String scoped = LOCAL_PREFIX + userId;
        return new TenantScope(scoped, scoped);
    }

    /**
     * 是否仍是过渡值。接通平台身份后应恒为 false。
     *
     * <p>刻意<strong>不</strong>叫 {@code isLocal()}：那个名字符合 JavaBean getter 约定，
     * Jackson 会把它当成一个属性序列化出去，于是一个内部派生标志就出现在了对外契约上
     * ——而契约上出现过的字段，早晚会有人依赖它。实测确认过它会以 {@code "local": true}
     * 的形式出现在标书响应里。
     */
    public boolean usesLocalPlaceholder() {
        return workspaceId.startsWith(LOCAL_PREFIX);
    }
}
