// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.UsageEvent;
import com.td.czghagent.domain.model.UsageMetric;
import com.td.czghagent.domain.repository.UsageBufferRepository;
import com.td.czghagent.domain.repository.UsageBufferRepository.BufferedUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用量缓冲区打在真实库上。
 *
 * <p>认领互斥与租约是这一组的重点：api 与 worker 跑的是同一个镜像，两边都会
 * 起冲洗任务。互斥错了不会报错——只会让同一条用量被两个节点各报一次，
 * 而幂等键会让平台安静地把第二次当成重放，于是这个 bug 永远不会自己暴露。
 *
 * <p>顺带它也是 V29 建表语句的验收：这个上下文跑在 H2 上，
 * V26 就是在「只在 MySQL 上成立的 DDL」这件事上先失败了一次。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:usage-buffer-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.bootstrap.enabled=false",
        // 冲洗任务在后台跑会去认领测试刚写进去的行——关掉它。
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/usage-buffer-${random.uuid}"
})
class UsageBufferIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-09T10:00:00");

    @Autowired
    private UsageBufferRepository buffer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM platform_usage_event");
    }

    // ── 写入与去重 ──────────────────────────────────────────────────────────

    @Test
    void keepsEveryFieldItWasHandedIntact() {
        buffer.buffer(new UsageEvent("ws-1", UsageMetric.BID_GENERATIONS, 3,
                "key-1", "user-1", "task-9"), NOW);

        assertThat(claimAll()).singleElement().satisfies(row -> {
            assertThat(row.workspaceId()).isEqualTo("ws-1");
            assertThat(row.metric()).isEqualTo("tenderforge.bid.generations");
            assertThat(row.amount()).isEqualTo(3);
            assertThat(row.endUserId()).isEqualTo("user-1");
            assertThat(row.taskId()).isEqualTo("task-9");
            assertThat(row.occurredAt()).isEqualTo(NOW);
        });
    }

    /**
     * 同一个幂等键写第二次是<strong>无操作</strong>，不是覆盖也不是报错。
     *
     * <p>覆盖会让「同一次导出被记了两次」看起来像一次；报错会把一次无害的重放
     * 变成调用方要处理的异常——而调用方是导出成功之后的那一行代码。
     */
    @Test
    void treatsARepeatedKeyAsAlreadyRecorded() {
        buffer.buffer(new UsageEvent("ws-1", UsageMetric.DOCUMENT_EXPORTS, 1,
                "key-1", "user-1", null), NOW);
        buffer.buffer(new UsageEvent("ws-1", UsageMetric.DOCUMENT_EXPORTS, 99,
                "key-1", "user-2", null), NOW.plusMinutes(5));

        assertThat(claimAll()).singleElement().satisfies(row -> {
            assertThat(row.amount()).as("第二次不覆盖第一次").isEqualTo(1);
            assertThat(row.endUserId()).isEqualTo("user-1");
        });
    }

    // ── 认领互斥 ────────────────────────────────────────────────────────────

    /**
     * 顺序认领：第二次认领看不见已被认领的行。
     *
     * <p><strong>这一条只证明了顺序排他。</strong>它挡住的是候选 SELECT 的过滤，
     * 而不是 UPDATE 里的复检——实测确认过：把 UPDATE 的
     * {@code claimed_at} 条件改成恒真，这条测试仍然全绿。
     * 真正的并发互斥由 {@link #handsARowToExactlyOneOfTwoRacingClaimants()} 覆盖。
     */
    @Test
    void handsARowToExactlyOneClaimant() {
        buffer.buffer(event("key-1"), NOW);
        buffer.buffer(event("key-2"), NOW);

        List<BufferedUsage> first = buffer.claim("token-a", 10, NOW, NOW.minusMinutes(5));
        List<BufferedUsage> second = buffer.claim("token-b", 10, NOW, NOW.minusMinutes(5));

        assertThat(first).hasSize(2);
        assertThat(second).as("已被认领且租约未到期的行不该再被发出去").isEmpty();
    }

    /**
     * 租约过期后可以被抢走。
     *
     * <p>没有这一条，一个在冲洗中途被杀掉的进程会把它认领的那批用量
     * <strong>永久</strong>扣在手里——缓冲区里躺着一批谁也碰不了的账。
     */
    @Test
    void letsAnotherNodeTakeOverAfterTheLeaseExpires() {
        buffer.buffer(event("key-1"), NOW);
        buffer.claim("token-dead", 10, NOW, NOW.minusMinutes(5));

        List<BufferedUsage> rescued = buffer.claim(
                "token-alive", 10, NOW.plusMinutes(10), NOW.plusMinutes(5));

        assertThat(rescued).extracting(BufferedUsage::idempotencyKey).containsExactly("key-1");
    }

    /**
     * 两个节点同时认领，一行只能进一个人的手里。
     *
     * <p>这是 UPDATE 里那句 {@code claimed_at IS NULL OR claimed_at < ?} 唯一
     * 承重的地方：候选 SELECT 只做了一次乐观筛选，两个节点会筛出同一批行；
     * 真正把它们分开的是 UPDATE 在<strong>拿到行锁之后</strong>重新判一次条件。
     *
     * <p>做不到的表现极其隐蔽：同一条用量被 api 和 worker 各报一次，
     * 平台按幂等键当成重放安静吃掉，账没错、日志没错，
     * 只是两个节点从此一直在抢同一批行，而缓冲区看起来永远在冲洗。
     */
    @Test
    void handsARowToExactlyOneOfTwoRacingClaimants() throws Exception {
        for (int i = 0; i < 20; i++) {
            buffer.buffer(event("key-" + i), NOW.plusSeconds(i));
        }

        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<List<String>> claimant = () -> {
            start.await();
            try {
                return buffer.claim("token-" + Thread.currentThread().getId(), 20,
                                NOW.plusSeconds(30), NOW.minusMinutes(5))
                        .stream().map(BufferedUsage::idempotencyKey).toList();
            } catch (RuntimeException contended) {
                // 输掉行锁的一方拿不到任何行，这是合法结果——那些行留给下一轮。
                return List.of();
            }
        };
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var left = pool.submit(claimant);
            var right = pool.submit(claimant);
            List<String> a = left.get(30, java.util.concurrent.TimeUnit.SECONDS);
            List<String> b = right.get(30, java.util.concurrent.TimeUnit.SECONDS);

            // 按「两边合起来有没有重复」判，而不是 doesNotContainAnyElementsOf：
            // 输掉行锁的一方合法地拿到空列表，而那个断言遇到空集合会直接抛。
            List<String> together = new java.util.ArrayList<>(a);
            together.addAll(b);
            assertThat(together)
                    .as("同一行被两个节点各认走一次——这正是会被幂等键掩盖掉的那个 bug")
                    .doesNotHaveDuplicates();
            assertThat(together).as("至少有一方拿到了行，否则这条测试什么也没验").isNotEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void countsAttemptsSoAStuckRowIsVisible() {
        buffer.buffer(event("key-1"), NOW);

        buffer.claim("token-a", 10, NOW, NOW.minusMinutes(5));
        buffer.release(List.of("key-1"), "HTTP 503");
        List<BufferedUsage> again = buffer.claim("token-b", 10, NOW, NOW.minusMinutes(5));

        assertThat(again).singleElement()
                .satisfies(row -> assertThat(row.attempts()).isEqualTo(2));
    }

    @Test
    void honoursTheBatchLimitAndTakesTheOldestFirst() {
        buffer.buffer(event("newest"), NOW.plusMinutes(2));
        buffer.buffer(event("oldest"), NOW);
        buffer.buffer(event("middle"), NOW.plusMinutes(1));

        assertThat(buffer.claim("token-a", 2, NOW, NOW.minusMinutes(5)))
                .extracting(BufferedUsage::idempotencyKey)
                .containsExactly("oldest", "middle");
    }

    // ── 冲洗与归还 ──────────────────────────────────────────────────────────

    @Test
    void stopsHandingOutARowOnceItIsFlushed() {
        buffer.buffer(event("key-1"), NOW);
        buffer.claim("token-a", 10, NOW, NOW.minusMinutes(5));
        buffer.markFlushed(List.of("key-1"), NOW.plusSeconds(1));

        assertThat(buffer.claim("token-b", 10, NOW.plusHours(1), NOW))
                .as("已冲洗的行不再进入任何认领").isEmpty();
    }

    /** 归还后立刻可以被重新认领，不必等租约走完。 */
    @Test
    void makesAReleasedRowImmediatelyClaimableAgain() {
        buffer.buffer(event("key-1"), NOW);
        buffer.claim("token-a", 10, NOW, NOW.minusMinutes(5));
        buffer.release(List.of("key-1"), "HTTP 503: upstream down");

        assertThat(buffer.claim("token-b", 10, NOW, NOW.minusMinutes(5))).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error FROM platform_usage_event WHERE idempotency_key = 'key-1'",
                String.class)).contains("503");
    }

    /** 空列表不该发出任何语句——一条没有占位符的 IN () 在多数库上是语法错误。 */
    @Test
    void doesNothingWhenHandedAnEmptyBatch() {
        buffer.markFlushed(List.of(), NOW);
        buffer.release(List.of(), "unused");

        assertThat(claimAll()).isEmpty();
    }

    // ── 对账窗口 ────────────────────────────────────────────────────────────

    /**
     * 清理只碰已冲洗且过了窗口的行。
     *
     * <p>把还没冲洗的行清掉就是直接销毁账目——那笔钱再也没有任何地方记得。
     */
    @Test
    void purgesOnlyWhatHasBeenReportedAndAged() {
        buffer.buffer(event("flushed-old"), NOW.minusDays(30));
        buffer.buffer(event("flushed-recent"), NOW.minusDays(1));
        buffer.buffer(event("never-flushed"), NOW.minusDays(30));
        buffer.markFlushed(List.of("flushed-old"), NOW.minusDays(30));
        buffer.markFlushed(List.of("flushed-recent"), NOW.minusDays(1));

        assertThat(buffer.purgeFlushedBefore(NOW.minusDays(14))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM platform_usage_event", String.class))
                .containsExactlyInAnyOrder("flushed-recent", "never-flushed");
    }

    private List<BufferedUsage> claimAll() {
        return buffer.claim("probe-" + System.nanoTime(), 100, NOW, NOW.minusMinutes(5));
    }

    private static UsageEvent event(String key) {
        return new UsageEvent("ws-1", UsageMetric.BID_GENERATIONS, 1, key, "user-1", null);
    }
}
