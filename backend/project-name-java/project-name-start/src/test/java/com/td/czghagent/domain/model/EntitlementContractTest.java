// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2 权益的判定规则。
 *
 * <p>这一组保护的每一条，错了都不会报错，只会把钱和权限的关系弄反：
 * 门控公式放宽一点，没付钱的人也能用；收紧一点，付了钱的人用不了。
 * 两种都不会抛异常，也都不会出现在任何日志里。
 */
class EntitlementContractTest {

    // ── 门控公式 ────────────────────────────────────────────────────────────

    /**
     * 界面门控只看 tier；数据面门控多认捆绑覆盖。
     *
     * <p>两者刻意不同：被捆绑覆盖的工作空间可以被别的产品调用本产品的数据能力，
     * 但它自己不该在客户目录里看到本产品的完整界面。
     */
    @Test
    void separatesTheSurfaceGateFromTheDataPlaneGate() {
        Entitlement bundledOnly = entitlement(null, true);

        assertThat(bundledOnly.allowsProductSurface())
                .as("只被捆绑覆盖不等于买了本产品").isFalse();
        assertThat(bundledOnly.allowsDataPlane())
                .as("但它的数据能力可以被覆盖它的那个产品调用").isTrue();
    }

    @Test
    void noSubscriptionMeansNeitherGateOpens() {
        Entitlement none = Entitlement.none("ws-1", "tenderforge");

        assertThat(none.allowsProductSurface()).isFalse();
        assertThat(none.allowsDataPlane()).isFalse();
        assertThat(none.status())
                .as("null 不是一个状态值，是「从未订阅」").isNull();
    }

    @Test
    void anyTierOpensBothGates() {
        Entitlement paid = entitlement("free", false);

        assertThat(paid.allowsProductSurface()).isTrue();
        assertThat(paid.allowsDataPlane()).isTrue();
    }

    // ── -1 是无限制 ─────────────────────────────────────────────────────────

    /**
     * {@code -1} 是哨兵，<strong>绝不当数字比较</strong>。
     *
     * <p>拿它去比大小会得到「上限为负」——于是一个本该无限制的用户
     * 连第一次操作都做不了，而错误信息会说「已达上限」。
     */
    @Test
    void treatsMinusOneAsUnlimitedRatherThanANegativeCeiling() {
        Entitlement entitlement = new Entitlement(
                "ws-1", "tenderforge", "active", null, null, false, null,
                "pro", false, Map.of("asset.library_items", -1L), List.of());

        assertThat(entitlement.isUnlimited("asset.library_items", 10)).isTrue();
        assertThat(entitlement.limitOf("asset.library_items", 10))
                .isEqualTo(Entitlement.UNLIMITED);
    }

    /** 平台配置的销售数字永远压过产品默认值；默认值只为「发布当天就能用」存在。 */
    @Test
    void letsThePlatformNumberOverrideTheProductDefault() {
        Entitlement entitlement = new Entitlement(
                "ws-1", "tenderforge", "active", null, null, false, null,
                "pro", false, Map.of("bid.concurrent_generations", 9L), List.of());

        assertThat(entitlement.limitOf("bid.concurrent_generations", 3)).isEqualTo(9);
        assertThat(entitlement.limitOf("some.other.limit", 3))
                .as("平台没配的走产品默认值").isEqualTo(3);
    }

    // ── 能力矩阵 ────────────────────────────────────────────────────────────

    /** 没有 tier 就没有能力，即使被捆绑覆盖——那条区别由数据面门控表达。 */
    @Test
    void grantsNoCapabilityWithoutATier() {
        assertThat(BidCapability.of(entitlement(null, true))).isEmpty();
        assertThat(BidCapability.of(Entitlement.none("ws-1", "tenderforge"))).isEmpty();
        assertThat(BidCapability.of(null))
                .as("连信封都没有时更要 fail-closed").isEmpty();
    }

    /**
     * 平台五档<strong>逐个</strong>被认得，且当前五行内容相同。
     *
     * <p>「上架四个套餐」与「认全五个档位」是两件事。商务上卖四个，不等于平台
     * 不会下发第五个；它一旦发来而表里没有，就会掉进未知档——一个付费最高的
     * 客户拿到最低的能力，而且不报错。
     *
     * <p>这条断言逐档写死而不是遍历 {@code knownTiers()}：遍历会让「表里少了一档」
     * 与「测试也少了一档」同时发生而依然全绿——测试跟着被测对象一起错，是这类
     * 映射表最典型的失效方式。
     */
    @Test
    void recognisesAllFivePlatformTiers() {
        for (String tier : new String[] {"free", "starter", "pro", "business", "enterprise"}) {
            assertThat(BidCapability.of(entitlement(tier, false)))
                    .as("档位 %s 应当拿到全量能力", tier)
                    .containsExactlyInAnyOrder(BidCapability.values());
            assertThat(BidCapability.isKnownTier(tier))
                    .as("档位 %s 必须被认得", tier).isTrue();
        }
        assertThat(BidCapability.knownTiers())
                .as("平台值域是五个，少一个就有人会掉进未知档").hasSize(5);
    }

