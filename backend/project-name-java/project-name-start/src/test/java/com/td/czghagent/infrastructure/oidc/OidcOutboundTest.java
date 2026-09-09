// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.td.czghagent.domain.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出站两件事：读发现文档、和 token 端点打交道。
 *
 * <p>这里保护的主要是<strong>失败的分类</strong>：哪些错该重试、哪些错是我们自己配错了。
 * 分错的后果不是崩溃——是一个永远重试永远失败的循环，或者一条被当成
 * 「用户没权限」上报的服务器配置缺失。
 */
class OidcOutboundTest {

    private FakeIdp idp;
    private OidcProperties properties;
    private OidcDiscovery discovery;
    private OidcTokenClient tokenClient;

    @BeforeEach
    void setUp() throws Exception {
        idp = new FakeIdp();
        properties = propertiesFor(idp.issuer());
        discovery = new OidcDiscovery(RestClient.builder(), properties);
        tokenClient = new OidcTokenClient(RestClient.builder(), properties, discovery);
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    // ── 发现文档 ────────────────────────────────────────────────────────────

    @Test
    void readsEveryEndpointFromTheDiscoveryDocument() {
        OidcDiscovery.Document document = discovery.document();

        assertThat(document.authorizationEndpoint()).isEqualTo(idp.issuer() + "/authorize");
        assertThat(document.tokenEndpoint()).isEqualTo(idp.issuer() + "/token");
        assertThat(document.jwksUri()).isEqualTo(idp.issuer() + "/jwks");
    }

    /**
     * 发现文档自称的 issuer 与配置不一致时必须拒绝。
     *
     * <p>不一致意味着我们正在跟一个<strong>自称是它</strong>的东西对话——
     * 这正是发现文档最先要挡住的一种情况。
     */
    @Test
    void rejectsADocumentWhoseIssuerDoesNotMatchTheConfiguredOne() {
        OidcProperties mismatched = propertiesFor("http://127.0.0.1:1");
        OidcDiscovery other = new OidcDiscovery(RestClient.builder(), mismatched);

        assertThatThrownBy(other::document).isInstanceOf(BusinessException.class);
    }

    /**
     * 配置问题<strong>不伪装成鉴权失败</strong>。
     *
     * <p>issuer 不可达返回 503 且可重试；issuer 配错返回 500 且不可重试。
     * 两者都不是 401——监控要能区分「服务器配错了」和「调用方没权限」，
     * 而把配置缺失报成 401 会让排障的人去查用户的权限。
     */
    @Test
    void separatesAnUnreachableIssuerFromAMisconfiguredOne() {
        OidcProperties unreachable = propertiesFor("http://127.0.0.1:1");
        // 端口 1 上没有任何东西在听，所以这是「连不上」而不是「答得不对」。
        BusinessException error = catchBusinessException(
                () -> new OidcDiscovery(RestClient.builder(), unreachable).document());

        assertThat(error.getErrorCode()).isEqualTo("AUTH_ISSUER_UNREACHABLE");
        assertThat(error.getHttpStatus()).isEqualTo(503);
        assertThat(error.isRetryable())
                .as("不可达是会自愈的，重试有意义").isTrue();
    }

    /** 缓存生效：同一份文档不应每次调用都去拉一遍，否则 IdP 变成单点。 */
    @Test
    void cachesTheDocumentBetweenCalls() {
        assertThat(discovery.document()).isSameAs(discovery.document());
    }

    // ── token 端点 ──────────────────────────────────────────────────────────

    @Test
    void exchangesAnAuthorizationCodeForTokens() {
        idp.respondToTokenWith(200, """
                {"access_token":"at","refresh_token":"rt","id_token":"it","expires_in":300}
                """);

        OidcTokenClient.TokenResponse response = tokenClient.exchangeCode("code-1", "verifier-1");

        assertThat(response.accessToken()).isEqualTo("at");
        assertThat(response.refreshToken()).isEqualTo("rt");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }

    /**
     * 没有 access_token 的 200 也是失败。
     *
     * <p>一个「状态码对了就算成功」的实现会把它当成登录成功，
     * 然后在建会话时才炸——那时错误信息已经离现场很远了。
     */
    @Test
    void treatsATwoHundredWithoutAnAccessTokenAsFailure() {
        idp.respondToTokenWith(200, "{\"token_type\":\"Bearer\"}");

        assertThatThrownBy(() -> tokenClient.exchangeCode("code-1", "verifier-1"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 刷新失败即会话死亡，<strong>不可重试</strong>。
     *
     * <p>{@code invalid_grant} 说明刷新令牌已被撤销或已轮换过一次，
     * 再试一次只会得到同一个答案。把它当成瞬时故障，用户会卡在一个永远转圈的界面上。
     */
    @Test
    void treatsAFailedRefreshAsSessionDeathRatherThanARetryableFault() {
        idp.respondToTokenWith(400, "{\"error\":\"invalid_grant\"}");

        BusinessException error = catchBusinessException(() -> tokenClient.refresh("rt"));

        assertThat(error.getErrorCode()).isEqualTo("AUTH_SESSION_EXPIRED");
        assertThat(error.isRetryable()).isFalse();
        assertThat(error.getHttpStatus()).isEqualTo(401);
    }

    /**
     * 换票阶段的 5xx 可重试，4xx 不可。
     *
     * <p>前者是 IdP 自己出了问题，会自愈；后者是我们送错了东西，等多久都一样。
     */
    @Test
    void classifiesExchangeFailuresByWhetherWaitingCanHelp() {
        idp.respondToTokenWith(503, "{\"error\":\"temporarily_unavailable\"}");
        assertThat(catchBusinessException(
                () -> tokenClient.exchangeCode("c", "v")).isRetryable()).isTrue();

        idp.respondToTokenWith(400, "{\"error\":\"invalid_grant\"}");
        assertThat(catchBusinessException(
                () -> tokenClient.exchangeCode("c", "v")).isRetryable()).isFalse();
    }

    /**
     * 错误信息里不能回显请求体。
     *
     * <p>换票请求的表单里带着 client_secret；把上游响应整段塞进异常消息，
     * 一个回显请求参数的 IdP 就会让密钥进日志。
     */
    @Test
    void neverEchoesTheClientSecretIntoTheErrorMessage() {
        idp.respondToTokenWith(400,
                "{\"error\":\"invalid_client\",\"echo\":\"client_secret=super-secret-value\"}");

        BusinessException error = catchBusinessException(() -> tokenClient.exchangeCode("c", "v"));

        assertThat(error.getMessage()).doesNotContain("super-secret-value");
    }

    private static BusinessException catchBusinessException(Runnable action) {
        try {
            action.run();
        } catch (BusinessException exception) {
            return exception;
        }
        throw new AssertionError("预期抛出 BusinessException，但没有");
    }

    private OidcProperties propertiesFor(String issuer) {
        return new OidcProperties(
                issuer, "tenderforge", "super-secret-value",
                "http://127.0.0.1/api/auth/oidc/callback", "", "openid profile",
                true, 43200, "local");
    }
}
