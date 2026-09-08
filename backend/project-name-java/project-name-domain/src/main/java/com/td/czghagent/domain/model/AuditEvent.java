// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

/**
 * 一条审计事件，字段名取自《产品接入通则》X-3 的最小字段集。
 *
 * <p>名字不是本仓的偏好，是跨产品对账的前提：三个产品各写各的字段名，
 * 「都合规」与「能一起查」就不是一回事了。
 *
 * <p>做成 record 而不是继续用十二个位置参数，是因为位置参数在第八个之后
 * 就只能靠数——而这一组里有五个连续的 String，错位不会编译失败，
 * 只会让审计表里的「对象类型」列装着结果码。
 *
 * <p>{@code taskId} 由 {@link TaskContext} 自动带入：它必须穿过整条调用链，
 * 让每个写审计的地方都显式传一次，等于给每处都留一次遗漏的机会。
 */
public record AuditEvent(
        String actorId,
        String actorConsole,
        String action,
        String objectType,
        String objectId,
        String outcome,
        String detailSummary,
        String taskId,
        String orgId,
        String workspaceId,
        String traceId,
        String ipAddress
) {

    /** 结果必须区分成功与被拒（X-3）——只记成功的审计流回答不了「谁被挡住了」。 */
    public static final String SUCCESS = "SUCCESS";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    /**
     * 用户在本产品界面上发起的动作。
     *
     * <p>{@code actorConsole} 填本产品码：本方自产的写填进程常量，
     * 这是 X-3 对「不经由外部控制台的写」的规定。
     */
    public static AuditEvent byUser(OperationContext context, String action,
                                    String objectType, String objectId,
                                    String outcome, String detailSummary) {
        CurrentUser user = context.user();
        TenantScope tenant = user == null ? null : user.tenant();
        return new AuditEvent(
                user == null ? null : user.id(),
                ProductIdentity.PRODUCT_CODE,
                action, objectType, objectId, outcome, detailSummary,
                TaskContext.current(),
                tenant == null ? null : tenant.orgId(),
                tenant == null ? null : tenant.workspaceId(),
                context.traceId(), context.ipAddress()
        );
    }

    /**
     * 后台通道发起的动作（Temporal 活动、定时任务）。
     *
     * <p>{@code actorConsole} 为 {@code null}，而不是编一个控制台名：
     * 通则明确 MUST NOT 硬编——一个编出来的名字会让按控制台筛查的审计员
     * 收到一批根本不是从那里发起的动作，且他没有办法发现这一点。
     * {@code actorId} 仍记归属人，因为这条动作确实是为他跑的。
     */
    public static AuditEvent bySystem(String actorId, TenantScope tenant, String action,
                                      String objectType, String objectId,
                                      String outcome, String detailSummary,
                                      String traceId) {
        return new AuditEvent(
                actorId, null, action, objectType, objectId, outcome, detailSummary,
                TaskContext.current(),
                tenant == null ? null : tenant.orgId(),
                tenant == null ? null : tenant.workspaceId(),
                traceId, "internal"
        );
    }
}
