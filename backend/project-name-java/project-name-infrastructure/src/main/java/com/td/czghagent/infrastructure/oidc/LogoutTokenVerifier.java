// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jwt.JWTClaimsSet;
import com.td.czghagent.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 反向登出令牌（OIDC Back-Channel Logout 1.0 §2.4）的校验。
 *
 * <p>logout_token 与 id_token 长得像，<strong>校验规则却不同</strong>，
 * 而这正是这份规范最容易被抄错的地方。抄错的后果不是崩溃：
 * 一个把 id_token 当 logout_token 收的实现，会让<strong>任何人拿一张自己的登录票
 * 就能登出别人</strong>。
 *
 * <p>三条与 id_token 相反的规则：
 * <ol>
 *   <li><b>必须有 {@code events} 声明</b>，且含
 *       {@code http://schemas.openid.net/event/backchannel-logout} 键。
 *       这是区分「这是一张登出通知」与「这是一张登录票」的唯一标志。</li>
 *   <li><b>必须<u>没有</u> {@code nonce}</b>。规范明文禁止——nonce 是把票绑到
 *       一次授权请求上的，而登出通知不属于任何授权请求。带了 nonce 的票
 *       就是一张 id_token，收下它就是上面那个漏洞。</li>
 *   <li><b>必须有 {@code sub} 或 {@code sid} 之一</b>。两个都没有的话，
 *       这张票没说要登出谁。</li>
 * </ol>
 *
 * <p>签名、{@code iss}、{@code aud}、{@code exp} 与 id_token 同规则，
 * 所以复用 {@link IdTokenVerifier} 建好的处理链，而不是另起一套——
 * 另起一套意味着两处算法白名单，早晚会有一处被漏掉。
 */
@Component
public class LogoutTokenVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogoutTokenVerifier.class);

    private static final String BACKCHANNEL_LOGOUT_EVENT =
            "http://schemas.openid.net/event/backchannel-logout";

    private final IdTokenVerifier idTokenVerifier;

    public LogoutTokenVerifier(IdTokenVerifier idTokenVerifier) {
        this.idTokenVerifier = idTokenVerifier;
    }

    /**
     * 验证并取出要登出的 subject。
     *
     * <p>失败一律同一个码：区分「签名错」「不是登出票」「带了 nonce」
     * 只对攻击者有用，排障需要的细节在服务端日志里。
     */
    public String verifyAndExtractSubject(String logoutToken) {
        JWTClaimsSet claims = idTokenVerifier.verifySignatureAndStandardClaims(logoutToken);

        if (claims.getClaim("nonce") != null) {
            throw rejected("logout_token 不得携带 nonce（那是一张 id_token）");
        }
        if (!hasBackchannelLogoutEvent(claims)) {
            throw rejected("缺少 backchannel-logout 事件声明");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            // sid 单独存在时只能登出某一个会话，而本产品按 subject 撤销全部会话。
            // 支持 sid 需要额外记录会话与 sid 的对应关系，那是另一件事。
            throw rejected("缺少 sub，无法确定要登出谁");
        }
        return subject;
    }

    private boolean hasBackchannelLogoutEvent(JWTClaimsSet claims) {
        Object events = claims.getClaim("events");
        return events instanceof Map<?, ?> map && map.containsKey(BACKCHANNEL_LOGOUT_EVENT);
    }

    private BusinessException rejected(String reason) {
        LOGGER.warn("logout_token rejected: {}", reason);
        return new BusinessException(
                "AUTH_LOGOUT_TOKEN_INVALID", "登出通知无效", 401, false, null);
    }
}
