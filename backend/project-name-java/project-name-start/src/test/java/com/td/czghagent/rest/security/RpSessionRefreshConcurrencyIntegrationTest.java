// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
package com.td.czghagent.rest.security;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同一会话的两个请求在票过期后同时到达：只续期一次，会话活下来。
 *
 * <p>2026-09-16 生产上，owner 登录一个多小时后会话被静默删掉（没有登出审计）。平台的刷新令牌轮换带重放检测：
 * 一张已换过的刷新令牌再用一次，整条令牌链当场吊销。切回标签页时页面同时发出权益查询与门禁重问，
 * 两个请求各拿同一张旧刷新令牌去换，第二个被判重放——会话被删，人被踢回登录页。
 *
 * <p>必须跑在真 Postgres 上：排队靠的是 {@code SELECT … FOR UPDATE} 的行锁，内存替身里没有这个东西，
 * 在那里测只能测到替身。替身 IdP 按平台的规则轮换：旧令牌再用即拒，并把窗口拉宽到足以让两个请求撞上。
 */
@SpringBootTest(properties = {
        "app.storage.root=${java.io.tmpdir}/rp-refresh-race-${random.uuid}"
})
class RpSessionRefreshConcurrencyIntegrationTest extends PostgresBackedTest {

    @Autowired
    private RpSessionRepository repository;

    @Autowired
    private PlatformSessionResolver resolver;

    @Autowired
    private RotatingIdp idp;

    @Test
    void twoRequestsArrivingTogetherAfterExpiryShareOneRefreshAndKeepTheSession() throws Exception {
        String cookie = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String refreshToken = idp.issueRefreshToken();
        repository.insertSession(new RpSession(
                UUID.randomUUID().toString(), "sub-race", "张三", null, null,
                new TenantScope("org-1", "ws-1"), "workspace:owner",
                "access-expired", refreshToken,
                now.minusSeconds(5), now.plusHours(12),
                "华东设计院", "投标一部"), SessionToken.hash(cookie));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Optional<RpSession>> request = () -> {
                start.await();
                return resolver.resolveSession(cookie);
            };
            Future<Optional<RpSession>> first = pool.submit(request);
            Future<Optional<RpSession>> second = pool.submit(request);
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).as("先到的请求").isPresent();
            assertThat(second.get(30, TimeUnit.SECONDS))
                    .as("后到的请求拿旧刷新令牌再换一次，就会被平台判为重放、连会话一起吊销")
                    .isPresent();
        } finally {
            pool.shutdownNow();
        }

        assertThat(idp.refreshCalls()).as("两个请求只换一次票").isEqualTo(1);
        assertThat(repository.findByTokenHash(SessionToken.hash(cookie), LocalDateTime.now()))
                .as("会话还在：没有被当成续期失败删掉")
                .isPresent();
    }

    @TestConfiguration
    static class IdpConfiguration {
        @Bean
        @Primary
        RotatingIdp rotatingIdp() {
            return new RotatingIdp();
        }
    }

    /** 按平台规则轮换刷新令牌的替身：只有当前那张能换，旧令牌再用即拒。 */
    static final class RotatingIdp implements OidcGateway {
        private final AtomicInteger refreshCalls = new AtomicInteger();
        private final AtomicInteger generation = new AtomicInteger();
        private volatile String currentRefresh;

        String issueRefreshToken() {
            currentRefresh = "refresh-" + generation.incrementAndGet();
            return currentRefresh;
        }

        int refreshCalls() {
            return refreshCalls.get();
        }

        @Override
        public Tokens refresh(String refreshToken) {
            refreshCalls.incrementAndGet();
            try {
                // 平台一次换票的往返；让两个请求有足够的时间撞在一起。
                Thread.sleep(400);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            synchronized (this) {
                if (!refreshToken.equals(currentRefresh)) {
                    currentRefresh = null;
                    throw new BusinessException("AUTH_REFRESH_REUSED", "刷新令牌重放，令牌链已吊销", 401, false, null);
                }
                String next = issueRefreshToken();
                return new Tokens("access-" + generation.get(), next, null, 3600);
            }
        }

        @Override
        public String authorizationUrl(String state, String nonce, String codeChallenge) {
            throw new UnsupportedOperationException("本用例只涉及续期");
        }

        @Override
        public Tokens exchangeCode(String code, String codeVerifier) {
            throw new UnsupportedOperationException("本用例只涉及续期");
        }

        @Override
        public PlatformClaims readClaims(Tokens tokens, String expectedNonce) {
            throw new UnsupportedOperationException("本用例只涉及续期");
        }

        @Override
        public String subjectOfLogoutToken(String logoutToken) {
            throw new UnsupportedOperationException("本用例只涉及续期");
        }

        @Override
        public String endSessionUrl() {
            return null;
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
}
