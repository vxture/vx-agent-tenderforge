// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.query.service;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.domain.model.TenantScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三个列表的游标分页打在真实库上（通则 A-3）。
 *
 * <p>键集分页错了不会报错，只会让数据在页边界上<strong>重复或消失</strong>——而两种表现
 * 都像「数据就是这样」。所以每个列表都故意造<strong>时间完全相同</strong>的几行：
 * 只按时间比、漏了决胜键 {@code id} 的实现，恰好在这里重复或漏行。
 *
 * <p>期望顺序不在测试里手算，而是让数据库按契约里的 {@code ORDER BY 时间 DESC, id DESC}
 * 给出——uuid 的比较规则归数据库，测试只断言「翻完所有页 == 一次性按同一顺序查出来」。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/cursor-pagination-${random.uuid}"
})
class ListCursorPaginationIntegrationTest extends PostgresBackedTest {

    /** 翻页次数的硬上限：游标失效时会反复拿到第一页，没有它这条用例会挂死而不是变红。 */
    private static final int MAX_PAGES = 20;

    @Autowired
    private BidQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String workspaceId = UUID.randomUUID().toString();
    private CurrentUser user;

    @BeforeEach
    void signIn() {
        user = new CurrentUser("owner-" + workspaceId, "owner", "Owner", "PLANNER", null,
                new TenantScope("org-1", workspaceId));
    }

    @Test
    void pagingThroughBidsVisitsEveryRowOnceInContractOrder() {
        // 五份标书，其中三份更新时间完全相同。
        insertBid("2026-09-15T10:00:03Z");
        insertBid("2026-09-15T10:00:02Z");
        insertBid("2026-09-15T10:00:02Z");
        insertBid("2026-09-15T10:00:02Z");
        insertBid("2026-09-15T10:00:01Z");

        List<String> expected = jdbcTemplate.queryForList("""
                SELECT id::text FROM bid_document WHERE owner_id = ? AND workspace_id = ?
                ORDER BY updated_at DESC, id DESC
                """, String.class, user.id(), workspaceId);

        assertThat(walk((limit, cursor) -> queryService.list(user, limit, cursor), item -> item.id()))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void pagingThroughAssetsVisitsEveryActiveRowOnceInContractOrder() {
        insertAsset("2026-09-15T10:00:03Z", "ACTIVE");
        insertAsset("2026-09-15T10:00:02Z", "ACTIVE");
        insertAsset("2026-09-15T10:00:02Z", "ACTIVE");
        insertAsset("2026-09-15T10:00:02Z", "PROCESSING");
        insertAsset("2026-09-15T10:00:02Z", "ACTIVE");
        insertAsset("2026-09-15T10:00:01Z", "ACTIVE");

        List<String> expected = jdbcTemplate.queryForList("""
                SELECT id::text FROM bid_reference_asset
                WHERE owner_id = ? AND workspace_id = ? AND status = 'ACTIVE'
                ORDER BY updated_at DESC, id DESC
                """, String.class, user.id(), workspaceId);

        assertThat(expected).as("未就绪的素材不进列表，分页不能把这个筛选弄丢").hasSize(5);
        assertThat(walk((limit, cursor) -> queryService.assets(null, null, limit, cursor, user),
                item -> item.id()))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void pagingThroughExportsVisitsEveryVersionOnceNewestFirst() {
        String bidId = insertBid("2026-09-15T09:00:00Z");
        insertExport(bidId, 1, "2026-09-15T10:00:01Z");
        insertExport(bidId, 2, "2026-09-15T10:00:02Z");
        insertExport(bidId, 3, "2026-09-15T10:00:02Z");
        insertExport(bidId, 4, "2026-09-15T10:00:02Z");
        insertExport(bidId, 5, "2026-09-15T10:00:03Z");

        List<String> expected = jdbcTemplate.queryForList("""
                SELECT id::text FROM bid_export WHERE bid_id = ?::uuid
                ORDER BY created_at DESC, id DESC
                """, String.class, bidId);

        assertThat(walk((limit, cursor) -> queryService.exports(bidId, limit, cursor, user),
                item -> item.id()))
                .containsExactlyElementsOf(expected);
        assertThat(queryService.exports(bidId, 1, null, user).items())
                .as("「下载最新」取 limit=1 的第一条，必须是最近生成的那份")
                .singleElement()
                .satisfies(latest -> assertThat(latest.version()).isEqualTo(5));
    }

    @Test
    void limitIsClampedAndAMalformedCursorStartsFromTheBeginning() {
        for (int i = 0; i < 3; i++) {
            insertBid("2026-09-15T10:00:0" + i + "Z");
        }

        CursorPage<?> tiny = queryService.list(user, 0, null);
        assertThat(tiny.items()).as("limit 小于 1 钳制到 1，不报错").hasSize(1);
        assertThat(tiny.nextCursor()).as("只取了一条，后面还有").isNotNull();

        CursorPage<?> huge = queryService.list(user, 100_000, null);
        assertThat(huge.items()).as("超上限钳制到 200，三条全在").hasSize(3);
        assertThat(huge.nextCursor()).as("全在这一页，没有下一页").isNull();

        assertThat(queryService.list(user, 2, "not-a-cursor").items())
                .as("游标解不开从头开始，而不是报错页")
                .containsExactlyElementsOf(queryService.list(user, 2, null).items());
    }

    /** 以每页两条翻到底，返回按页拼接的标识；同时断言每页不超宽、最后一页没有游标。 */
    private <T> List<String> walk(BiFunction<Integer, String, CursorPage<T>> fetch,
                                  java.util.function.Function<T, String> idOf) {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            CursorPage<T> result = fetch.apply(2, cursor);
            assertThat(result.items()).hasSizeLessThanOrEqualTo(2);
            result.items().forEach(item -> seen.add(idOf.apply(item)));
            if (result.nextCursor() == null) {
                return seen;
            }
            cursor = result.nextCursor();
        }
        throw new AssertionError("翻了 " + MAX_PAGES + " 页还没到底——游标没有推进，已见：" + seen);
    }

    private String insertBid(String updatedAt) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO bid_document(id, owner_id, org_id, workspace_id, code, writing_method, title,
                                         created_at, updated_at)
                VALUES (?::uuid, ?, 'org-1', ?, ?, 'SCORING_CRITERIA', '分页', ?::timestamptz, ?::timestamptz)
                """, id, user.id(), workspaceId, "PG-" + id.substring(0, 8), updatedAt, updatedAt);
        return id;
    }

    private void insertAsset(String updatedAt, String status) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO bid_reference_asset(id, owner_id, org_id, workspace_id, category, display_name,
                                                original_file_name, object_key, media_type, file_size,
                                                content_hash, status, created_at, updated_at)
                VALUES (?::uuid, ?, 'org-1', ?, 'TEMPLATE', '范本', 'a.docx', ?, 'application/octet-stream',
                        1, 'hash', ?, ?::timestamptz, ?::timestamptz)
                """, id, user.id(), workspaceId, "assets/" + id, status, updatedAt, updatedAt);
    }

    private void insertExport(String bidId, int version, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO bid_export(id, bid_id, version_no, file_name, object_key, file_size, created_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, 1, ?::timestamptz)
                """, UUID.randomUUID().toString(), bidId, version, "v" + version + ".docx",
                "exports/" + bidId + "/" + version, createdAt);
    }
}
