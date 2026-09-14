// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.repository.AdminRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class JdbcAdminRepository implements AdminRepository {

    private static final RowMapper<AuditLogEntry> AUDIT_MAPPER = (resultSet, rowNumber) ->
            new AuditLogEntry(
                    resultSet.getString("event_id"), resultSet.getString("actor_id"),
                    resultSet.getString("actor_name"), resultSet.getString("actor_console"),
                    resultSet.getString("object_name"),
                    resultSet.getString("action"), resultSet.getString("object_type"),
                    resultSet.getString("object_id"), resultSet.getString("outcome"),
                    resultSet.getString("detail_summary"), resultSet.getString("task_id"),
                    resultSet.getString("org_id"), resultSet.getString("workspace_id"),
                    resultSet.getString("trace_id"), resultSet.getString("ip_address"),
                    JdbcTimes.localDateTime(resultSet, "occurred_at")
            );

    // app_user 的两处 LEFT JOIN 只为给历史本地账号的审计行解析出操作者与对象名。
    // 本地账号体系已退役、表保留；平台身份的 actor_id 是 subject，join 不中，展示回落到原始值。
    //
    // **id 必须显式 ::text。** audit_log 的 actor_id / object_id 是 VARCHAR（它们要装平台
    // subject），而 app_user.id 与 bid_document.id 是 UUID——PostgreSQL 没有 uuid = varchar
    // 运算符，这条查询连计划都生成不出来，审计页对每个管理员都是 500。迁到 PostgreSQL 起
    // 就是坏的，因为唯一覆盖它的测试用的是替身仓储；现在由
    // LocalAccountSurfacesRetiredIntegrationTest 以真实库验。转换放在 UUID 一侧：反过来
    // 把 actor_id 转成 uuid，遇到非 UUID 的平台 subject 会直接报错。
    private static final String AUDIT_JOINS = """
            FROM audit_log a
            LEFT JOIN app_user u ON u.id::text = a.actor_id
            LEFT JOIN bid_document b ON a.object_type = 'BID' AND b.id::text = a.object_id
            LEFT JOIN app_user target_user ON a.object_type = 'USER' AND target_user.id::text = a.object_id
            """;

    private static final String AUDIT_SELECT = """
            SELECT a.*, u.username AS actor_name,
                   COALESCE(b.title, target_user.display_name, a.object_id) AS object_name
            """ + AUDIT_JOINS;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 键集游标翻页。
     *
     * <p>游标谓词写成 {@code (occurred_at, event_id) < (?, ?)} 的展开形式而不是 MySQL 的行值比较，
     * 是为了让优化器用得上 {@code (occurred_at, event_id)} 上的索引——行值比较在 MySQL 8 上
     * 对复合索引的利用并不稳定，而这条查询正是靠索引才避免深翻页扫描的。
     */
    @Override
    public List<AuditLogEntry> listAuditLogs(AuditFilter filter) {
        QueryParts where = auditWhere(filter);
        StringBuilder sql = new StringBuilder(AUDIT_SELECT).append(where.sql());
        List<Object> parameters = new ArrayList<>(where.parameters());
        if (filter.cursorCreatedAt() != null && filter.cursorId() != null) {
            sql.append(" AND (a.occurred_at < ? OR (a.occurred_at = ? AND a.event_id < ?))");
            parameters.add(filter.cursorCreatedAt());
            parameters.add(filter.cursorCreatedAt());
            parameters.add(filter.cursorId());
        }
        sql.append(" ORDER BY a.occurred_at DESC, a.event_id DESC LIMIT ?");
        parameters.add(filter.limit());
        return jdbcTemplate.query(sql.toString(), AUDIT_MAPPER, parameters.toArray());
    }

    private QueryParts auditWhere(AuditFilter filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(filter.keyword())) {
            sql.append(" AND (LOWER(COALESCE(u.username, '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(b.title, target_user.display_name, a.object_id, '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(a.detail_summary, '')) LIKE ?")
                    .append(" OR LOWER(a.trace_id) LIKE ?)");
            String keyword = like(filter.keyword());
            for (int index = 0; index < 4; index++) {
                parameters.add(keyword);
            }
        }
        appendEquals(sql, parameters, "a.action", filter.actionCode());
        appendEquals(sql, parameters, "a.outcome", filter.resultCode());
        appendTime(sql, parameters, "a.occurred_at >= ?", filter.startAt());
        appendTime(sql, parameters, "a.occurred_at <= ?", filter.endAt());
        return new QueryParts(sql.toString(), parameters);
    }

    private static void appendEquals(StringBuilder sql, List<Object> parameters,
                                     String column, String value) {
        if (hasText(value)) {
            sql.append(" AND ").append(column).append(" = ?");
            parameters.add(value.trim());
        }
    }

    private static void appendTime(StringBuilder sql, List<Object> parameters,
                                   String condition, LocalDateTime value) {
        if (value != null) {
            sql.append(" AND ").append(condition);
            parameters.add(value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String like(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private record QueryParts(String sql, List<Object> parameters) {
    }
}
