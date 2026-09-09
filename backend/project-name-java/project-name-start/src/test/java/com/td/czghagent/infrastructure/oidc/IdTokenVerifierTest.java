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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * id_token 验签的每一条判据。
 *
 * <p>这些不是形式主义：每一条都对应一种<strong>已经发生过</strong>的伪造手法，
 * 而它们的共同点是——<strong>放行不会报错</strong>。一个漏掉 aud 校验的 RP
 * 会愉快地接受别的产品签发的票，用户看到的一切都正常，直到有人拿别处的票登进来。
 *
 * <p>用真的 HTTP、真的 RSA、真的 nimbus 处理链（见 {@link FakeIdp}），
 * 而不是把 verifier 打桩：这条链上最值得保护的东西全在 nimbus 内部，
 * 打桩之后测的就只剩自己写的那几行胶水。
 */
class IdTokenVerifierTest {

    private static final String NONCE = "nonce-under-test";

    private FakeIdp idp;
    private IdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        idp = new FakeIdp();
        OidcProperties properties = propertiesFor(idp.issuer());
        verifier = new IdTokenVerifier(
                properties, new OidcDiscovery(RestClient.builder(), properties));
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    @Test
    void acceptsATokenThatSatisfiesEveryCheck() throws Exception {
        JWTClaimsSet claims = verifier.verify(idp.sign(validClaims().build()), NONCE);

        assertThat(claims.getSubject()).isEqualTo("sub-1");
    }

    // ── 算法 ────────────────────────────────────────────────────────────────
    //
    // 【这两条的实际效力，实测确认过】
    // 把生产代码的算法白名单从「只认 RS256」放宽到 {RS256, HS256, RS512}，
    // 下面两条<b>依然全绿</b>。原因是 nimbus 的 JWSVerificationKeySelector
    // 只会从 JWKS 里挑键，而 JWKS 里只有 RSA 公钥——HS256 无论是否在白名单里
    // 都选不到密钥，alg:none 更是压根没有可选的键。
    //
    // 也就是说：这两种经典伪造在当前实现下是<b>结构性不可达</b>的，
    // 而不是被白名单挡住的。保留它们是为了钉住这个性质——哪天有人把 nimbus
    // 换成手写解析，它们会立刻变红。但<b>不要</b>把它们当成「白名单在起作用」的证据。

    /**
     * {@code alg: none}：三段结构齐全、签名段为空。
     *
     * <p>验证方若按「头部说什么算法就用什么算法」办事，它一路放行。
     * 这是 JWT 历史上最经典的一种伪造。见上方说明——本条在当前实现下不可证伪。
     */
    @Test
    void rejectsTheNoneAlgorithm() {
        String forged = idp.signWithNoneAlgorithm(validClaims().build());

        assertThatThrownBy(() -> verifier.verify(forged, NONCE))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("AUTH_OIDC_TOKEN_INVALID");
    }

    /**
     * 算法混淆：拿 RSA <strong>公钥</strong>当 HMAC 密钥签一张 HS256。
     *
     * <p>公钥是公开的，所以任何人都能签出这样一张票。只要验证方把
     * 「用配置里那把键去验」实现成「用键的字节去验」，它就成立。
     *
     * <p>见上方说明：本实现挡住它靠的是「密钥只能来自 JWKS」这个结构，
     * 不是算法白名单。两者都成立时它才被挡住，而只有前者是可以被这条用例证伪的。
     */
    @Test
    void rejectsAlgorithmConfusionWithThePublicKeyAsHmacSecret() throws Exception {
        String forged = idp.signWithPublicKeyAsHmacSecret(validClaims().build());

        assertThatThrownBy(() -> verifier.verify(forged, NONCE))
                .isInstanceOf(BusinessException.class);
    }

