// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.S2STokenMinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S2S 换票（RFC 8693）。
 *
 * <p>这一组里最要紧的是<strong>缓存键</strong>。把 OBO 的票缓存错了，
 * 一个用户的凭证会被发给另一个用户的请求——那是越权，而它不会报错：
 * 被调方看到的是一张合法的票，只是里面的人不对。
 */
class PlatformS2STokenMinterTest {

    private static final TenantScope TENANT =
            new TenantScope("org-1", "11111111-1111-1111-1111-111111111111");

    private FakeIdp idp;
    private S2STokenMinter minter;

    @BeforeEach
    void setUp() throws Exception {
        idp = new FakeIdp();
        OidcProperties properties = new OidcProperties(
                idp.issuer(), "tenderforge", "super-secret-value",
                "http://127.0.0.1/api/auth/oidc/callback", "", "openid profile",
                true, 43200, "local");
        minter = new PlatformS2STokenMinter(RestClient.builder(), properties,
                new OidcDiscovery(RestClient.builder(), properties));
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    // ── 铸币 ────────────────────────────────────────────────────────────────

    @Test
    void mintsAnOnBehalfOfTokenAndReadsTheServerResolvedTenant() throws Exception {
        respondWith(mintedToken("user-sub", "tenant-uuid"), 300);

        S2SToken token = minter.onBehalfOf("atlas", "user-access-token");

        assertThat(token.audience()).isEqualTo("atlas");
        assertThat(token.mode()).isEqualTo(S2SToken.Mode.ON_BEHALF_OF);
        assertThat(token.tenantId())
                .as("tenant_id 由平台服务端从 workspace 解析后盖在票上，"
                        + "调用方无从伪造，也不必自己维护一份")
                .isEqualTo("tenant-uuid");
    }

    @Test
    void mintsAServiceTokenForABackgroundJob() throws Exception {
        respondWith(mintedToken(null, "tenant-uuid"), 300);

        S2SToken token = minter.forService("atlas", TENANT);

        assertThat(token.mode()).isEqualTo(S2SToken.Mode.SERVICE);
        assertThat(token.subject())
                .as("service 模式的票刻意不带 sub——没有用户在场")
                .isNull();
    }

    // ── 缓存 ────────────────────────────────────────────────────────────────

    @Test
    void reusesAFreshTokenInsteadOfMintingOnEveryCall() throws Exception {
        respondWith(mintedToken("user-sub", "t"), 300);

        S2SToken first = minter.onBehalfOf("atlas", "user-access-token");
        S2SToken second = minter.onBehalfOf("atlas", "user-access-token");

        assertThat(second.value()).isEqualTo(first.value());
    }

    /**
     * <strong>不同用户的票绝不能共用一条缓存。</strong>
     *
     * <p>共用的表现不是报错，是被调方收到一张合法但属于别人的票——
     * 一次安静的越权。
     */
    @Test
    void neverServesOneUsersTokenToAnotherUsersRequest() throws Exception {
        respondWith(mintedToken("alice", "t"), 300);
        S2SToken alice = minter.onBehalfOf("atlas", "alice-access-token");

        respondWith(mintedToken("bob", "t"), 300);
        S2SToken bob = minter.onBehalfOf("atlas", "bob-access-token");

        assertThat(bob.value()).isNotEqualTo(alice.value());
        assertThat(bob.subject()).isEqualTo("bob");
    }

    /** 面向不同被调方的票也不能共用：一张给 atlas 的票在 runos 那里构造性 401。 */
    @Test
    void keepsTokensForDifferentAudiencesApart() throws Exception {
        respondWith(mintedToken("user-sub", "t"), 300);
        S2SToken forAtlas = minter.onBehalfOf("atlas", "same-user-token");

        respondWith(mintedToken("user-sub", "t"), 300);
        S2SToken forRunos = minter.onBehalfOf("runos", "same-user-token");

        assertThat(forRunos.value()).isNotEqualTo(forAtlas.value());
        assertThat(forRunos.audience()).isEqualTo("runos");
    }

    /** 不同工作空间的 service 票同样不共用。 */
    @Test
    void keepsServiceTokensPerWorkspace() throws Exception {
        respondWith(mintedToken(null, "t1"), 300);
        S2SToken first = minter.forService("atlas", TENANT);

        respondWith(mintedToken(null, "t2"), 300);
        S2SToken second = minter.forService("atlas", new TenantScope("org-2", "ws-2"));

        assertThat(second.value()).isNotEqualTo(first.value());
    }

    /**
     * 快过期的票要重铸，不能等真过期。
     *
     * <p>一次调用可能在票有效时开始、在票过期后才到达被调方；
     * 那种失败只在慢请求上偶发，最难复现。
     */
    @Test
    void remintsWhenTheCachedTokenIsInsideTheExpiryMargin() throws Exception {
        respondWith(mintedToken("user-sub", "t"), 10);
        S2SToken first = minter.onBehalfOf("atlas", "user-access-token");

        respondWith(mintedToken("user-sub", "t"), 300);
        S2SToken second = minter.onBehalfOf("atlas", "user-access-token");

        assertThat(second.value()).isNotEqualTo(first.value());
    }

    /** 被调方回 401 之后作废缓存，让下一次重新铸而不是重放同一张。 */
    @Test
    void remintsAfterTheCachedTokenIsInvalidated() throws Exception {
        respondWith(mintedToken("user-sub", "t"), 300);
        S2SToken first = minter.onBehalfOf("atlas", "user-access-token");

        minter.invalidate(first);
        respondWith(mintedToken("user-sub", "t"), 300);
        S2SToken second = minter.onBehalfOf("atlas", "user-access-token");

        assertThat(second.value()).isNotEqualTo(first.value());
    }

    // ── 失败 ────────────────────────────────────────────────────────────────

    /**
     * {@code invalid_target} 是重载的，而对我们它几乎一定是「还没开通」。
     *
     * <p>audience 是写死的常量，所以先查的那一步不会错；拿到这个码说明
     * 平台还没在当前工作空间为本产品开通对该被调方的调用权限——
     * 这正是一个新登记产品最先撞上的失败，值得一句说人话的提示。
     */
    @Test
    void explainsInvalidTargetAsAMissingGrantRatherThanEchoingTheOauthCode() {
        idp.respondToTokenWith(400,
                "{\"message\":\"invalid_target\",\"error\":\"Bad Request\",\"statusCode\":400}");

        BusinessException error = catchBusinessException(
                () -> minter.onBehalfOf("atlas", "user-access-token"));

        assertThat(error.getMessage()).contains("尚未").contains("atlas");
        assertThat(error.isRetryable())
                .as("开通是运营动作，等待改变不了任何事").isFalse();
    }

    /**
     * OAuth 码可能在 {@code message} 而不是 {@code error} 里。
     *
     * <p>平台的换票端点返回的是 NestJS 异常体：{@code error} 装的是 HTTP 原因短语
     * 「Bad Request」，OAuth 码在 {@code message}。只读 {@code error} 的客户端
     * 得到的是一句什么也说明不了的话。
     */
    @Test
    void readsTheOauthCodeFromEitherEnvelopeShape() {
        idp.respondToTokenWith(400, "{\"error\":\"invalid_target\"}");

        assertThat(catchBusinessException(() -> minter.forService("atlas", TENANT)).getMessage())
                .contains("尚未");
    }

    @Test
    void classifiesFailuresByWhetherWaitingCanHelp() {
        idp.respondToTokenWith(503, "{\"error\":\"temporarily_unavailable\"}");
        assertThat(catchBusinessException(
                () -> minter.forService("atlas", TENANT)).isRetryable()).isTrue();

        idp.respondToTokenWith(400, "{\"error\":\"invalid_client\"}");
        assertThat(catchBusinessException(
                () -> minter.forService("atlas", TENANT)).isRetryable()).isFalse();
    }

    /** 换票表单里带着 client_secret；回显请求参数的端点不能把它带进异常。 */
    @Test
    void neverEchoesTheClientSecretIntoTheError() {
        idp.respondToTokenWith(400,
                "{\"error\":\"invalid_client\",\"echo\":\"client_secret=super-secret-value\"}");

        assertThat(catchBusinessException(() -> minter.forService("atlas", TENANT)).getMessage())
                .doesNotContain("super-secret-value");
    }

    @Test
    void treatsAResponseWithoutAnAccessTokenAsFailure() {
        idp.respondToTokenWith(200, "{\"token_type\":\"Bearer\"}");

        assertThatThrownBy(() -> minter.forService("atlas", TENANT))
                .isInstanceOf(BusinessException.class);
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private void respondWith(String token, long expiresIn) {
        idp.respondToTokenWith(200,
                "{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}");
    }

    /** 每次铸出来的票都不同，这样「有没有重新铸」才可断言。 */
    private String mintedToken(String subject, String tenantId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(idp.issuer())
                .audience("atlas")
                .jwtID(java.util.UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
        if (subject != null) {
            builder.subject(subject);
        }
        if (tenantId != null) {
            builder.claim("tenant_id", tenantId);
        }
        return idp.signAccessToken(builder.build());
    }

    private static BusinessException catchBusinessException(Runnable action) {
        try {
            action.run();
        } catch (BusinessException exception) {
            return exception;
        }
        throw new AssertionError("预期抛出 BusinessException，但没有");
    }
}
