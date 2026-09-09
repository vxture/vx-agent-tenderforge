// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.oidc.Pkce;
import com.td.czghagent.domain.oidc.ReturnTo;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * C1 登录回路：授权跳转、回调换票、静默续期、登出。
 *
 * <p>整条流程的不变量：<strong>浏览器从头到尾只见到一个不透明串</strong>。
 * state / nonce / PKCE verifier 全部服务端持有，access / refresh / id token
 * 全部落在 {@code rp_session}。
 */
@Service
public class OidcLoginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcLoginService.class);

    /**
     * 授权请求的存活时间。
     *
     * <p>正常路径是秒级（跳转到 IdP、认证、跳回来）。给到 10 分钟是为了容纳
     * 用户在登录页停留输密码、过 MFA；再长就只是给重放留窗口。
     */
    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);

    private final OidcGateway gateway;
    private final RpSessionRepository sessions;
    private final AuditRepository auditRepository;

    public OidcLoginService(OidcGateway gateway, RpSessionRepository sessions,
                            AuditRepository auditRepository) {
        this.gateway = gateway;
        this.sessions = sessions;
        this.auditRepository = auditRepository;
    }

    /**
     * 发起授权：铸 state / nonce / PKCE，存服务端，返回跳转地址。
     *
     * <p><b>Preconditions:</b> OIDC 已配置完整（未配置由调用方先行拒绝）。
     * <b>Side Effects:</b> 写一行一次性授权请求。
     * <b>Error Semantics:</b> 发现文档不可达抛 {@code AUTH_ISSUER_UNREACHABLE}（可重试）。
     */
    @Transactional
    public String beginAuthorization(String requestedReturnTo) {
        String state = Pkce.createOpaqueValue();
        String nonce = Pkce.createOpaqueValue();
        String verifier = Pkce.createVerifier();
        LocalDateTime now = LocalDateTime.now();

        sessions.saveAuthorizationRequest(new RpSessionRepository.AuthorizationRequest(
                state, nonce, verifier, ReturnTo.sanitize(requestedReturnTo),
                now.plus(AUTHORIZATION_TTL)));
        // 顺手清理过期行。单独起定时任务只为了删几行短命记录，
        // 不如挂在这条本来就会被频繁触发的路径上。
        sessions.deleteExpiredAuthorizationRequests(now);

        return gateway.authorizationUrl(state, nonce, Pkce.challengeOf(verifier));
    }

    /**
     * 回调：校验 state、换票、验 id_token、建会话。
     *
     * <p><b>Side Effects:</b> 消费掉授权请求、写会话、写一条登录审计。
     * <b>Error Semantics:</b> state 无效/过期/已用过一律
     * {@code AUTH_OIDC_STATE_INVALID}，三者不可区分。
     */
    @Transactional
    public CallbackResult completeAuthorization(String state, String code,
                                                String traceId, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        RpSessionRepository.AuthorizationRequest request = sessions
                .consumeAuthorizationRequest(state, now)
                .orElseThrow(() -> new BusinessException(
                        "AUTH_OIDC_STATE_INVALID", "登录请求已失效，请重新登录", 401, false, null));

        OidcGateway.Tokens tokens = gateway.exchangeCode(code, request.codeVerifier());
        PlatformClaims claims = gateway.readClaims(tokens, request.nonce());

        String cookieValue = SessionToken.generate();
        RpSession session = new RpSession(
                UUID.randomUUID().toString(),
                claims.subject(), claims.displayName(), claims.email(), claims.picture(),
                claims.tenant(), String.join(",", claims.roles()),
                tokens.accessToken(), tokens.refreshToken(),
                now.plusSeconds(tokens.expiresInSeconds()),
                now.plusSeconds(gateway.sessionSeconds())
        );
        sessions.insertSession(session, SessionToken.hash(cookieValue));

        CurrentUser user = session.toCurrentUser();
        auditRepository.append(AuditEvent.byUser(
                new OperationContext(user, traceId, ipAddress),
                "AUTH_LOGIN", "USER", claims.subject(), AuditEvent.SUCCESS, "平台身份登录成功"));

        return new CallbackResult(cookieValue, session, request.returnTo());
    }

    /**
     * 静默续期。
     *
     * <p>提前 60 秒换票，避免一次调用在票有效时开始、在票过期后才到达被调方。
     * <strong>{@code invalid_grant} 即会话死亡，不重试</strong>——刷新令牌已被撤销
     * 或已轮换过一次，再试只会得到同一个答案。
     *
     * <p>返回的可能是同一个会话（没到续期时点）或续期后的会话；
     * 续期失败时抛错，由调用方清 cookie。
     */
    @Transactional
    public RpSession refreshIfNeeded(RpSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (!session.needsRefresh(now)) {
            return session;
        }
        if (session.refreshToken() == null || session.refreshToken().isBlank()) {
            throw new BusinessException(
                    "AUTH_SESSION_EXPIRED", "登录状态已失效，请重新登录", 401, false, null);
        }
        OidcGateway.Tokens refreshed = gateway.refresh(session.refreshToken());
        LocalDateTime accessExpiresAt = now.plusSeconds(refreshed.expiresInSeconds());
        // 轮换存新弃旧。IdP 未返回新刷新令牌时沿用旧的——
        // 那是「刷新令牌不轮换」的合法配置，不是缺失。
        String nextRefresh = refreshed.refreshToken() == null
                ? session.refreshToken() : refreshed.refreshToken();
        sessions.updateTokens(session.id(), refreshed.accessToken(), nextRefresh, accessExpiresAt);
        LOGGER.debug("RP session {} refreshed", session.id());
        return new RpSession(
                session.id(), session.subject(), session.displayName(), session.email(),
                session.picture(), session.tenant(), session.rolesCsv(),
                refreshed.accessToken(), nextRefresh, accessExpiresAt, session.expiresAt());
    }

    @Transactional
    public void logout(String cookieValue, CurrentUser user, String traceId, String ipAddress) {
        if (cookieValue != null) {
            sessions.deleteByTokenHash(SessionToken.hash(cookieValue));
        }
        if (user != null) {
            auditRepository.append(AuditEvent.byUser(
                    new OperationContext(user, traceId, ipAddress),
                    "AUTH_LOGOUT", "USER", user.id(), AuditEvent.SUCCESS, "平台身份退出登录"));
        }
    }

    /**
     * 反向登出：撤销该 IdP 用户在本产品的<strong>全部</strong>会话。
     *
     * <p>平台通知的是「这个人登出了」，而他可能在这里开着几个标签页。
     * 只清发起的那一个，等于没有登出。
     */
    @Transactional
    public void backChannelLogout(String subject) {
        sessions.deleteBySubject(subject);
        auditRepository.append(AuditEvent.bySystem(
                subject, null, "AUTH_BACKCHANNEL_LOGOUT", "USER", subject,
                AuditEvent.SUCCESS, "平台反向登出，已撤销该用户全部会话", null));
    }

    public record CallbackResult(String cookieValue, RpSession session, String returnTo) {
    }
}
