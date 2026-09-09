// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

/**
 * 一条待上报的用量事件。
 *
 * @param workspaceId    计量归属的工作空间——平台按它出账
 * @param metric         平台登记表里的指标键
 * @param amount         正整数；0 或负数是调用方的 bug，不是「不计量」的表达方式
 * @param idempotencyKey 强制；重放即无操作
 * @param endUserId      可选的用户归属。平台按工作空间出账，这个字段只让
 *                       工作空间管理员看得到「谁用掉的」
 * @param taskId         可选的跨产品聚合键（X-2），有就带上
 */
public record UsageEvent(
        String workspaceId,
        UsageMetric metric,
        long amount,
        String idempotencyKey,
        String endUserId,
        String taskId) {

    public static UsageEvent of(TenantScope tenant, UsageMetric metric,
                                String entityId, String endUserId) {
        return new UsageEvent(
                tenant == null ? null : tenant.workspaceId(),
                metric, 1L, metric.idempotencyKeyFor(entityId),
                endUserId, TaskContext.current());
    }
}