    /**
     * 未知档位 <strong>fail-closed 到空集</strong>，不是落到某一档上。
     *
     * <p>改之前这里是「未知落在付费集上」——注释写着「未知即降级」，实现却是
     * 未知即全量。那条路径正是平台加一档、或云端配置写错一个字母会走到的。
     *
     * <p>不落到「最低档」是因为五档当前内容相同，落到最低档等于又给全量。
     * 空集会让界面立刻显形：产品与平台的档位表已经不同步，此时继续放行任何能力
     * 都是在猜。
     */
    @Test
    void failsClosedOnAnUnknownTier() {
        assertThat(BidCapability.of(entitlement("platinum", false)))
                .as("没见过的档位不该拿到任何能力").isEmpty();
        assertThat(BidCapability.isKnownTier("platinum")).isFalse();
        assertThat(BidCapability.isKnownTier("enterprise-plus")).isFalse();
        assertThat(BidCapability.isKnownTier(null)).isFalse();
    }

    /**
     * 大小写与首尾空白归一，<strong>但不做别名映射</strong>。
     *
     * <p>云端配置里多打一个空格、或写成 {@code Pro}，不该让一个付费客户掉进未知档。
     * 而把 {@code professional} 当成 {@code pro} 是另一回事——那等于产品替平台
     * 定义值域，而值域不归产品。写错的档位应当显形，不该被产品猜对。
     */
    @Test
    void normalisesCaseAndBlanksButDoesNotInventAliases() {
        assertThat(BidCapability.of(entitlement("  Pro ", false)))
                .as("大小写与空白不该把付费客户挡在外面")
                .containsExactlyInAnyOrder(BidCapability.values());
        assertThat(BidCapability.isKnownTier("BUSINESS")).isTrue();

        assertThat(BidCapability.of(entitlement("professional", false)))
                .as("别名不猜：值域归平台").isEmpty();
        assertThat(BidCapability.isKnownTier("professional")).isFalse();
    }

    // ── 转化深链 ────────────────────────────────────────────────────────────

    /**
     * 意图由订阅状态决定，而 {@code null} 是「从未订阅」。
     *
     * <p>混了就会永远显示错的行动号召：对一个从没买过的人说「续费」，
     * 或者对刚过期的人说「开始订阅」——两者都会把他带到一个不存在的页面。
     */
    @Test
    void picksTheIntentFromWhetherTheyEverSubscribed() {
        assertThat(SubscribeDeeplink.intentFor(Entitlement.none("ws-1", "p")))
                .isEqualTo(SubscribeDeeplink.Intent.SUBSCRIBE);
        assertThat(SubscribeDeeplink.intentFor(statusOnly("expired")))
                .isEqualTo(SubscribeDeeplink.Intent.RENEW);
        assertThat(SubscribeDeeplink.intentFor(statusOnly("cancelled")))
                .isEqualTo(SubscribeDeeplink.Intent.RENEW);
        assertThat(SubscribeDeeplink.intentFor(entitlement("free", false)))
                .as("已经在用的人点进来是想升级").isEqualTo(SubscribeDeeplink.Intent.UPGRADE);
    }

    /** 没见过的状态保守成首购——它至少会把人带到一个能看懂的页面。 */
    @Test
    void degradesAnUnknownStatusToTheSubscribeIntent() {
        assertThat(SubscribeDeeplink.intentFor(statusOnly("some_future_status")))
                .isEqualTo(SubscribeDeeplink.Intent.SUBSCRIBE);
    }

    /**
     * 深链<strong>不带 workspace_id</strong>——console 从会话解析。
     *
     * <p>带上它等于让一个可被伪造的查询参数决定给谁开通。
     */
    @Test
    void neverPutsTheWorkspaceIntoTheConversionLink() {
        String url = SubscribeDeeplink.of(
                "https://console.vxture.com/", "tenderforge",
                SubscribeDeeplink.Intent.SUBSCRIBE);

        assertThat(url)
                .isEqualTo("https://console.vxture.com/subscribe?product=tenderforge&intent=subscribe");
        assertThat(url).doesNotContain("workspace");
    }

    private static Entitlement entitlement(String tier, boolean bundled) {
        return new Entitlement("ws-1", "tenderforge", tier == null ? null : "active",
                null, null, false, null, tier, bundled, Map.of(), List.of());
    }

    private static Entitlement statusOnly(String status) {
        return new Entitlement("ws-1", "tenderforge", status,
                null, null, false, null, null, false, Map.of(), List.of());
    }
}
