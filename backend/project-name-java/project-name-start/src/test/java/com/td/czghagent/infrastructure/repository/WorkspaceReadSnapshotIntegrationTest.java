// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * 工作台读取在同一个快照里完成。
 *
 * <p>线上的症状是「重新生成目录后，偶发仍读到旧目录」：目录任务显示已完成，章节与页数
 * 却还是旧的。写入侧是原子的，撕裂发生在读取侧——读取器分多条语句取数据，
 * 另一个事务恰好在两条语句之间提交。
 *
 * <p>靠并发撞这个窗口是不确定的，所以这里<strong>把提交钉进窗口里</strong>：
 * 读取器的倒数第二步是 {@code productionRepository.loadState}，最后一步重新读标书行。
 * 在 {@code loadState} 被调用的那一刻，另一个线程提交一次改名；
 * 快照成立时最后那次读看不见它，不成立时就读到了。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/workspace-snapshot-${random.uuid}"
})
class WorkspaceReadSnapshotIntegrationTest extends PostgresBackedTest {

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private BidProductionRepository productionRepository;

    private String bidId;

    @BeforeEach
    void insertBid() {
        bidId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO bid_document(id, owner_id, org_id, workspace_id, code, writing_method, title)
                VALUES (?, 'owner-1', 'org-1', 'ws-1', ?, 'SCORING_CRITERIA', '改名之前')
                """, bidId, "SN-" + bidId.substring(0, 8));
    }

    @Test
    void aCommitLandingMidReadIsInvisibleToTheRestOfThatRead() throws Exception {
        BidDocument bid = bidRepository.findBidForTask(bidId, "owner-1").orElseThrow();
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        doAnswer(invocation -> {
            // 另起线程提交：它不在读取事务里，是一次真正的并发写。
            Thread writer = new Thread(() -> {
                try {
                    jdbcTemplate.update(
                            "UPDATE bid_document SET title = '改名之后' WHERE id = ?", bidId);
                } catch (Throwable failure) {
                    writerFailure.set(failure);
                }
            });
            writer.start();
            writer.join(10_000);
            return invocation.callRealMethod();
        }).when(productionRepository).loadState(eq(bidId), any());

        BidWorkspace workspace = bidRepository.loadWorkspace(bid);

        assertThat(writerFailure.get()).as("并发写本身要成功，否则这条用例什么也没验").isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM bid_document WHERE id = ?", String.class, bidId))
                .as("改名确实在读取途中提交了")
                .isEqualTo("改名之后");
        assertThat(workspace.bid().title())
                .as("同一次读取里，读取开始之后提交的改动必须不可见——否则就是撕裂读")
                .isEqualTo("改名之前");
    }
}
