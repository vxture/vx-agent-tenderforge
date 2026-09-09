// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.port.OidcGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 两种票、两种读法。
 *
 * <p>身份声明取自<strong>已验签的 id_token</strong>；租户轴与治理角色取自
 * access token，而 access token 在这里<strong>不验签</strong>——它刚由 IdP 经
 * 已认证的 TLS 通道发来，读它只是为了决定本地渲染与查询范围，不是做授权判定。
 *
 * <p>这条界线必须被测试固定住：把它实现反了（信 id_token 里的租户、或者去验
 * access token 的签名）都不会立刻崩，只会在某一天以「用户看到了别的工作空间」
 * 或「登录莫名失败」的形式出现。
 */
class PlatformOidcGatewayTest {

    private static final String NONCE = "nonce-1";

    private FakeIdp idp;
    private OidcGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        idp = new FakeIdp();
        OidcProperties properties = new OidcProperties(
                idp.issuer(), "tenderforge", "secret",
                "http://127.0.0.1/api/auth/oidc/callback", "", "openid profile",
                true, 43200, "local");
        OidcDiscovery discovery = new OidcDiscovery(RestClient.builder(), properties);
        IdTokenVerifier idTokenVerifier = new IdTokenVerifier(properties, discovery);
        gateway = new PlatformOidcGateway(properties, discovery,
                new OidcTokenClient(RestClient.builder(), properties, discovery),
                idTokenVerifier, new LogoutTokenVerifier(idTokenVerifier));
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    // ── 授权地址 ────────────────────────────────────────────────────────────

    @Test
    void buildsAnAuthorizationUrlCarryingPkceAndTheOneTimeValues() {
        String url = gateway.authorizationUrl("state-1", "nonce-1", "challenge-1");

        assertThat(url).startsWith(idp.issuer() + "/authorize?");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("code_challenge=challenge-1");
        assertThat(url).contains("code_challenge_method=S256");
        assertThat(url).contains("client_id=tenderforge");
    }

    /** redirect_uri 里带 {@code :} 与 {@code /}，不编码会让 IdP 拿到一个截断的地址。 */
    @Test
    void urlEncodesEveryParameterItPutsIntoTheAuthorizationUrl() {
        String url = gateway.authorizationUrl("st/ate", "no nce", "cha+llenge");

        assertThat(url).contains("redirect_uri=http%3A%2F%2F127.0.0.1%2Fapi%2Fauth%2Foidc%2Fcallback");
        assertThat(url).contains("state=st%2Fate");
        assertThat(url).contains("nonce=no+nce").doesNotContain("nonce=no nce");
    }

    // ── 声明的来源 ──────────────────────────────────────────────────────────

    @Test
    void takesIdentityFromTheIdTokenAndTenancyFromTheAccessToken() throws Exception {
        OidcGateway.Tokens tokens = tokensWith(
                idClaims().claim("name", "张三").claim("email", "z@example.com").build(),
                accessClaims()
                        .claim("active_org", "org-1")
                        .claim("active_workspace", "ws-1")
                        .claim("roles", List.of("workspace:owner", "org:member"))
                        .build());

        PlatformClaims claims = gateway.readClaims(tokens, NONCE);

        assertThat(claims.subject()).isEqualTo("sub-1");
        assertThat(claims.displayName()).isEqualTo("张三");
        assertThat(claims.email()).isEqualTo("z@example.com");
        assertThat(claims.orgId()).isEqualTo("org-1");
        assertThat(claims.workspaceId()).isEqualTo("ws-1");
        assertThat(claims.roles()).containsExactly("workspace:owner", "org:member");
    }

