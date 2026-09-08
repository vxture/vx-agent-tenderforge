// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.td.czghagent.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * id_token 验签与声明校验。
 *
 * <p>每一条检查都对应一种<strong>已经发生过</strong>的攻击或事故，不是形式主义：
 *
 * <ol>
 *   <li><b>算法白名单只有 RS256</b>。{@code alg: none} 是签名可以被完全绕过；
 *       {@code HS*} 是拿公钥当 HMAC 密钥伪造。二者都必须显式拒绝，
 *       而不是「只要能验过就行」——能验过正是攻击的目标。</li>
 *   <li><b>按 kid 从 JWKS 取键，缓存，未命中刷新一次</b>。不缓存会把 IdP 打成单点；
 *       永不刷新会让轮换后全线失败。</li>
 *   <li><b>iss 精确匹配</b>。接受别的签发方等于接受任何人。</li>
 *   <li><b>aud 必须等于自己的 client_id</b>。别人的票能在你这里用，
 *       是平台侧真实修复过的漏洞。</li>
 *   <li><b>exp 未过</b>，容许 60 秒时钟偏移。</li>
 *   <li><b>nonce 必须与本次授权请求一致</b>。缺了它，一张旧 id_token 可以被重放。</li>
 * </ol>
 *
 * <p>用 nimbus 而不是自己拼：签名验证是这套流程里唯一不该手写的部分。
 */
@Component
public class IdTokenVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdTokenVerifier.class);

    /** 只此一种。列表存在的意义是「其余全部拒绝」，不是「优先尝试」。 */
    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.RS256;

    private static final int CLOCK_SKEW_SECONDS = 60;

    /** OIDC Core 对 id_token 的要求。 */
    private static final Set<String> ID_TOKEN_REQUIRED_CLAIMS = Set.of("sub", "iat", "exp");

    /**
     * Back-Channel Logout 1.0 §2.4 对 logout_token 的要求。
     *
     * <p>刻意<strong>不含 exp</strong>：规范里它是可选的。要求它会让「平台没发 exp」
     * 表现为全部登出通知被拒——一个只在真的有人登出时才发作的故障。
     * {@code sub} 也不在这里，由 {@link LogoutTokenVerifier} 单独判断，
     * 因为规范允许用 {@code sid} 代替，而本产品的取舍需要写在那里。
     */
    private static final Set<String> LOGOUT_TOKEN_REQUIRED_CLAIMS = Set.of("iat", "jti");

    private final OidcProperties properties;
    private final OidcDiscovery discovery;
    private final Map<String, ConfigurableJWTProcessor<SecurityContext>> processors =
            new ConcurrentHashMap<>();

    public IdTokenVerifier(OidcProperties properties, OidcDiscovery discovery) {
        this.properties = properties;
        this.discovery = discovery;
    }

    /**
     * 验证 id_token 并返回其声明。
     *
     * <p>{@code expectedNonce} 为空表示<strong>不接受</strong>——不是「跳过检查」。
     * 一个可选的 nonce 检查等于没有 nonce 检查。
     */
    public JWTClaimsSet verify(String idToken, String expectedNonce) {
        Objects.requireNonNull(expectedNonce, "expectedNonce");
        try {
            JWTClaimsSet claims = processor(ID_TOKEN_REQUIRED_CLAIMS).process(idToken, null);
            if (!expectedNonce.equals(claims.getStringClaim("nonce"))) {
                throw rejected("nonce 不匹配");
            }
            return claims;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.warn("id_token verification failed", exception);
            throw rejected("令牌验证失败");
        }
    }

    /**
     * 只验签名与 iss / aud / exp 这些两种票共有的部分。
     *
     * <p>给 {@code logout_token} 用：它与 id_token 的签名规则完全相同，
     * 但必备声明不同（不要求 {@code exp}，要求 {@code jti} 与 {@code events}），
     * 而且<strong>必须没有 nonce</strong>。差异部分由
     * {@link LogoutTokenVerifier} 判断，这里只提供共有的那一半。
     *
     * <p>复用同一条处理链而不是另起一套：另起一套意味着两处算法白名单，
     * 早晚会有一处被漏掉，而漏掉的那一处不会报错。
     */
    public JWTClaimsSet verifySignatureAndStandardClaims(String token) {
        try {
            return processor(LOGOUT_TOKEN_REQUIRED_CLAIMS).process(token, null);
        } catch (Exception exception) {
            LOGGER.warn("logout_token signature verification failed", exception);
            throw rejected("令牌验证失败");
        }
    }

    /**
     * 按必备声明集分别缓存处理链。
     *
     * <p>两种票共用一条链会让其中一种的必备声明被强加给另一种——
     * 要求 logout_token 带 exp，平台不发它时全部登出通知被拒；
     * 放宽 id_token 不要求 exp，一张永不过期的登录票就被接受了。
     */
    private ConfigurableJWTProcessor<SecurityContext> processor(Set<String> requiredClaims) {
        OidcDiscovery.Document document = discovery.document();
        return processors.computeIfAbsent(
                document.jwksUri() + "|" + requiredClaims,
                key -> build(document, requiredClaims));
    }

    private ConfigurableJWTProcessor<SecurityContext> build(
            OidcDiscovery.Document document, Set<String> requiredClaims) {
        try {
            // JWKSourceBuilder 自带缓存与「未命中时刷新一次」的限流重取，
            // 这正是轮换期需要的行为；自己实现这一段容易写成每次未命中都打一次 IdP。
            JWKSource<SecurityContext> keys = JWKSourceBuilder
                    .create(new URL(document.jwksUri()))
                    .retrying(true)
                    .build();

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ALGORITHM, keys));
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    properties.clientId(),
                    new JWTClaimsSet.Builder().issuer(document.issuer()).build(),
                    requiredClaims
            ) {
                {
                    setMaxClockSkew(CLOCK_SKEW_SECONDS);
                }
            });
            return processor;
        } catch (Exception exception) {
            LOGGER.error("Failed to build id_token processor for {}", document.jwksUri(), exception);
            throw new BusinessException(
                    "AUTH_ISSUER_NOT_CONFIGURED", "身份服务密钥不可用", 500, false, null);
        }
    }

    /**
     * 验签失败一律同一个码与同一句话。
     *
     * <p>不区分「签名错」「aud 错」「过期」——区分它们只对攻击者有用，
     * 而排障需要的细节在服务端日志里。
     */
    private BusinessException rejected(String reason) {
        LOGGER.warn("id_token rejected: {}", reason);
        return new BusinessException(
                "AUTH_OIDC_TOKEN_INVALID", "身份令牌无效，请重新登录", 401, false, null);
    }
}
