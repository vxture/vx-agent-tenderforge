// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.repository;

import com.td.czghagent.domain.model.RpSession;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * RP 会话与授权请求的持久化。
 *
 * <p>两者放在同一个端口里，因为它们是同一条流程的两半：授权请求在跳转前写入、
 * 回调时读出并<strong>立即消费</strong>，会话在那之后建立。拆成两个端口只会让
 * 「回调必须一次性消费掉暂存」这条纪律散落在两个实现里。
 */
public interface RpSessionRepository {

    // ── 授权请求（一次性） ──────────────────────────────────────────────

    void saveAuthorizationRequest(AuthorizationRequest request);

    /**
     * 取出并<strong>删除</strong>一条授权请求。
     *
     * <p>一次性消费是硬要求，不是优化：留着的 state 可以被重放，
     * 而重放一次成功的授权码交换意味着攻击者能拿到一个属于别人的会话。
     * 返回空表示「不存在、已被用过、或已过期」——三者<strong>刻意不区分</strong>，
     * 区分它们只对攻击者有用。
     */
    Optional<AuthorizationRequest> consumeAuthorizationRequest(String state, LocalDateTime now);

    void deleteExpiredAuthorizationRequests(LocalDateTime now);

    // ── 会话 ────────────────────────────────────────────────────────────

    void insertSession(RpSession session, String tokenHash);

    Optional<RpSession> findByTokenHash(String tokenHash, LocalDateTime now);

    /** 静默续期后回写新票；轮换是存新弃旧，不保留上一张。 */
    void updateTokens(String sessionId, String accessToken, String refreshToken,
                      LocalDateTime accessExpiresAt);

    void touch(String tokenHash, LocalDateTime seenAt);

    void deleteByTokenHash(String tokenHash);

    /**
     * 撤销某个 IdP 用户的<strong>全部</strong>会话。
     *
     * <p>反向登出（back-channel logout）用它。按 subject 而不是按会话：
     * 平台通知的是「这个人登出了」，而他可能在这个产品里开着几个标签页。
     */
    void deleteBySubject(String subject);

    void deleteExpiredSessions(LocalDateTime now);

    /**
     * 授权请求的服务端暂存。
     *
     * <p>{@code codeVerifier} 在这里，不在浏览器。它一旦可被读取，
     * PKCE 就退化成了一个多余的往返。
     */
    record AuthorizationRequest(
            String state,
            String nonce,
            String codeVerifier,
            String returnTo,
            LocalDateTime expiresAt
    ) {
    }
}
