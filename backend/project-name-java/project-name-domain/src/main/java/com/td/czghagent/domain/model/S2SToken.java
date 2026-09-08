// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

/**
 * 一张面向某个被调方的短时凭证（RFC 8693 token exchange）。
 *
 * <p>平台签发的 S2S 票<strong>只活 300 秒且不可刷新</strong>。这条性质决定了整个设计：
 * 票不能进配置文件（粘进 .env 的那张五分钟后就过期，只够通过一次手工冒烟），
 * 必须每次调用现铸并按上下文缓存。
 *
 * <p>{@code tenantId} 是平台<strong>服务端从 workspace 解析</strong>后盖在票上的，
 * 调用方无从伪造，也不需要在自己的配置里维护一份。调 Atlas 时要带的那个
 * {@code tenantId} 就取自这里，而不是从别处推。
 */
public record S2SToken(
        String value,
        String audience,
        Mode mode,
        /** OBO 模式下是用户的 subject；service 模式下为 null——平台刻意不签 sub。 */
        String subject,
        String tenantId,
        LocalDateTime expiresAt
) {

    /**
     * 两种模式，按<strong>有没有用户在场</strong>选，不是按方便选。
     *
     * <p>选错不会报错：用 service 模式跑一次用户触发的操作，被调方的审计里
     * 就少了终端用户归因，而那要等到有人按用户查一次调用链时才发现。
     */
    public enum Mode {
        /**
         * 用户在场。{@code subject_token} 是用户的 access token，
         * 平台从票里解出 org / workspace / user，<strong>调用方无从伪造</strong>。
         */
        ON_BEHALF_OF,
        /**
         * 无用户在场（后台 Job、Temporal 活动）。调用方显式声明工作空间，
         * 平台铸币时校验本产品是否真的覆盖它。
         */
        SERVICE
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
