// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.time.LocalDateTime;

/**
 * 审计读取视图，字段名取自《产品接入通则》X-3 的最小字段集。
 *
 * <p>与写入侧的 {@link AuditEvent} 是同一件事的两个方向，字段名必须一致——
 * 读写两套名字会让「按这个名字写进去的东西用另一个名字查」，
 * 而这种错位在跨产品对账时才会暴露，那时已经攒了几个月的数据。
 *
 * <p>{@code actorName} 与 {@code objectName} 是 join 出来的展示字段，不属于 X-3 最小集，
 * 也不参与对账——它们随时可能因为被引用对象改名而变化。
 */
public record AuditLogEntry(
        String eventId,
        String actorId,
        String actorName,
        String actorConsole,
        String objectName,
        String action,
        String objectType,
        String objectId,
        String outcome,
        String detailSummary,
        String taskId,
        String orgId,
        String workspaceId,
        String traceId,
        String ipAddress,
        LocalDateTime occurredAt
) {
}
