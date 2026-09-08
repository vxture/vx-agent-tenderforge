// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import com.td.czghagent.domain.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 反向登出令牌。
 *
 * <p>它与 id_token 长得像，<strong>校验规则却相反</strong>，而这正是这份规范
 * 最容易被抄错的地方。抄错不会崩：一个把 id_token 当 logout_token 收下的实现，
 * 会让<strong>任何人拿自己的登录票就能登出别人</strong>——攻击者只要有一个账号，
 * 就能持续把某个目标踢下线，而服务端日志里看到的是一次「正常的登出通知」。
 */
class LogoutTokenVerifierTest {

    private static final String LOGOUT_EVENT =
            "http://schemas.openid.net/event/backchannel-logout";

    private FakeIdp idp;
    private LogoutTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        idp = new FakeIdp();
        OidcProperties properties = new OidcProperties(
                idp.issuer(), "tenderforge", "secret",
                "http://127.0.0.1/api/auth/oidc/callback", "", "openid profile",
                true, 43200, "local");
        OidcDiscovery discovery = new OidcDiscovery(RestClient.builder(), properties);
        verifier = new LogoutTokenVerifier(new IdTokenVerifier(properties, discovery));
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    @Test
    void acceptsAWellFormedLogoutTokenAndReturnsTheSubject() throws Exception {
        String token = idp.sign(logoutClaims().build());

        assertThat(verifier.verifyAndExtractSubject(token)).isEqualTo("sub-1");
    }

    // ── 与 id_token 相反的三条 ──────────────────────────────────────────────

    /**
     * <strong>这是整组里最重要的一条。</strong>
     *
     * <p>一张普通的 id_token：签名对、iss 对、aud 对、exp 没过——
     * 唯一的差别是它带着 nonce 而没有 events。收下它就等于「任何人都能登出任何人」。
     */
    @Test
    void refusesAnOrdinaryIdTokenPresentedAsALogoutNotice() throws Exception {
        String idToken = idp.sign(new JWTClaimsSet.Builder(logoutClaims().build())
                .claim("events", null)
                .claim("nonce", "some-nonce")
                .build());

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(idToken))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("AUTH_LOGOUT_TOKEN_INVALID");
    }

    /** 规范明文禁止 logout_token 携带 nonce——带了就说明它其实是一张 id_token。 */
    @Test
    void refusesALogoutTokenCarryingANonce() throws Exception {
        String token = idp.sign(logoutClaims().claim("nonce", "n").build());

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
                .isInstanceOf(BusinessException.class);
    }

    /** events 声明是「这是一张登出通知」的唯一标志。 */
    @Test
    void refusesATokenWithoutTheBackchannelLogoutEvent() throws Exception {
        String noEvents = idp.sign(new JWTClaimsSet.Builder(logoutClaims().build())
                .claim("events", null).build());
        String wrongEvent = idp.sign(logoutClaims()
                .claim("events", Map.of("http://example.com/other-event", Map.of())).build());

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(noEvents))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(wrongEvent))
                .isInstanceOf(BusinessException.class);
    }

    // ── 与 id_token 相同的那一半 ────────────────────────────────────────────

    @Test
    void stillRequiresAValidSignatureFromTheIssuer() throws Exception {
        String forged = idp.signWithUnknownKey(logoutClaims().build());

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(forged))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void stillRequiresTheAudienceToBeThisProduct() throws Exception {
        String token = idp.sign(logoutClaims().audience("another-product").build());

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
                .isInstanceOf(BusinessException.class);
    }

    // ── 自有的必备声明 ──────────────────────────────────────────────────────

    /**
     * 没有 exp 的 logout_token <strong>应当被接受</strong>。
     *
     * <p>规范里 exp 对 logout_token 是可选的。要求它会让「平台没发 exp」
     * 表现为全部登出通知被拒——一个只在真的有人登出时才发作的故障。
     * 这条同时是本组的反向对照：没有它，一个「什么都拒绝」的实现也能全绿。
     */
    @Test
    void acceptsALogoutTokenWithoutAnExpiryBecauseTheSpecMakesItOptional() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(logoutClaims().build())
                .expirationTime(null).build();

        assertThat(verifier.verifyAndExtractSubject(idp.sign(claims))).isEqualTo("sub-1");
    }

    /** jti 是规范要求的，用于投递去重。 */
    @Test
    void refusesALogoutTokenWithoutAJti() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(logoutClaims().build())
                .jwtID(null).build();

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(idp.sign(claims)))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 没有 sub 就没说要登出谁。
     *
     * <p>规范允许用 sid 代替，但本产品按 subject 撤销全部会话——
     * 支持 sid 需要额外记录会话与 sid 的对应关系，那是另一件事，
     * 在做之前拒绝比猜一个更安全。
     */
    @Test
    void refusesALogoutTokenThatDoesNotSayWhoToLogOut() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder(logoutClaims().build())
                .subject(null).build();

        assertThatThrownBy(() -> verifier.verifyAndExtractSubject(idp.sign(claims)))
                .isInstanceOf(BusinessException.class);
    }

    private JWTClaimsSet.Builder logoutClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(idp.issuer())
                .audience("tenderforge")
                .subject("sub-1")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(120)))
                .claim("events", Map.of(LOGOUT_EVENT, Map.of()));
    }
}
