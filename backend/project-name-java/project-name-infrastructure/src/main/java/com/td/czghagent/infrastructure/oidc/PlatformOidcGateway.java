// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.oidc.Pkce;
import com.td.czghagent.domain.port.OidcGateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;

/**
 * 真实身份服务实现。
 */
public class PlatformOidcGateway implements OidcGateway {

    private final OidcProperties properties;
    private final OidcDiscovery discovery;
    private final OidcTokenClient tokenClient;
    private final IdTokenVerifier verifier;
    private final LogoutTokenVerifier logoutTokenVerifier;

    public PlatformOidcGateway(OidcProperties properties, OidcDiscovery discovery,
                               OidcTokenClient tokenClient, IdTokenVerifier verifier,
                               LogoutTokenVerifier logoutTokenVerifier) {
        this.properties = properties;
        this.discovery = discovery;
        this.tokenClient = tokenClient;
        this.verifier = verifier;
        this.logoutTokenVerifier = logoutTokenVerifier;
    }

    @Override
    public String authorizationUrl(String state, String nonce, String codeChallenge) {
        return discovery.document().authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + encode(properties.scopes())
                + "&state=" + encode(state)
                + "&nonce=" + encode(nonce)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=" + Pkce.METHOD;
    }

    @Override
    public Tokens exchangeCode(String code, String codeVerifier) {
        return map(tokenClient.exchangeCode(code, codeVerifier));
    }

    @Override
    public Tokens refresh(String refreshToken) {
        return map(tokenClient.refresh(refreshToken));
    }

    /**
     * 读出平台声明。
     *
     * <p>身份声明取自<strong>已验签的 id_token</strong>；租户轴与治理角色取自
     * access token。后者也是 JWT，但这里<strong>不验它的签名</strong>：
     * 它刚由 IdP 经已认证的 TLS 通道发来，而我们读它只是为了决定本地渲染与查询范围，
     * 不是为了做授权判定——真正的授权判定发生在被调方，那里会验签。
     * 这条界线要说清楚，否则下一个人会以为这里漏了一次校验。
     */
    @Override
    public PlatformClaims readClaims(Tokens tokens, String expectedNonce) {
        JWTClaimsSet idClaims = verifier.verify(tokens.idToken(), expectedNonce);
        JWTClaimsSet accessClaims = decodeWithoutVerification(tokens.accessToken());
        return new PlatformClaims(
                idClaims.getSubject(),
                displayNameOf(idClaims),
                stringClaim(idClaims, "email"),
                stringClaim(idClaims, "picture"),
                stringClaim(accessClaims, "active_org"),
                stringClaim(accessClaims, "active_workspace"),
                rolesOf(accessClaims)
        );
    }

    @Override
    public String subjectOfLogoutToken(String logoutToken) {
        return logoutTokenVerifier.verifyAndExtractSubject(logoutToken);
    }

    @Override
    public long sessionSeconds() {
        return properties.sessionSeconds();
    }

    @Override
    public boolean isMock() {
        return false;
    }

    /**
     * 展示名的取值顺序：name > nickname > preferred_username。
     *
     * <p>三者都缺时返回 null 而不是 sub——把一个 uuid 显示成人名，
     * 比显示「未命名」更让人困惑。
     */
    private static String displayNameOf(JWTClaimsSet claims) {
        for (String field : List.of("name", "nickname", "preferred_username")) {
            String value = stringClaim(claims, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> rolesOf(JWTClaimsSet claims) {
        if (claims == null) {
            return List.of();
        }
        Object raw = claims.getClaim("roles");
        if (raw instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        if (claims == null) {
            return null;
        }
        try {
            return claims.getStringClaim(name);
        } catch (ParseException exception) {
            return null;
        }
    }

    /**
     * 不验签地读 access token 的载荷。
     *
     * <p>见 {@link #readClaims} 的说明：这里读到的东西只用于本地渲染与查询范围，
     * 不做授权判定。access token 也可能根本不是 JWT（不透明串），
     * 那时返回 null，租户轴为空，调用方按「尚未开通工作空间」处理。
     */
    private static JWTClaimsSet decodeWithoutVerification(String token) {
        try {
            return com.nimbusds.jwt.JWTParser.parse(token).getJWTClaimsSet();
        } catch (ParseException exception) {
            return null;
        }
    }

    private static Tokens map(OidcTokenClient.TokenResponse response) {
        return new Tokens(response.accessToken(), response.refreshToken(),
                response.idToken(), response.expiresInSeconds());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
