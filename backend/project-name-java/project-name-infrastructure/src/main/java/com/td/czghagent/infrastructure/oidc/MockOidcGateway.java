// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.port.OidcGateway;

import java.util.List;
import java.util.UUID;

/**
 * 本地开发用的身份服务替身。
 *
 * <p>存在的理由只有一个：<strong>整个界面要能在空 .env 下被探索</strong>——
 * 这是本地开发与 CI 的刚需。它不是「没配好时的兜底」，
 * 部署态由 {@link OidcGatewayConfiguration} 直接拒绝它启动。
 *
 * <p>它编造的一切都<strong>可辨认</strong>：subject 带 {@code mock-} 前缀、
 * 工作空间带 {@code local:} 前缀。任何一份跑出来的数据只要带着这些前缀，
 * 就说明它来自替身而不是真实身份——这比让 mock 尽量装得像真的要有用得多。
 */
public class MockOidcGateway implements OidcGateway {

    /** 与真实平台的 UUID 形状刻意不同，一眼可辨。 */
    private static final String SUBJECT_PREFIX = "mock-";

    private final OidcProperties properties;

    public MockOidcGateway(OidcProperties properties) {
        this.properties = properties;
    }

    /**
     * 直接跳回自己的回调地址。
     *
     * <p>不起一个假的登录页：那会让本地流程与真实流程在「有没有一次跨域跳转」
     * 这件事上产生差异，而 cookie 的 SameSite 行为恰好只在跨域跳转里才暴露问题。
     * 这里保留一次真实的浏览器跳转，只是目的地是自己。
     */
    @Override
    public String authorizationUrl(String state, String nonce, String codeChallenge) {
        String base = properties.redirectUri().isBlank()
                ? "/api/auth/oidc/callback" : properties.redirectUri();
        return base + "?state=" + state + "&code=mock-code";
    }

    @Override
    public Tokens exchangeCode(String code, String codeVerifier) {
        return new Tokens("mock-access-token", "mock-refresh-token", "mock-id-token", 3600);
    }

    @Override
    public Tokens refresh(String refreshToken) {
        return new Tokens("mock-access-token", refreshToken, "mock-id-token", 3600);
    }

    /**
     * 编造一份声明。
     *
     * <p>{@code expectedNonce} 被忽略——替身没有可验的签名。这一点也是它
     * 必须被挡在部署态之外的理由之一：nonce 校验在这条路径上根本不存在。
     */
    @Override
    public PlatformClaims readClaims(Tokens tokens, String expectedNonce) {
        String subject = SUBJECT_PREFIX + UUID.randomUUID();
        String workspace = "local:" + subject;
        return new PlatformClaims(
                subject, "本地开发用户", "dev@example.invalid", null,
                workspace, workspace, List.of("workspace:owner"));
    }

    /**
     * 替身<strong>永远拒绝</strong>登出令牌。
     *
     * <p>它没有可验的签名，所以「收下并撤销」等于本地开发环境上任何人
     * 都能登出任何人。返回一个假的 subject 更糟——那会撤销真实存在的会话。
     */
    @Override
    public String subjectOfLogoutToken(String logoutToken) {
        throw new BusinessException(
                "AUTH_LOGOUT_TOKEN_INVALID", "替身身份服务不接受登出通知", 401, false, null);
    }

    @Override
    public long sessionSeconds() {
        return properties.sessionSeconds();
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
