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
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime(), resultSet.getLong("revision")
    );

    private static final RowMapper<AuditLogEntry> AUDIT_MAPPER = (resultSet, rowNumber) ->
            new AuditLogEntry(
                    resultSet.getString("id"), resultSet.getString("user_id"),
                    resultSet.getString("username"), resultSet.getString("target_name"),
                    resultSet.getString("action_code"), resultSet.getString("target_type"),
                    resultSet.getString("target_id"), resultSet.getString("result_code"),
                    resultSet.getString("detail_summary"), resultSet.getString("trace_id"),
                    resultSet.getString("ip_address"),
                    resultSet.getTimestamp("created_at").toLocalDateTime()
            );

    private static final String AUDIT_JOINS = """
            FROM audit_log a
            LEFT JOIN app_user u ON u.id = a.user_id
            LEFT JOIN bid_document b ON a.target_type = 'BID' AND b.id = a.target_id
            LEFT JOIN app_user target_user ON a.target_type = 'USER' AND target_user.id = a.target_id
            """;

    private static final String AUDIT_SELECT = """
            SELECT a.*, u.username,
                   COALESCE(b.title, target_user.display_name, a.target_id) AS target_name
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
        parameters.add(filter.offset());
        return jdbcTemplate.query(
                "SELECT * FROM app_user" + where.sql()
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                USER_MAPPER, parameters.toArray()
        );
    }

    @Override
    public long countUsers(UserFilter filter) {
        QueryParts where = userWhere(filter);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user" + where.sql(),
                Long.class, where.parameters().toArray()
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ManagedUser> findUserById(String userId) {
        return jdbcTemplate.query("SELECT * FROM app_user WHERE id = ?", USER_MAPPER, userId)
                .stream().findFirst();
    }

    @Override
    public List<AuditLogEntry> listAuditLogs(AuditFilter filter) {
        QueryParts where = auditWhere(filter);
        List<Object> parameters = new ArrayList<>(where.parameters());
        parameters.add(filter.limit());
        parameters.add(filter.offset());
        return jdbcTemplate.query(
                AUDIT_SELECT + where.sql() + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
                AUDIT_MAPPER, parameters.toArray()
        );
    }

    @Override
    public long countAuditLogs(AuditFilter filter) {
        QueryParts where = auditWhere(filter);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + AUDIT_JOINS + where.sql(),
                Long.class, where.parameters().toArray()
        );
        return count == null ? 0 : count;
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
                    .append(" OR LOWER(COALESCE(b.title, target_user.display_name, a.target_id, '')) LIKE ?")
                    .append(" OR LOWER(COALESCE(a.detail_summary, '')) LIKE ?")
                    .append(" OR LOWER(a.trace_id) LIKE ?)");
            String keyword = like(filter.keyword());
            for (int index = 0; index < 4; index++) {
                parameters.add(keyword);
            }
        }
        appendEquals(sql, parameters, "a.action_code", filter.actionCode());
        appendEquals(sql, parameters, "a.result_code", filter.resultCode());
        appendTime(sql, parameters, "a.created_at >= ?", filter.startAt());
        appendTime(sql, parameters, "a.created_at <= ?", filter.endAt());
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
