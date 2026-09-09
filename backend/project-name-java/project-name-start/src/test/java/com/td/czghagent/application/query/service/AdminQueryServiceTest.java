// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.application.query.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.model.PageCursor;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理面查询：游标翻页与准入。
 *
 * <p>游标那一段是<strong>契约行为</strong>（通则 A-3），而它有两个安静的失败模式：
 * 多取的那一条被当成数据返回给调用方（每页多出一行，且下一页会重复它），
 * 以及最后一页仍然给出 nextCursor（调用方永远翻不到头）。两者都不会报错。
 */
class AdminQueryServiceTest {

    private RecordingRepository repository;
    private AdminQueryService service;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        service = new AdminQueryService(repository);
    }

    // ── 准入 ────────────────────────────────────────────────────────────────

    @Test
    void refusesEveryQueryToANonAdministrator() {
        CurrentUser planner = userWithRole("PLANNER");

        assertThat(rejectionOf(() -> service.listUsers(planner, 10, null, null, null)))
                .isEqualTo("ADMIN_ACCESS_DENIED");
        assertThat(rejectionOf(() -> service.getUser(planner, "u-1")))
                .isEqualTo("ADMIN_ACCESS_DENIED");
        assertThat(rejectionOf(() ->
                service.listAuditLogs(planner, 10, null, null, null, null, null, null)))
                .isEqualTo("ADMIN_ACCESS_DENIED");
    }

    @Test
    void refusesAnAnonymousCaller() {
        assertThat(rejectionOf(() -> service.listUsers(null, 10, null, null, null)))
                .isEqualTo("ADMIN_ACCESS_DENIED");
    }

    // ── 账号列表 ────────────────────────────────────────────────────────────

    @Test
    void clampsTheRequestedLimitServerSide() {
        service.listUsers(admin(), 10_000, null, null, null);
        assertThat(repository.lastUserFilter.limit()).isEqualTo(CursorPage.MAX_LIMIT);

        service.listUsers(admin(), null, null, null, null);
        assertThat(repository.lastUserFilter.limit()).isEqualTo(CursorPage.DEFAULT_LIMIT);

        service.listUsers(admin(), 0, null, null, null);
        assertThat(repository.lastUserFilter.limit()).isEqualTo(1);
    }

    @Test
    void normalisesBlankFiltersToNullSoTheyDoNotNarrowTheQuery() {
        service.listUsers(admin(), 10, "   ", null, null);

        assertThat(repository.lastUserFilter.keyword())
                .as("空白关键字若原样下推，会变成一个匹配不到任何行的 LIKE")
                .isNull();
    }

    @Test
    void rejectsAnUnknownRoleFilter() {
        assertThat(rejectionOf(() -> service.listUsers(admin(), 10, null, "SUPERUSER", null)))
                .isEqualTo("USER_ROLE_INVALID");
    }

    @Test
    void acceptsRoleFiltersCaseInsensitively() {
        service.listUsers(admin(), 10, null, "admin", null);

        assertThat(repository.lastUserFilter.roleCode()).isEqualTo("ADMIN");
    }

    // ── 审计流水的游标 ──────────────────────────────────────────────────────

    @Test
    void rejectsAnInvertedTimeRange() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(rejectionOf(() -> service.listAuditLogs(
                admin(), 10, null, null, null, null, now, now.minusDays(1))))
                .isEqualTo("AUDIT_TIME_RANGE_INVALID");
    }

    /**
     * 多取一条用来判断有没有下一页，<strong>那一条不返回</strong>。
     *
     * <p>把它一起返回的后果是每页多出一行，而且下一页会以它开头——
     * 调用方看到的是「有一条记录出现了两次」，而不是「服务端多给了一条」。
     */
    @Test
    void fetchesOneExtraRowToDetectTheNextPageButNeverReturnsIt() {
        repository.auditRows = rows(6);

        CursorPage<AuditLogEntry> page = service.listAuditLogs(
                admin(), 5, null, null, null, null, null, null);

        assertThat(repository.lastAuditFilter.limit())
                .as("必须向下游多要一条").isEqualTo(6);
        assertThat(page.items()).hasSize(5);
        assertThat(page.nextCursor()).isNotNull();
    }

    /** 最后一页不给游标，否则调用方永远翻不到头。 */
    @Test
    void omitsTheCursorOnTheLastPage() {
        repository.auditRows = rows(3);

        CursorPage<AuditLogEntry> page = service.listAuditLogs(
                admin(), 5, null, null, null, null, null, null);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isNull();
    }

    /** 恰好一页时同样不给游标——边界上多给一个游标会换来一次空翻页。 */
    @Test
    void omitsTheCursorWhenTheRowsExactlyFillOnePage() {
        repository.auditRows = rows(5);

        assertThat(service.listAuditLogs(admin(), 5, null, null, null, null, null, null)
                .nextCursor()).isNull();
    }

    /**
     * 游标锚在<strong>返回给调用方的最后一行</strong>上，不是多取的那一行。
     *
     * <p>锚错一行的表现是下一页少一条——安静地丢数据，而且只在翻页时发生。
     */
    @Test
    void anchorsTheCursorOnTheLastReturnedRowNotTheExtraOne() {
        repository.auditRows = rows(6);

        CursorPage<AuditLogEntry> page = service.listAuditLogs(
                admin(), 5, null, null, null, null, null, null);

        PageCursor decoded = PageCursor.decode(page.nextCursor());
        assertThat(decoded).isNotNull();
        assertThat(decoded.id()).isEqualTo("event-5");
    }

    @Test
    void passesTheDecodedCursorDownToTheRepository() {
        repository.auditRows = rows(1);
        LocalDateTime anchor = LocalDateTime.of(2026, 9, 8, 12, 0, 0);
        String cursor = new PageCursor(anchor, "event-9").encode();

        service.listAuditLogs(admin(), 5, cursor, null, null, null, null, null);

        assertThat(repository.lastAuditFilter.cursorCreatedAt()).isEqualTo(anchor);
        assertThat(repository.lastAuditFilter.cursorId()).isEqualTo("event-9");
    }

    /**
     * 坏游标从头开始，而不是报错。
     *
     * <p>游标会因为服务端换了排序键而失效；那时用户手里的旧游标
     * 不该表现为一个错误页面。
     */
    @Test
    void treatsAMalformedCursorAsTheFirstPage() {
        repository.auditRows = rows(1);

        service.listAuditLogs(admin(), 5, "not-a-cursor", null, null, null, null, null);

        assertThat(repository.lastAuditFilter.cursorCreatedAt()).isNull();
        assertThat(repository.lastAuditFilter.cursorId()).isNull();
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private static String rejectionOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
        throw new AssertionError("预期被拒绝，但调用成功了");
    }

    private static CurrentUser admin() {
        return userWithRole("ADMIN");
    }

    private static CurrentUser userWithRole(String roleCode) {
        return new CurrentUser("u-1", "u", "U", roleCode, null, TenantScope.local("u-1"));
    }

    private static List<AuditLogEntry> rows(int count) {
        LocalDateTime base = LocalDateTime.of(2026, 9, 8, 12, 0, 0);
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new AuditLogEntry(
                        "event-" + index, "actor", "张三", "tenderforge", "对象",
                        "AUTH_LOGIN", "USER", "obj", "SUCCESS", "详情",
                        null, "org-1", "ws-1", "trace", "127.0.0.1",
                        base.minusMinutes(index)))
                .toList();
    }

    private static final class RecordingRepository implements AdminRepository {
        private AdminRepository.UserFilter lastUserFilter;
        private AdminRepository.AuditFilter lastAuditFilter;
        private List<AuditLogEntry> auditRows = new ArrayList<>();

        @Override
        public List<ManagedUser> listUsers(UserFilter filter) {
            lastUserFilter = filter;
            return List.of();
        }

        @Override
        public Optional<ManagedUser> findUserById(String userId) {
            return Optional.empty();
        }

        @Override
        public List<AuditLogEntry> listAuditLogs(AuditFilter filter) {
            lastAuditFilter = filter;
            // 真实仓储按 limit 截断；替身必须照做，否则「多取一条」的断言测不到东西。
            return auditRows.stream().limit(filter.limit()).toList();
        }
    }
}
