// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.RpSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RP 会话仓储，跑在真数据库上。
 *
 * <p>这一组必须碰真库：要验的是 V27 的建表语句、SQL 本身、以及
 * 「取出即删除」这条<strong>跨两条语句的事务语义</strong>。
 * 用内存替身测这些，测的是替身的实现。
 *
 * <p>用与集成测试相同的 H2（MySQL 兼容模式）：生产是 MySQL 8.4，两者的交集
 * 比 MySQL 窄，而窄的那一侧才是迁移必须活下来的地方——V26 就是在这里
 * 被发现用了 MySQL 专有语法。
 */
@SpringBootTest(properties = {
        "app.bootstrap.enabled=false",
        "app.storage.root=${java.io.tmpdir}/rp-session-${random.uuid}"
})
class JdbcRpSessionRepositoryTest extends PostgresBackedTest {

    @Autowired
    private RpSessionRepository repository;

    // ── 授权请求 ────────────────────────────────────────────────────────────

    /**
     * 取出即删除，一次性。
     *
     * <p>这是跨「SELECT 然后 DELETE」两条语句的语义，也是内存替身最容易
     * 无意中实现对、而真 SQL 最容易实现错的一处。
     */
    @Test
    void consumingAnAuthorizationRequestRemovesIt() {
        String state = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        repository.saveAuthorizationRequest(new RpSessionRepository.AuthorizationRequest(
                state, "nonce-1", "verifier-1", "/planner/bids", now.plusMinutes(10)));

        Optional<RpSessionRepository.AuthorizationRequest> first =
                repository.consumeAuthorizationRequest(state, now);
        Optional<RpSessionRepository.AuthorizationRequest> second =
                repository.consumeAuthorizationRequest(state, now);

        assertThat(first).isPresent();
        assertThat(first.get().codeVerifier()).isEqualTo("verifier-1");
        assertThat(first.get().returnTo()).isEqualTo("/planner/bids");
        assertThat(second).as("重放一次成功的授权码交换 = 拿到别人的会话").isEmpty();
    }

    /**
     * 过期的请求：删掉但返回空。
     *
     * <p>「不存在」「已用过」「已过期」三者对外不可区分——区分它们只对攻击者有用。
     */
    @Test
    void anExpiredAuthorizationRequestIsConsumedButNotReturned() {
        String state = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        repository.saveAuthorizationRequest(new RpSessionRepository.AuthorizationRequest(
                state, "n", "v", "/", now.minusMinutes(1)));

        assertThat(repository.consumeAuthorizationRequest(state, now)).isEmpty();
        assertThat(repository.consumeAuthorizationRequest(state, now)).isEmpty();
    }

    // ── 会话 ────────────────────────────────────────────────────────────────

    @Test
    void storesAndReadsBackEveryFieldIncludingTheTenantAxis() {
        String tokenHash = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        RpSession session = sessionFor("sub-store", now.plusHours(12));

        repository.insertSession(session, tokenHash);

        RpSession found = repository.findByTokenHash(tokenHash, now).orElseThrow();
        assertThat(found.subject()).isEqualTo("sub-store");
        assertThat(found.displayName()).isEqualTo("张三");
        assertThat(found.tenant()).isEqualTo(new TenantScope("org-1", "ws-1"));
        assertThat(found.accessToken()).isEqualTo("access-1");
    }

    /**
     * 平台 subject 可以比本地 UUID 长得多。
     *
     * <p>这一条钉住 V28 放宽的那些列。它是被一次真实的
     * {@code Data too long for column 'actor_id'} 逼出来的——
     * 而那个尺寸假设在接真身份之前从来没有被触碰过。
     */
    @Test
    void acceptsAPlatformSubjectLongerThanALocalUuid() {
        String longSubject = "opr_" + "a".repeat(200);
        String tokenHash = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        repository.insertSession(sessionFor(longSubject, now.plusHours(1)), tokenHash);

        assertThat(repository.findByTokenHash(tokenHash, now).orElseThrow().subject())
                .isEqualTo(longSubject);
    }

    @Test
    void doesNotReturnAnExpiredSession() {
        String tokenHash = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        repository.insertSession(sessionFor("sub-expired", now.minusSeconds(1)), tokenHash);

        assertThat(repository.findByTokenHash(tokenHash, now)).isEmpty();
    }

    @Test
    void rotatingTokensReplacesBothOfThemAtOnce() {
        String tokenHash = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        RpSession session = sessionFor("sub-rotate", now.plusHours(12));
        repository.insertSession(session, tokenHash);

        repository.updateTokens(session.id(), "access-2", "refresh-2", now.plusHours(1));

        RpSession refreshed = repository.findByTokenHash(tokenHash, now).orElseThrow();
        assertThat(refreshed.accessToken()).isEqualTo("access-2");
        assertThat(refreshed.refreshToken())
                .as("存新弃旧：保留上一张会让被窃取的旧令牌继续可用")
                .isEqualTo("refresh-2");
    }

    /**
     * 反向登出撤销该 subject 的<strong>全部</strong>会话。
     *
     * <p>只清发起的那一个等于没有登出——同一个人可能开着几个标签页。
     */
    @Test
    void deletingBySubjectRemovesEverySessionOfThatPerson() {
        LocalDateTime now = LocalDateTime.now();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        repository.insertSession(sessionFor("sub-multi", now.plusHours(12)), first);
        repository.insertSession(sessionFor("sub-multi", now.plusHours(12)), second);
        repository.insertSession(sessionFor("sub-other", now.plusHours(12)), other);

        repository.deleteBySubject("sub-multi");

        assertThat(repository.findByTokenHash(first, now)).isEmpty();
        assertThat(repository.findByTokenHash(second, now)).isEmpty();
        assertThat(repository.findByTokenHash(other, now))
                .as("别人的会话不能被顺手清掉").isPresent();
    }

    private static RpSession sessionFor(String subject, LocalDateTime expiresAt) {
        return new RpSession(
                UUID.randomUUID().toString(), subject, "张三", "z@example.com", null,
                new TenantScope("org-1", "ws-1"), "workspace:owner",
                "access-1", "refresh-1",
                LocalDateTime.now().plusMinutes(5), expiresAt);
    }
}
