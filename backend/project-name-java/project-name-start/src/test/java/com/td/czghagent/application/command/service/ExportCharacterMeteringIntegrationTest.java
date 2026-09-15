// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.service;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 字数高水位打在真实库上：服务角色的列权限、行锁、事务边界。
 *
 * <p>这三件事替身都验不出来。列权限漏授是生产上的 permission denied；
 * 行锁漏掉是并发导出时重叠的字数被记两遍；事务边界错了是缓冲没写成、水位却抬了过去，
 * 那段字数再也不会被报。三者在界面上都毫无迹象。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/export-characters-${random.uuid}"
})
class ExportCharacterMeteringIntegrationTest extends PostgresBackedTest {

    private static final String CHARACTERS = "tenderforge.document.characters";

    @Autowired
    private ExportUsageMeter meter;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final String workspaceId = UUID.randomUUID().toString();
    private String bidId;

    @BeforeEach
    void insertBid() {
        bidId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO bid_document(id, owner_id, org_id, workspace_id, code, writing_method, title)
                VALUES (?, 'owner-1', 'org-1', ?, ?, 'SCORING_CRITERIA', '字数统计')
                """, bidId, workspaceId, "TA-" + bidId.substring(0, 8));
    }

    @Test
    void repeatedExportsReportOnlyTheGrowthAboveTheHighWaterMark() {
        long revisionBefore = revision();

        export(bid(workspaceId), "export-1", body(10));   // 全文 13（标题 3 + 正文 10）
        export(bid(workspaceId), "export-2", body(10));   // 未变
        export(bid(workspaceId), "export-3", body(15));   // 长到 18，补 5
        export(bid(workspaceId), "export-4", body(4));    // 改短到 7，不回退
        export(bid(workspaceId), "export-5", body(20));   // 长到 23，只补高出 18 的 5

        assertThat(jdbcTemplate.queryForList(
                "SELECT amount FROM platform_usage_event WHERE workspace_id = ? AND metric = ?"
                        + " ORDER BY amount DESC, idempotency_key", Long.class, workspaceId, CHARACTERS))
                .as("累计 = 导出过的最大全文字数 23")
                .containsExactly(13L, 5L, 5L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM platform_usage_event WHERE workspace_id = ? AND metric = ?",
                String.class, workspaceId, CHARACTERS))
                .containsExactlyInAnyOrder(
                        CHARACTERS + ":" + bidId + ":13",
                        CHARACTERS + ":" + bidId + ":18",
                        CHARACTERS + ":" + bidId + ":23");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metered_characters FROM bid_document WHERE id = ?", Long.class, bidId))
                .isEqualTo(23L);
        assertThat(revision()).as("计量不是用户可见的修改，不能顶掉界面的乐观锁版本")
                .isEqualTo(revisionBefore);
    }

    @Test
    void concurrentExportsDoNotCountTheOverlapTwice() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstRaised = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<OptionalLong> first = threads.submit(() -> transaction.execute(status -> {
                OptionalLong previous = bidRepository.raiseMeteredCharacters(bidId, 100);
                firstRaised.countDown();
                awaitQuietly(releaseFirst);
                return previous;
            }));
            assertThat(firstRaised.await(10, TimeUnit.SECONDS)).isTrue();

            Future<OptionalLong> second = threads.submit(() -> transaction.execute(
                    status -> bidRepository.raiseMeteredCharacters(bidId, 150)));
            // 第二个必须真的撞上第一个的行锁再放行——否则这只是两次顺序调用，什么也没验。
            awaitABackendWaitingOnALock();
            releaseFirst.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).hasValue(0);
            assertThat(second.get(10, TimeUnit.SECONDS))
                    .as("后到的导出要读到被抬过的水位 100、只报 50；读到 0 就把重叠的 100 字又报了一遍")
                    .hasValue(100);
        } finally {
            releaseFirst.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    void aFailedBufferWriteLeavesTheHighWaterMarkWhereItWas() {
        // workspace_id 超过缓冲列宽 255：缓冲写入失败（记录器吞掉异常，计量不失败调用方）。
        String unbufferable = "w".repeat(300);

        catchThrowable(() -> export(bid(unbufferable), "export-1", body(10)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT metered_characters FROM bid_document WHERE id = ?", Long.class, bidId))
                .as("缓冲没写成，水位就不能抬——抬了，这 13 字再也不会被报")
                .isZero();

        export(bid(workspaceId), "export-2", body(10));
        assertThat(jdbcTemplate.queryForList(
                "SELECT amount FROM platform_usage_event WHERE workspace_id = ? AND metric = ?",
                Long.class, workspaceId, CHARACTERS))
                .containsExactly(13L);
    }

    private void export(BidDocument bid, String exportId, List<BidWorkspace.Chapter> chapters) {
        meter.record(bid, chapters, exportId, "owner-1");
    }

    private long revision() {
        return jdbcTemplate.queryForObject(
                "SELECT revision FROM bid_document WHERE id = ?", Long.class, bidId);
    }

    private void awaitABackendWaitingOnALock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("第二个事务始终没有等在行锁上");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** 标题「第一章」3 字 + 正文 {@code characters} 字。 */
    private static List<BidWorkspace.Chapter> body(int characters) {
        return List.of(new BidWorkspace.Chapter("c1", "node-1", "第一章",
                "<p>" + "字".repeat(characters) + "</p>", "SUCCEEDED", LocalDateTime.now(), 1));
    }

    private BidDocument bid(String workspace) {
        return new BidDocument(bidId, "owner-1", new TenantScope("org-1", workspace), "TA-1",
                "SCORING_CRITERIA", "字数统计", 60, "OPEN", "CONTENT", "COMPLETED", false, null,
                LocalDateTime.now(), LocalDateTime.now(), 0);
    }
}
