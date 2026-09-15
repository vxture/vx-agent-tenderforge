// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

/**
 * 一次开通/停用投递（C3 下发）。
 *
 * @param deliveryId 平台的投递标识，也是幂等键
 * @param type       事件类型。在线词表四种：驱动开通状态机的 {@code tenant.provisioned} /
 *                   {@code tenant.deprovisioned}，与只作提示的通知 {@code subscription_changed} /
 *                   {@code grant.invalidated}。<strong>没见过的类型要记下并放过</strong>，
 *                   而不是报错——平台加一个新事件类型是一次对它完全正常的变更，
 *                   而在这里报错会让重试打到天荒地老
 * @param seq        每 (workspace, product) 单调递增。<strong>只有 {@code tenant.*} 带</strong>；
 *                   通知类事件没有 seq，这里是 0——绝不能拿它去做乱序判定
 * @param workspaceId 事件针对的工作空间
 * @param orgId      所属组织（平台的 {@code tenant_id}）。<strong>可为空</strong>——
 *                   平台不一定在每个事件里都带它，而空值比一个从 workspaceId
 *                   凑出来的假 orgId 好：后者会让按组织筛的审计查询查到不存在的组织
 * @param product    事件针对的产品码；不是本产品的一律不处理
 */
public record ProvisioningEvent(
        String deliveryId,
        String type,
        long seq,
        String workspaceId,
        String orgId,
        String product) {

    /** 租户轴，两段都在时才成立。 */
    public TenantScope tenant() {
        return orgId == null || workspaceId == null
                ? null : new TenantScope(orgId, workspaceId);
    }

    public static final String PROVISIONED = "tenant.provisioned";
    public static final String DEPROVISIONED = "tenant.deprovisioned";
    public static final String SUBSCRIPTION_CHANGED = "subscription_changed";
    public static final String GRANT_INVALIDATED = "grant.invalidated";

    /**
     * 通知类事件：不带 seq、不驱动开通状态机，只提示「你缓存的东西过期了」。
     *
     * <p>它们要在乱序判定之前分流。按 seq=0 去比，任何开通过的空间上都是「过期」，
     * 于是套餐变了、权益缓存却不失效——而那不会报错。
     */
    public boolean isNotification() {
        return SUBSCRIPTION_CHANGED.equals(type) || GRANT_INVALIDATED.equals(type);
    }

    /** 开通状态，单一字符串而不是一对布尔（B-3）。 */
    public static final String STATE_PROVISIONED = "provisioned";
    public static final String STATE_DEPROVISIONED = "deprovisioned";
}
