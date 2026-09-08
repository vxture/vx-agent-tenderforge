// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.security;

import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * cookie 到身份的解析。
 *
 * <p>这一层每一条失败路径的处置都必须是「当作未登录」而不是「抛错」：
 * 它跑在<strong>每一个请求</strong>的过滤器里，一个从这里逃出去的异常
 * 会把「会话过期」变成「系统故障」，而用户看到的是后者。
 */
class PlatformSessionResolverTest {

    private InMemorySessions sessions;
    private OidcLoginService loginService;
    private PlatformSessionResolver resolver;

    @BeforeEach
    void setUp() {
        sessions = new InMemorySessions();
        loginService = mock(OidcLoginService.class);
        resolver = new PlatformSessionResolver(sessions, loginService);
    }

    @Test
    void resolvesAKnownCookieIntoTheCallerIdentity() {
        RpSession stored = seed("cookie-1", "sub-1", LocalDateTime.now().plusHours(1));
        when(loginService.refreshIfNeeded(any())).thenReturn(stored);

        CurrentUser user = resolver.resolve("cookie-1").orElseThrow();

        assertThat(user.id()).isEqualTo("sub-1");
        assertThat(user.tenant()).isEqualTo(new TenantScope("org-1", "ws-1"));
    }

    /** 查库用哈希，不是 cookie 原值。 */
    @Test
    void looksTheSessionUpByHashRatherThanByTheRawCookie() {
        RpSession stored = seed("cookie-1", "sub-1", LocalDateTime.now().plusHours(1));
        when(loginService.refreshIfNeeded(any())).thenReturn(stored);

        resolver.resolve("cookie-1");

        assertThat(sessions.lookedUpHashes).containsExactly(SessionToken.hash("cookie-1"));
    }

    @Test
    void yieldsNothingForAnUnknownCookie() {
        assertThat(resolver.resolve("never-issued")).isEmpty();
    }

    /**
     * 续期失败即会话死亡：<strong>删掉它</strong>，不是每次请求重试一次注定失败的刷新。
     *
     * <p>不删的表现是：用户每次点击都要等一次必然失败的网络往返，
     * 然后收到同一个 401——一个又慢又不解释自己的未登录状态。
     */
    @Test
    void dropsTheSessionWhenRefreshFails() {
        seed("cookie-1", "sub-1", LocalDateTime.now().plusHours(1));
        when(loginService.refreshIfNeeded(any())).thenThrow(new BusinessException(
                "AUTH_SESSION_EXPIRED", "登录状态已失效", 401, false, null));

        assertThat(resolver.resolve("cookie-1")).isEmpty();
        assertThat(sessions.deletedHashes)
                .containsExactly(SessionToken.hash("cookie-1"));
    }

    /**
     * 解析失败不能把异常抛出过滤器。
     *
     * <p>这一层跑在每个请求上；逃出去的异常会把「会话过期」渲染成「系统故障」。
     */
    @Test
    void neverLetsARefreshFailureEscapeAsAnError() {
        seed("cookie-1", "sub-1", LocalDateTime.now().plusHours(1));
        when(loginService.refreshIfNeeded(any())).thenThrow(new BusinessException(
                "AUTH_SESSION_EXPIRED", "x", 401, false, null));

        assertThat(resolver.resolve("cookie-1")).isEmpty();
    }

    /** 成功解析后刷新最近使用时间，让空闲会话可被回收判定。 */
    @Test
    void touchesTheSessionOnEverySuccessfulResolve() {
        RpSession stored = seed("cookie-1", "sub-1", LocalDateTime.now().plusHours(1));
        when(loginService.refreshIfNeeded(any())).thenReturn(stored);

        resolver.resolve("cookie-1");

        assertThat(sessions.touchedHashes).containsExactly(SessionToken.hash("cookie-1"));
    }

    private RpSession seed(String cookieValue, String subject, LocalDateTime expiresAt) {
        RpSession session = new RpSession(
                "s-1", subject, "张三", null, null,
                new TenantScope("org-1", "ws-1"), "workspace:owner",
                "access", "refresh", LocalDateTime.now().plusMinutes(30), expiresAt);
        sessions.stored.put(SessionToken.hash(cookieValue), session);
        return session;
    }

    private static final class InMemorySessions implements RpSessionRepository {
        private final Map<String, RpSession> stored = new HashMap<>();
        private final List<String> lookedUpHashes = new ArrayList<>();
        private final List<String> deletedHashes = new ArrayList<>();
        private final List<String> touchedHashes = new ArrayList<>();

        @Override
        public Optional<RpSession> findByTokenHash(String tokenHash, LocalDateTime now) {
            lookedUpHashes.add(tokenHash);
            return Optional.ofNullable(stored.get(tokenHash));
        }

        @Override
        public void deleteByTokenHash(String tokenHash) {
            deletedHashes.add(tokenHash);
            stored.remove(tokenHash);
        }

        @Override
        public void touch(String tokenHash, LocalDateTime seenAt) {
            touchedHashes.add(tokenHash);
        }

        @Override
        public void saveAuthorizationRequest(AuthorizationRequest request) {
        }

        @Override
        public Optional<AuthorizationRequest> consumeAuthorizationRequest(
                String state, LocalDateTime now) {
            return Optional.empty();
        }

        @Override
        public void deleteExpiredAuthorizationRequests(LocalDateTime now) {
        }

        @Override
        public void insertSession(RpSession session, String tokenHash) {
        }

        @Override
        public void updateTokens(String sessionId, String accessToken, String refreshToken,
                                 LocalDateTime accessExpiresAt) {
        }

        @Override
        public void deleteBySubject(String subject) {
        }

        @Override
        public void deleteExpiredSessions(LocalDateTime now) {
        }
    }
}