    /**
     * 租户轴<strong>只</strong>认 access token。
     *
     * <p>id_token 里若也带着 active_workspace，必须被忽略：两张票的签发时机不同，
     * 而工作空间是可以在会话期间切换的。信错了那一张，用户切换空间后仍会看到旧空间的数据。
     */
    @Test
    void ignoresTenancyClaimsThatAppearInTheIdToken() throws Exception {
        OidcGateway.Tokens tokens = tokensWith(
                idClaims()
                        .claim("active_org", "org-from-id-token")
                        .claim("active_workspace", "ws-from-id-token")
                        .build(),
                accessClaims()
                        .claim("active_org", "org-1")
                        .claim("active_workspace", "ws-1")
                        .build());

        assertThat(gateway.readClaims(tokens, NONCE).workspaceId()).isEqualTo("ws-1");
    }

    /** 展示名的取值顺序：name > nickname > preferred_username。 */
    @Test
    void fallsBackThroughTheProfileClaimsInOrder() throws Exception {
        assertThat(displayNameFrom(idClaims()
                .claim("nickname", "阿三").claim("preferred_username", "zhangsan").build()))
                .isEqualTo("阿三");
        assertThat(displayNameFrom(idClaims()
                .claim("preferred_username", "zhangsan").build()))
                .isEqualTo("zhangsan");
    }

    /**
     * 三个展示名声明都缺时返回 null，而不是拿 sub 顶上。
     *
     * <p>把一个 uuid 显示成人名，比显示「未命名」更让人困惑。
     */
    @Test
    void leavesTheDisplayNameEmptyRatherThanShowingTheSubject() throws Exception {
        assertThat(displayNameFrom(idClaims().build())).isNull();
    }

    /**
     * access token 可以是不透明串（根本不是 JWT）。
     *
     * <p>那不是错误——OIDC 不要求 access token 是 JWT。此时租户轴为空，
     * 调用方按「尚未开通工作空间」处理，而不是整条登录失败。
     */
    @Test
    void toleratesAnOpaqueAccessTokenByYieldingNoTenant() throws Exception {
        OidcGateway.Tokens tokens = new OidcGateway.Tokens(
                "not-a-jwt-at-all", "refresh", idp.sign(idClaims().build()), 3600);

        PlatformClaims claims = gateway.readClaims(tokens, NONCE);

        assertThat(claims.subject()).isEqualTo("sub-1");
        assertThat(claims.tenant()).isNull();
        assertThat(claims.roles()).isEmpty();
    }

    /** roles 不是数组时当作没有角色，而不是抛错让登录整条失败。 */
    @Test
    void treatsAMalformedRolesClaimAsNoRoles() throws Exception {
        OidcGateway.Tokens tokens = tokensWith(
                idClaims().build(),
                accessClaims().claim("roles", "workspace:owner").build());

        assertThat(gateway.readClaims(tokens, NONCE).roles()).isEmpty();
    }

    /** id_token 仍然要验签——这条路径上唯一不能放松的地方。 */
    @Test
    void stillRejectsAnUnverifiableIdToken() throws Exception {
        OidcGateway.Tokens tokens = new OidcGateway.Tokens(
                idp.signAccessToken(accessClaims().build()), "refresh",
                idp.signWithUnknownKey(idClaims().build()), 3600);

        assertThatThrownBy(() -> gateway.readClaims(tokens, NONCE))
                .hasMessageContaining("身份令牌无效");
    }

    private String displayNameFrom(JWTClaimsSet idTokenClaims) throws Exception {
        return gateway.readClaims(
                tokensWith(idTokenClaims, accessClaims().build()), NONCE).displayName();
    }

    private OidcGateway.Tokens tokensWith(JWTClaimsSet idTokenClaims,
                                          JWTClaimsSet accessTokenClaims) throws Exception {
        return new OidcGateway.Tokens(
                idp.signAccessToken(accessTokenClaims), "refresh",
                idp.sign(idTokenClaims), 3600);
    }

    private JWTClaimsSet.Builder idClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(idp.issuer())
                .audience("tenderforge")
                .subject("sub-1")
                .claim("nonce", NONCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private JWTClaimsSet.Builder accessClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(idp.issuer())
                .subject("sub-1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }
}
