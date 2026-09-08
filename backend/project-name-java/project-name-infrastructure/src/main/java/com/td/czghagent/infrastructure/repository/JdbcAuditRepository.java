// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.repository.AuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcAuditRepository implements AuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(String userId, String actionCode, String targetType,
                       String targetId, String resultCode, String detailSummary,
                       String traceId, String ipAddress) {
        jdbcTemplate.update("""
                        INSERT INTO audit_log(
                            id, user_id, action_code, target_type, target_id,
                            result_code, detail_summary, trace_id, ip_address
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), userId, actionCode, targetType,
                targetId, resultCode, detailSummary, traceId, ipAddress);
    }
}
