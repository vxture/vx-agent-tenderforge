// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.support;

/**
 * 跨平面同义的拒绝码（产品接入通则 X-1）。
 *
 * <p>这四个码在 Atlas、Runos、平台管理面上指的是同一件事，所以调用方匹配一个常量即可，
 * 不必为每个被调方维护一条分支。<strong>照抄，不要自造拼写</strong>——
 * 同一件事曾经有 {@code quota_exceeded} / {@code QUOTA_EXCEEDED} / {@code quota_exhausted}
 * 三种写法，收敛的代价由每个消费方分摊。
 *
 * <p>{@code RATE_LIMITED} 不在通则的四码之内，是各平面已经实现的舰队约定：
 * 它是唯一会自行清除的拒绝，因此也是唯一 {@code retryable=true} 的一条，
 * 不能和商业配额上限混为一谈。
 */
public final class RejectionCodes {

    /** 无授权。运营动作，不是重试能解决的。 */
    public static final String NOT_ENTITLED = "NOT_ENTITLED";

    /** 策略拒绝：操作风险等级超出授权范围。不可自行绕过。 */
    public static final String POLICY_DENIED = "POLICY_DENIED";

    /** 需要人工审批。这是一条出路，不是一个错误——引导去申请，别当失败展示。 */
    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";

    /** 配额耗尽。累计不滚存，只有运营能重置。 */
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";

    /** 容量闸门，会自行清除。唯一可重试的一条。 */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private RejectionCodes() {
    }
}
