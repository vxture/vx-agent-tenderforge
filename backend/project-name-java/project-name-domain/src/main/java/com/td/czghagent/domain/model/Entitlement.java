// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

import java.util.List;
import java.util.Map;

/**
 * C2 权益信封（产品接入通则 §C2，信封 v3）。
 *
 * <p><strong>三条禁令</strong>，每一条违反了都不会报错：
 * <ol>
 *   <li><b>不落产品库。</b>落了就有第二份真相，而失效链管不到它——
 *       用户升了档，产品这边还按旧档服务，界面上看不出任何异常。</li>
 *   <li><b>不做商业推断。</b>能不能试用、该买什么、什么价——信封里没有，也不会加。
 *       产品渲染通用入口 + 深链，决策 UI 归 console。</li>
 *   <li><b>容忍未知。</b>新增字段与 {@code status} 的新枚举值必须能容忍，
 *       未知即保守渲染。做不到的表现是：平台加一档，产品这边整个挂掉。</li>
 * </ol>
 */
public record Entitlement(
        String workspaceId,
        String product,
        /**
         * 订阅状态。<strong>{@code null} 不是一个状态值，是「从未订阅」。</strong>
         *
         * <p>用它区分首购与失效：混了就会永远显示错的行动号召——
         * 对一个从没买过的人说「续费」，或者对一个刚过期的人说「开始试用」。
         */
        String status,
        String trialEndsAt,
        String currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        /** 仅 {@code status=expired} 时有值。这是<strong>承诺下限</strong>，晚删不违约。 */
        String dataRetentionUntil,
        /** 五档商业阶梯，取最高。{@code null} = 没有生效的直接购买。 */
        String tier,
        /** 是否被任一生效的捆绑组件覆盖。<strong>独立轴</strong>，与 tier 并存。 */
        boolean bundled,
        /** 上限型销售数字，取最大值。<strong>{@code -1} = 无限制</strong>。 */
        Map<String, Long> limits,
        List<QuotaPool> quotaPools
) {

    /** 无限制哨兵。<strong>绝不当数字比较</strong>——那会得到「上限为负」。 */
    public static final long UNLIMITED = -1L;

    public Entitlement {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
        quotaPools = quotaPools == null ? List.of() : List.copyOf(quotaPools);
    }

    /**
     * 没有订阅时的信封。
     *
     * <p>解析失败也用它——<strong>fail-closed</strong>。把解析失败当成「暂时放行」
     * 的后果是：平台不可达的那几分钟里，所有人都拿到了完整权限。
     */
    public static Entitlement none(String workspaceId, String product) {
        return new Entitlement(workspaceId, product, null, null, null, false, null,
                null, false, Map.of(), List.of());
    }

    /**
     * 界面门控：这个工作空间能不能用本产品的界面。
     *
     * <p>公式来自通则，<strong>本地不得放宽</strong>。写成一个方法而不是让每个调用点
     * 各写一次 {@code tier != null}，是因为放宽只需要有人在某一处多写一个
     * {@code || bundled}，而那一处不会有人再去读。
     */
    public boolean allowsProductSurface() {
        return tier != null;
    }

    /**
     * 数据面门控：比界面门控多认捆绑覆盖。
     *
     * <p>两者刻意不同：被捆绑覆盖的工作空间可以被别的产品调用本产品的数据能力，
     * 但它自己不该在客户目录里看到本产品的完整界面。
     */
    public boolean allowsDataPlane() {
        return tier != null || bundled;
    }

    /**
     * 读一个上限。
     *
     * <p>平台配置的销售数字<strong>永远压过</strong>产品默认值；默认值只为
     * 「发布当天就能用」存在。返回 {@link #UNLIMITED} 时调用方必须走无限制分支，
     * 而不是拿它去比大小。
     */
    public long limitOf(String key, long productDefault) {
        Long configured = limits.get(key);
        return configured == null ? productDefault : configured;
    }

    /** 某个上限是否为无限制。提供它就是为了让调用点不必自己写 {@code == -1}。 */
    public boolean isUnlimited(String key, long productDefault) {
        return limitOf(key, productDefault) == UNLIMITED;
    }
}
