// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.RpSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * RP 会话与授权请求的 JDBC 实现。
 *
 * <p>会话按 {@code token_hash} 查而不是按 cookie 原值：库里存的是哈希，
 * 被读走时读到的东西无法用来冒充任何人。
 */
@Repository
public class JdbcRpSessionRepository implements RpSessionRepository {

    private static final RowMapper<RpSession> SESSION = (rs, row) -> new RpSession(
            rs.getString("id"),
            rs.getString("subject"),
            rs.getString("display_name"),
            rs.getString("email"),
            rs.getString("picture"),
            tenantOf(rs.getString("org_id"), rs.getString("workspace_id")),
            rs.getString("roles"),
            rs.getString("access_token"),
            rs.getString("refresh_token"),
            JdbcTimes.localDateTime(rs, "access_expires_at"),
            JdbcTimes.localDateTime(rs, "expires_at")
    );

    private static final RowMapper<AuthorizationRequest> AUTHORIZATION_REQUEST =
            (rs, row) -> new AuthorizationRequest(
                    rs.getString("state"),
                    rs.getString("nonce"),
                    rs.getString("code_verifier"),
                    rs.getString("return_to"),
                    JdbcTimes.localDateTime(rs, "expires_at")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcRpSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 租户轴为空是合法状态。
     *
     * <p>用户可能还没有被开通到任何工作空间——那时 token 里就没有
     * {@code active_workspace}。这里返回 null 让调用方显式处理，
     * 而不是兜底到某个默认空间：兜底的表现是「他看到了别人的数据」。
     */
    private static TenantScope tenantOf(String orgId, String workspaceId) {
        return orgId == null || workspaceId == null ? null : new TenantScope(orgId, workspaceId);
    }

    @Override
    public void saveAuthorizationRequest(AuthorizationRequest request) {
        jdbcTemplate.update("""
                INSERT INTO oidc_authorization_request(
                    state, nonce, code_verifier, return_to, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                """, request.state(), request.nonce(), request.codeVerifier(),
                request.returnTo(), request.expiresAt());
    }

    /**
     * 取出并删除。
     *
     * <p>先读后删而不是「删除并返回」，是因为 MySQL 的 DELETE 不回传行；
     * 两条语句放在同一个事务里，效果等价，而且删除的行数还能用来断言
     * 「这条 state 确实只被消费了一次」。
     */
    @Override
    @Transactional
    public Optional<AuthorizationRequest> consumeAuthorizationRequest(
            String state, LocalDateTime now) {
        Optional<AuthorizationRequest> found = jdbcTemplate.query("""
                SELECT * FROM oidc_authorization_request WHERE state = ?
                """, AUTHORIZATION_REQUEST, state).stream().findFirst();
        int removed = jdbcTemplate.update(
                "DELETE FROM oidc_authorization_request WHERE state = ?", state);
        if (found.isEmpty() || removed != 1) {
            return Optional.empty();
        }
        // 过期的请求删掉但不返回：调用方拿到空即拒绝，
        // 「不存在」「已用过」「已过期」三者对外不可区分。
        return found.filter(request -> now.isBefore(request.expiresAt()));
    }

    @Override
    public void deleteExpiredAuthorizationRequests(LocalDateTime now) {
        jdbcTemplate.update("DELETE FROM oidc_authorization_request WHERE expires_at < ?", now);
    }

    @Override
    public void insertSession(RpSession session, String tokenHash) {
        jdbcTemplate.update("""
                INSERT INTO rp_session(
                    id, token_hash, subject, display_name, email, picture,
                    org_id, workspace_id, roles, access_token, refresh_token,
                    access_expires_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                session.id(), tokenHash, session.subject(), session.displayName(),
                session.email(), session.picture(),
                session.tenant() == null ? null : session.tenant().orgId(),
                session.tenant() == null ? null : session.tenant().workspaceId(),
                session.rolesCsv(), session.accessToken(), session.refreshToken(),
                session.accessExpiresAt(), session.expiresAt());
    }

    @Override
    public Optional<RpSession> findByTokenHash(String tokenHash, LocalDateTime now) {
        return jdbcTemplate.query("""
                SELECT * FROM rp_session WHERE token_hash = ? AND expires_at > ?
                """, SESSION, tokenHash, now).stream().findFirst();
    }

    @Override
    public void updateTokens(String sessionId, String accessToken, String refreshToken,
                             LocalDateTime accessExpiresAt) {
        // 轮换是存新弃旧：保留上一张刷新令牌会让一次被窃取的旧令牌继续可用，
        // 而轮换的全部意义就是让它立刻失效。
        jdbcTemplate.update("""
                UPDATE rp_session
                SET access_token = ?, refresh_token = ?, access_expires_at = ?
                WHERE id = ?
                """, accessToken, refreshToken, accessExpiresAt, sessionId);
    }

    @Override
    public void touch(String tokenHash, LocalDateTime seenAt) {
        jdbcTemplate.update(
                "UPDATE rp_session SET last_seen_at = ? WHERE token_hash = ?", seenAt, tokenHash);
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        jdbcTemplate.update("DELETE FROM rp_session WHERE token_hash = ?", tokenHash);
    }

    @Override
    public void deleteBySubject(String subject) {
        jdbcTemplate.update("DELETE FROM rp_session WHERE subject = ?", subject);
    }

    @Override
    public void deleteExpiredSessions(LocalDateTime now) {
        jdbcTemplate.update("DELETE FROM rp_session WHERE expires_at < ?", now);
    }
}
