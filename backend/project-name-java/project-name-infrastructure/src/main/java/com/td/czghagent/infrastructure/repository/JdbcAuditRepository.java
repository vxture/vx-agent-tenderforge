// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.repository.AuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 审计写入：只有 INSERT。
 *
 * <p>这个类里没有 update 也没有 delete，不是还没写——是审计流的更正只能是补偿事件。
 * 列名按《产品接入通则》X-3 的最小字段集，与 Atlas / Runos / 平台管理面对齐，
 * 这样一次 agent 任务跨产品的动作可以按同一组名字拼回来。
 */
@Repository
public class JdbcAuditRepository implements AuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(AuditEvent event) {
        jdbcTemplate.update("""
                        INSERT INTO audit_log(
                            event_id, actor_id, actor_console, action, object_type, object_id,
                            outcome, detail_summary, task_id, org_id, workspace_id,
                            trace_id, ip_address
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(),
                event.actorId(), event.actorConsole(), event.action(),
                event.objectType(), event.objectId(), event.outcome(),
                event.detailSummary(), event.taskId(),
                event.orgId(), event.workspaceId(),
                event.traceId(), event.ipAddress());
    }
}
