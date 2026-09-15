// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.PlatformClaims;

/**
 * 与身份服务交互的出站端口。
 *
 * <p>做成端口是为了让<strong>阶段守卫</strong>有地方生效：本地开发要能在空 .env 下
 * 跑完整界面，而部署态让 mock 顶替真实身份是事故。两个实现，由部署阶段裁决选哪个，
 * 而不是由某个「记得改回来」的配置项。
 */
public interface OidcGateway {

    /** 构造授权跳转地址。 */
    String authorizationUrl(String state, String nonce, String codeChallenge);

    /** 授权码 + PKCE verifier 换票。 */
    Tokens exchangeCode(String code, String codeVerifier);

    /** 刷新续期。 */
    Tokens refresh(String refreshToken);

    /**
     * 验证并读出平台声明。
     *
     * <p>{@code expectedNonce} 是必需参数而不是可选：一个可以跳过的 nonce 检查
     * 等于没有 nonce 检查。
     */
    PlatformClaims readClaims(Tokens tokens, String expectedNonce);

    /**
     * 验证反向登出令牌并取出要登出的 subject。
     *
     * <p>放在端口上而不是让 Web 层直接依赖验签实现：Web 不依赖 infrastructure，
     * 而这条纪律不该为了一个方法破例。更重要的是——放在这里，
     * <b>mock 实现也必须表态</b>，而它的正确表态是「拒绝」：替身没有可验的签名，
     * 一个「收到就撤销」的替身意味着本地开发环境上任何人都能登出任何人。
     */
    String subjectOfLogoutToken(String logoutToken);

    /**
     * 平台登出端点的地址（RP-Initiated Logout 1.0），带 {@code client_id} 与
     * {@code post_logout_redirect_uri}；无从构造时返回 {@code null}。
     *
     * <p>只删本地会话不算登出：账户中心的会话还在，再点登录会被静默 SSO 直接送回来，
     * 换不了账号，登记的登出回跳地址也永远用不上。平台会话 cookie 是 SameSite=Lax，
     * 只有<strong>顶层导航</strong>带得上，所以这里只给地址，由浏览器自己跳过去。
     *
     * <p>不带 {@code id_token_hint}：平台按 {@code client_id} 登记的回跳白名单校验，
     * 会话已过期时也认得出发起方——为一个可选参数把 id_token 存进会话表，不值。
     */
    String endSessionUrl();

    /** 会话时长。放在端口上是因为 mock 与平台可以给出不同的值。 */
    long sessionSeconds();

    /** 本实现是否是 mock。{@code /api/status} 用它如实自报降级。 */
    boolean isMock();

    record Tokens(String accessToken, String refreshToken, String idToken, long expiresInSeconds) {
    }
}
