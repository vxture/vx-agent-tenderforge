// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.domain.repository.AuditRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 登录回路的状态机。
 *
 * <p>这一组保护的四件事，错了都不会崩：state 被重放（攻击者拿到别人的会话）、
 * 过期的授权请求仍然可用（重放窗口无限大）、续期没有轮换（旧刷新令牌继续可用）、
 * 续期失败被当成瞬时故障（用户卡在永远转圈的界面上）。
 *
 * <p>用手写的内存仓储而不是 Mockito：这些断言关心的是<strong>状态如何演变</strong>
 * ——「消费之后还在不在」——而不是「哪个方法被调了几次」。
 * 用打桩表达前者，会写成一串 verify，读的人拼不出那条状态轨迹。
 */
class OidcLoginServiceTest {

    private InMemorySessions sessions;
    private RecordingAudit audit;
    private StubGateway gateway;
    private OidcLoginService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemorySessions();
        audit = new RecordingAudit();
        gateway = new StubGateway();
        service = new OidcLoginService(gateway, sessions, audit);
    }

    // ── 授权请求是一次性的 ──────────────────────────────────────────────────

    @Test
    void storesTheVerifierServerSideAndNeverInTheAuthorizationUrl() {
        String url = service.beginAuthorization("/planner/bids");

        RpSessionRepository.AuthorizationRequest stored = sessions.onlyAuthorizationRequest();
        assertThat(url).contains("state=" + stored.state());
        assertThat(url)
                .as("PKCE verifier 一旦出现在浏览器可见的地方，PKCE 就退化成一个多余的往返")
                .doesNotContain(stored.codeVerifier());
        assertThat(url).contains("code_challenge=");
    }

    @Test
    void sanitizesTheReturnTargetBeforeStoringIt() {
        service.beginAuthorization("//evil.example");

        assertThat(sessions.onlyAuthorizationRequest().returnTo()).isEqualTo("/");
    }

    /**
     * 同一个 state 只能换一次会话。
     *
     * <p>留着的 state 可以被重放，而重放一次成功的授权码交换，
     * 意味着攻击者能拿到一个属于别人的会话。
     */
    @Test
    void refusesToReplayTheSameState() {
        service.beginAuthorization("/");
        String state = sessions.onlyAuthorizationRequest().state();

        service.completeAuthorization(state, "code-1", "trace-1", "127.0.0.1");

        assertThatThrownBy(() ->
                service.completeAuthorization(state, "code-1", "trace-1", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("AUTH_OIDC_STATE_INVALID");
    }

    @Test
    void refusesAnUnknownState() {
        assertThatThrownBy(() ->
                service.completeAuthorization("never-issued", "code-1", "t", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
    }

    /** 过期的授权请求即使还没被用过也不可用。 */
    @Test
    void refusesAnExpiredAuthorizationRequest() {
        sessions.saveAuthorizationRequest(new RpSessionRepository.AuthorizationRequest(
                "stale-state", "nonce", "verifier", "/",
                LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() ->
                service.completeAuthorization("stale-state", "code", "t", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
    }

    // ── 会话建立 ────────────────────────────────────────────────────────────

    @Test
    void storesOnlyTheHashOfTheCookieValue() {
        service.beginAuthorization("/planner/bids");
        String state = sessions.onlyAuthorizationRequest().state();

        OidcLoginService.CallbackResult result =
                service.completeAuthorization(state, "code-1", "trace-1", "127.0.0.1");

        assertThat(result.returnTo()).isEqualTo("/planner/bids");
        assertThat(sessions.storedTokenHashes)
                .as("库里存哈希：被读走时读到的东西无法用来冒充任何人")
                .containsExactly(SessionToken.hash(result.cookieValue()))
                .doesNotContain(result.cookieValue());
    }

    @Test
    void passesTheStoredNonceToTheGatewayForVerification() {
        service.beginAuthorization("/");
        RpSessionRepository.AuthorizationRequest stored = sessions.onlyAuthorizationRequest();

        service.completeAuthorization(stored.state(), "code-1", "t", "127.0.0.1");

        assertThat(gateway.lastExpectedNonce)
                .as("nonce 必须来自这一次授权请求，不能是调用方送来的任何东西")
                .isEqualTo(stored.nonce());
    }

    @Test
    void writesOneLoginAuditCarryingTheConsoleAndTenant() {
        service.beginAuthorization("/");
        service.completeAuthorization(
                sessions.onlyAuthorizationRequest().state(), "code-1", "trace-1", "10.0.0.1");

        assertThat(audit.events).hasSize(1);
        AuditEvent event = audit.events.get(0);
        assertThat(event.action()).isEqualTo("AUTH_LOGIN");
        assertThat(event.actorConsole()).isEqualTo("tenderforge");
        assertThat(event.workspaceId()).isEqualTo("ws-1");
    }

    // ── 静默续期 ────────────────────────────────────────────────────────────

    @Test
    void leavesAHealthySessionAlone() {
        RpSession healthy = sessionExpiringIn(600);

        assertThat(service.refreshIfNeeded(healthy)).isSameAs(healthy);
        assertThat(gateway.refreshCalls).isZero();
    }

    /** 轮换是存新弃旧：保留上一张刷新令牌会让一次被窃取的旧令牌继续可用。 */
    @Test
    void rotatesTokensWhenTheAccessTokenIsAboutToExpire() {
        RpSession expiring = sessionExpiringIn(10);
        gateway.nextRefresh = new OidcGateway.Tokens("new-access", "new-refresh", null, 3600);

        RpSession refreshed = service.refreshIfNeeded(expiring);

        assertThat(refreshed.accessToken()).isEqualTo("new-access");
        assertThat(refreshed.refreshToken()).isEqualTo("new-refresh");
        assertThat(sessions.updatedRefreshTokens).containsExactly("new-refresh");
    }

    /**
     * 组织名与工作空间名随会话落库，并带到调用者身份上。
     *
     * <p>门禁页「当前工作区」读的就是它；丢在这一步，界面上只剩兜底文案，而登录本身一切正常。
     */
    @Test
    void keepsTheOrganizationAndWorkspaceNamesOnTheSession() {
        service.beginAuthorization("/");

        OidcLoginService.CallbackResult result = service.completeAuthorization(
                sessions.onlyAuthorizationRequest().state(), "code-1", "trace-1", "127.0.0.1");

        assertThat(sessions.insertedSessions).singleElement().satisfies(stored -> {
            assertThat(stored.orgName()).isEqualTo("华东设计院");
            assertThat(stored.workspaceName()).isEqualTo("投标一部");
        });
        assertThat(result.session().toCurrentUser().orgName()).isEqualTo("华东设计院");
        assertThat(result.session().toCurrentUser().workspaceName()).isEqualTo("投标一部");
    }

    /** 续期换的是票，不是人：名字原样带过去，不在续期之后变回兜底文案。 */
    @Test
    void refreshingKeepsTheOrganizationAndWorkspaceNames() {
        gateway.nextRefresh = new OidcGateway.Tokens("new-access", "new-refresh", null, 3600);

        RpSession refreshed = service.refreshIfNeeded(sessionExpiringIn(10));

        assertThat(refreshed.orgName()).isEqualTo("华东设计院");
        assertThat(refreshed.workspaceName()).isEqualTo("投标一部");
    }

    /**
     * IdP 不轮换刷新令牌时沿用旧的。
     *
     * <p>「没返回新的」是一种合法配置，不是缺失。把它当成缺失会在第一次续期后
     * 把 refresh_token 置空，于是下一次续期必然失败——而那要等到一小时后才发作。
     */
    @Test
    void keepsTheExistingRefreshTokenWhenTheIssuerDoesNotRotateIt() {
        RpSession expiring = sessionExpiringIn(10);
        gateway.nextRefresh = new OidcGateway.Tokens("new-access", null, null, 3600);

        assertThat(service.refreshIfNeeded(expiring).refreshToken()).isEqualTo("old-refresh");
    }

    @Test
    void treatsAMissingRefreshTokenAsSessionDeath() {
        RpSession noRefresh = sessions.remember(new RpSession(
                "s-1", "sub-1", "张三", null, null, null, "",
                "access", null,
                LocalDateTime.now().plusSeconds(10), LocalDateTime.now().plusHours(1)));

        assertThatThrownBy(() -> service.refreshIfNeeded(noRefresh))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("AUTH_SESSION_EXPIRED");
    }

    /**
     * 另一个请求刚换好票：锁住会话行重读后直接用，不再拿旧刷新令牌去换。
     *
     * <p>平台的刷新令牌轮换带重放检测，旧令牌再用一次，整条令牌链当场吊销——会话随之被删，
     * 人被踢回登录页（2026-09-16 生产上发生过，没有任何登出审计）。
     */
    @Test
    void usesTheTokensAnotherRequestJustRotatedInsteadOfRefreshingAgain() {
        RpSession stale = sessionExpiringIn(10);
        sessions.remember(new RpSession(
                "s-1", "sub-1", "张三", null, null, null, "",
                "fresh-access", "fresh-refresh",
                LocalDateTime.now().plusSeconds(3600), LocalDateTime.now().plusHours(12),
                "华东设计院", "投标一部"));

        RpSession resolved = service.refreshIfNeeded(stale);

        assertThat(resolved.accessToken()).isEqualTo("fresh-access");
        assertThat(resolved.refreshToken()).isEqualTo("fresh-refresh");
        assertThat(gateway.refreshCalls).as("旧刷新令牌不能再拿去换").isZero();
    }

    /** 锁住时会话已经不在（刚登出、反向登出）：按会话失效处理，不拿它的刷新令牌去换票。 */
    @Test
    void treatsASessionDeletedBeforeTheLockAsSessionDeath() {
        RpSession gone = new RpSession(
                "s-gone", "sub-1", "张三", null, null, null, "",
                "old-access", "old-refresh",
                LocalDateTime.now().plusSeconds(10), LocalDateTime.now().plusHours(12));

        assertThatThrownBy(() -> service.refreshIfNeeded(gone))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("AUTH_SESSION_EXPIRED");
        assertThat(gateway.refreshCalls).isZero();
    }

    // ── 登出 ────────────────────────────────────────────────────────────────

    @Test
    void backChannelLogoutRevokesEverySessionOfThatSubject() {
        service.backChannelLogout("sub-1");

        assertThat(sessions.deletedSubjects)
                .as("平台通知的是「这个人登出了」，而他可能开着几个标签页")
                .containsExactly("sub-1");
        assertThat(audit.events).extracting(AuditEvent::actorConsole)
                .as("后台通道不属于任何控制台，MUST NOT 硬编一个")
                .containsOnlyNulls();
    }

    /** 快到期的会话，同时记成库里此刻的那一行（续期加锁后重读的就是它）。 */
    private RpSession sessionExpiringIn(long seconds) {
        return sessions.remember(new RpSession(
                "s-1", "sub-1", "张三", null, null, null, "",
                "old-access", "old-refresh",
                LocalDateTime.now().plusSeconds(seconds), LocalDateTime.now().plusHours(12),
                "华东设计院", "投标一部"));
    }

    // ── 登出 ────────────────────────────────────────────────────────────────

    /**
     * 登出要把人送去平台登出端点。
     *
     * <p>只删本地会话时界面看起来一切正常——直到用户想换个账号：再点登录，
     * 账户中心的会话还在，静默 SSO 把他原样送了回来。
     */
    @Test
    void logoutHandsBackThePlatformEndSessionUrl() {
        gateway.endSessionUrl = "https://accounts.example/oidc/end_session?client_id=tenderforge";

        String logoutUrl = service.logout("cookie-1", null, "trace-1", "127.0.0.1");

        assertThat(logoutUrl).isEqualTo("https://accounts.example/oidc/end_session?client_id=tenderforge");
    }

    /** 身份服务不可达时，退出仍然成功：本地会话已撤销，只是没有登出地址可去。 */
    @Test
    void logoutStillSucceedsWhenTheIdentityServiceIsUnreachable() {
        gateway.endSessionFailure = new BusinessException(
                "AUTH_ISSUER_UNREACHABLE", "身份服务不可达", 503, true, null);

        String logoutUrl = service.logout("cookie-1", null, "trace-1", "127.0.0.1");

        assertThat(logoutUrl).isNull();
    }

    // ── 替身 ────────────────────────────────────────────────────────────────

    private static final class StubGateway implements OidcGateway {
        private String lastExpectedNonce;
        private int refreshCalls;
        private Tokens nextRefresh = new Tokens("a", "r", null, 3600);
        private String endSessionUrl;
        private BusinessException endSessionFailure;

        @Override
        public String authorizationUrl(String state, String nonce, String codeChallenge) {
            return "https://idp.example/authorize?state=" + state
                    + "&nonce=" + nonce + "&code_challenge=" + codeChallenge;
        }

        @Override
        public Tokens exchangeCode(String code, String codeVerifier) {
            return new Tokens("access", "refresh", "id", 3600);
        }

        @Override
        public Tokens refresh(String refreshToken) {
            refreshCalls++;
            return nextRefresh;
        }

        @Override
        public PlatformClaims readClaims(Tokens tokens, String expectedNonce) {
            lastExpectedNonce = expectedNonce;
            return new PlatformClaims("sub-1", "张三", null, null,
                    "org-1", "ws-1", List.of("workspace:owner"), "华东设计院", "投标一部");
        }

        @Override
        public String subjectOfLogoutToken(String logoutToken) {
            throw new UnsupportedOperationException("本用例不涉及反向登出");
        }

        @Override
        public String endSessionUrl() {
            if (endSessionFailure != null) {
                throw endSessionFailure;
            }
            return endSessionUrl;
        }

        @Override
        public long sessionSeconds() {
            return 43200;
        }

        @Override
        public boolean isMock() {
            return true;
        }
    }

    private static final class RecordingAudit implements AuditRepository {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void append(AuditEvent event) {
            events.add(event);
        }
    }

    /** 手写内存仓储：断言的是状态轨迹，不是调用次数。 */
    private static final class InMemorySessions implements RpSessionRepository {
        private final Map<String, AuthorizationRequest> requests = new HashMap<>();
        private final List<String> storedTokenHashes = new ArrayList<>();
        private final List<RpSession> insertedSessions = new ArrayList<>();
        private final List<String> updatedRefreshTokens = new ArrayList<>();
        private final List<String> deletedSubjects = new ArrayList<>();

        AuthorizationRequest onlyAuthorizationRequest() {
            assertThat(requests).hasSize(1);
            return requests.values().iterator().next();
        }

        @Override
        public void saveAuthorizationRequest(AuthorizationRequest request) {
            requests.put(request.state(), request);
        }

        @Override
        public Optional<AuthorizationRequest> consumeAuthorizationRequest(
                String state, LocalDateTime now) {
            AuthorizationRequest removed = requests.remove(state);
            return Optional.ofNullable(removed)
                    .filter(request -> now.isBefore(request.expiresAt()));
        }

        @Override
        public void deleteExpiredAuthorizationRequests(LocalDateTime now) {
            requests.values().removeIf(request -> !now.isBefore(request.expiresAt()));
        }

        @Override
        public void insertSession(RpSession session, String tokenHash) {
            storedTokenHashes.add(tokenHash);
            insertedSessions.add(session);
        }

        @Override
        public Optional<RpSession> findByTokenHash(String tokenHash, LocalDateTime now) {
            return Optional.empty();
        }

        /** 库里此刻的会话行：续期加锁后重读的就是它。 */
        private final Map<String, RpSession> rows = new HashMap<>();

        RpSession remember(RpSession session) {
            rows.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<RpSession> lockForRefresh(String sessionId) {
            return Optional.ofNullable(rows.get(sessionId));
        }

        @Override
        public void updateTokens(String sessionId, String accessToken, String refreshToken,
                                 LocalDateTime accessExpiresAt) {
            updatedRefreshTokens.add(refreshToken);
        }

        @Override
        public void touch(String tokenHash, LocalDateTime seenAt) {
        }

        @Override
        public void deleteByTokenHash(String tokenHash) {
        }

        @Override
        public void deleteBySubject(String subject) {
            deletedSubjects.add(subject);
        }

        @Override
        public void deleteExpiredSessions(LocalDateTime now) {
        }
    }
}
