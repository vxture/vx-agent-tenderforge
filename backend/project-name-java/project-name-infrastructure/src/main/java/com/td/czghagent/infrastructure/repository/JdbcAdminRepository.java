// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.repository.AdminRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class JdbcAdminRepository implements AdminRepository {

    private static final RowMapper<ManagedUser> USER_MAPPER = (resultSet, rowNumber) -> new ManagedUser(
            resultSet.getString("id"), resultSet.getString("username"),
            resultSet.getString("display_name"), resultSet.getString("role_code"),
            resultSet.getString("avatar_url"), resultSet.getBoolean("enabled"),
            JdbcTimes.localDateTime(resultSet, "created_at"),
            JdbcTimes.localDateTime(resultSet, "updated_at"), resultSet.getLong("revision")
    );

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

    private static final String AUDIT_JOINS = """
            FROM audit_log a
            LEFT JOIN app_user u ON u.id = a.actor_id
            LEFT JOIN bid_document b ON a.object_type = 'BID' AND b.id = a.object_id
            LEFT JOIN app_user target_user ON a.object_type = 'USER' AND target_user.id = a.object_id
            """;

    private static final String AUDIT_SELECT = """
            SELECT a.*, u.username AS actor_name,
                   COALESCE(b.title, target_user.display_name, a.object_id) AS object_name
            """ + AUDIT_JOINS;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ManagedUser> listUsers(UserFilter filter) {
        QueryParts where = userWhere(filter);
        List<Object> parameters = new ArrayList<>(where.parameters());
        parameters.add(filter.limit());
        return jdbcTemplate.query(
                "SELECT * FROM app_user" + where.sql()
                        + " ORDER BY created_at DESC, id DESC LIMIT ?",
                USER_MAPPER, parameters.toArray()
        );
    }

    @Override
    public Optional<ManagedUser> findUserById(String userId) {
        return jdbcTemplate.query("SELECT * FROM app_user WHERE id = ?", USER_MAPPER, userId)
                .stream().findFirst();
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

    private QueryParts userWhere(UserFilter filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(filter.keyword())) {
            sql.append(" AND (LOWER(username) LIKE ? OR LOWER(display_name) LIKE ?)");
            String keyword = like(filter.keyword());
            parameters.add(keyword);
            parameters.add(keyword);
        }
        appendEquals(sql, parameters, "role_code", filter.roleCode());
        if (filter.enabled() != null) {
            sql.append(" AND enabled = ?");
            parameters.add(filter.enabled());
        }
        return new QueryParts(sql.toString(), parameters);
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
