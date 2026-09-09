// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 依赖方会话：服务端持有的那一半。
 *
 * <p>浏览器只拿一个不透明 cookie，token 全部留在这里（产品接入通则 C1）。
 * 这不只是防 XSS 窃取——{@code accessToken} 同时是 <strong>S2S 换票的原料</strong>：
 * OBO 模式用它作 {@code subject_token}，平台从票里解出上下文，调用方无从伪造。
 * 一旦 token 下发到前端，这条链就断了。
 */
public record RpSession(
        String id,
        String subject,
        String displayName,
        String email,
        String picture,
        TenantScope tenant,
        String rolesCsv,
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime expiresAt
) {

    /**
     * 提前续期的余量。
     *
     * <p>不等到过期才换：一次调用可能在票有效时开始、在票过期后才到达被调方。
     * 60 秒是取自 vxtpl 的实测值，覆盖了正常的网络与排队延迟。
     */
    public static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    /** access token 是否已到该续期的时候。 */
    public boolean needsRefresh(LocalDateTime now) {
        return accessExpiresAt == null || !now.plus(REFRESH_MARGIN).isBefore(accessExpiresAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public CurrentUser toCurrentUser() {
        String roleCode = rolesCsv != null && rolesCsv.toLowerCase(java.util.Locale.ROOT)
                .contains("workspace:owner") ? "ADMIN" : "PLANNER";
        return new CurrentUser(subject, subject, displayName, roleCode, picture, tenant);
    }
}
