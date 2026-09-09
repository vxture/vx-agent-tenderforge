// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.td.czghagent.domain.model.DeployStage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OIDC 依赖方配置。
 *
 * <p><strong>client_id 不能反推产品码</strong>：beta 环境的 client 是
 * {@code tenderforge-beta}，产品码仍是 {@code tenderforge}。产品身份的唯一来源是
 * {@code ProductIdentity.PRODUCT_CODE}，这里只管认证。
 *
 * <p>这一对 client 凭据<strong>同时是 S2S 凭据</strong>：RFC 8693 换票用的就是它，
 * 没有另一份要申请。
 */
@Component
public class OidcProperties {

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String postLogoutRedirectUri;
    private final String scopes;
    private final boolean enabled;
    private final long sessionSeconds;
    private final DeployStage deployStage;

    public OidcProperties(
            @Value("${app.oidc.issuer:}") String issuer,
            @Value("${app.oidc.client-id:}") String clientId,
            @Value("${app.oidc.client-secret:}") String clientSecret,
            @Value("${app.oidc.redirect-uri:}") String redirectUri,
            @Value("${app.oidc.post-logout-redirect-uri:}") String postLogoutRedirectUri,
            @Value("${app.oidc.scopes:openid profile email phone}") String scopes,
            @Value("${app.oidc.enabled:false}") boolean enabled,
            @Value("${app.oidc.session-seconds:43200}") long sessionSeconds,
            @Value("${app.deploy-stage:local}") String deployStage
    ) {
        this.issuer = trimTrailingSlash(issuer);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
        this.scopes = scopes;
        this.enabled = enabled;
        this.sessionSeconds = sessionSeconds;
        this.deployStage = DeployStage.parse(deployStage);
    }

    /**
     * 配置是否完整到可以真的走一遍登录。
     *
     * <p>与 {@link #isEnabled()} 分开：开关说的是「想不想开」，这个说的是「能不能开」。
     * 两者混成一个布尔，会让「开了但缺 secret」表现为一条不可能成功的登录跳转，
     * 而不是一条可以被 /api/status 报出来的配置缺失。
     */
    public boolean isConfigured() {
        return notBlank(issuer) && notBlank(clientId) && notBlank(clientSecret)
                && notBlank(redirectUri);
    }

    /**
     * 是否应该真的拦截请求。
     *
     * <p>{@code enabled} 为假时<strong>永不拦截</strong>——配错的部署应该由
     * {@code /api/status} 报警，而不是把所有人锁在门外。一个登录不了的产品
     * 和一个宕机的产品，对用户是同一件事，但对排障的人不是。
     */
    public boolean isActive() {
        return enabled && isConfigured();
    }

    public String issuer() {
        return issuer;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String postLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    public String scopes() {
        return scopes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long sessionSeconds() {
        return sessionSeconds;
    }

    public DeployStage deployStage() {
        return deployStage;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