    /** IdP 从未公布过的键签的票必须被拒——这正是「按 kid 取键」要挡住的。 */
    @Test
    void rejectsATokenSignedByAKeyTheIssuerNeverPublished() throws Exception {
        String forged = idp.signWithUnknownKey(validClaims().build());

        assertThatThrownBy(() -> verifier.verify(forged, NONCE))
                .isInstanceOf(BusinessException.class);
    }

    // ── 声明 ────────────────────────────────────────────────────────────────

    /**
     * 别人的票不能在这里用。
     *
     * <p>这是平台侧真实修复过的一个漏洞：漏掉 aud 校验时，任何一个同 IdP 下的
     * 产品签发的票都能拿来登录本产品。
     *
     * <p><b>已反向验证</b>：把 DefaultJWTClaimsVerifier 的受众参数改成 null，
     * 本条立刻变红。它是承重的。
     */
    @Test
    void rejectsATokenIssuedForAnotherAudience() throws Exception {
        String other = idp.sign(validClaims().audience("some-other-product").build());

        assertThatThrownBy(() -> verifier.verify(other, NONCE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() throws Exception {
        String other = idp.sign(validClaims().issuer("https://evil.example").build());

        assertThatThrownBy(() -> verifier.verify(other, NONCE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsATokenThatExpiredBeyondTheClockSkew() throws Exception {
        String stale = idp.sign(validClaims()
                .expirationTime(Date.from(Instant.now().minusSeconds(600))).build());

        assertThatThrownBy(() -> verifier.verify(stale, NONCE))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 刚过期一点点的票<strong>应当通过</strong>。
     *
     * <p>这一条是整组用例的反向对照：没有它，一个「什么都拒绝」的实现
     * 也能让上面每一条变绿。它同时锁住了 60 秒时钟容差本身——
     * 去掉容差，分布式部署上会出现零星的、无法复现的登录失败。
     */
    @Test
    void acceptsATokenWithinTheSixtySecondClockSkew() throws Exception {
        String slightlyStale = idp.sign(validClaims()
                .expirationTime(Date.from(Instant.now().minusSeconds(30))).build());

        assertThat(verifier.verify(slightlyStale, NONCE).getSubject()).isEqualTo("sub-1");
    }

    // ── nonce ───────────────────────────────────────────────────────────────

    /** nonce 对不上即重放。 */
    @Test
    void rejectsATokenWhoseNonceDoesNotMatchThisAuthorizationRequest() throws Exception {
        String replayed = idp.sign(validClaims().claim("nonce", "some-other-nonce").build());

        assertThatThrownBy(() -> verifier.verify(replayed, NONCE))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 完全没有 nonce 的票也必须被拒。
     *
     * <p>「有就比、没有就跳过」是最容易写出来的实现，而它等于没有 nonce 校验：
     * 攻击者只要不带这个声明就能绕过去。
     *
     * <p><b>已反向验证</b>：把校验改成「actualNonce != null 时才比」，
     * 本条立刻变红。它是承重的。
     */
    @Test
    void rejectsATokenCarryingNoNonceAtAll() throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder(validClaims().build());
        builder.claim("nonce", null);

        assertThatThrownBy(() -> verifier.verify(idp.sign(builder.build()), NONCE))
                .isInstanceOf(BusinessException.class);
    }

    /** 缺 sub 的票没有身份可言。 */
    @Test
    void rejectsATokenWithoutASubject() throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder(validClaims().build());
        builder.subject(null);

        assertThatThrownBy(() -> verifier.verify(idp.sign(builder.build()), NONCE))
                .isInstanceOf(BusinessException.class);
    }

    private OidcProperties propertiesFor(String issuer) {
        return new OidcProperties(
                issuer, "tenderforge", "secret",
                "http://127.0.0.1/api/auth/oidc/callback", "", "openid profile",
                true, 43200, "local");
    }

    private JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(idp.issuer())
                .audience("tenderforge")
                .subject("sub-1")
                .claim("nonce", NONCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }
}
