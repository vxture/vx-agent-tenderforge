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

    @Test
    void givesTheFreeTierEverythingExceptReview() {
        assertThat(BidCapability.of(entitlement("free", false)))
                .containsExactlyInAnyOrder(
                        BidCapability.BID_AUTHORING, BidCapability.AI_GENERATION,
                        BidCapability.DOCUMENT_EXPORT, BidCapability.ASSET_LIBRARY)
                .doesNotContain(BidCapability.CONSISTENCY_REVIEW);
    }

    /**
     * 未知档位按<strong>已知的最保守方式</strong>处理，并且被标记出来。
     *
     * <p>通则说「未知即降级」。反过来（未知给全权限）意味着平台加一档、
     * 或者有人手滑写错一个字母，就把完整能力发给了不该有的人——而那不会报错。
     *
     * <p>本产品当前只有免费档与付费档两种能力集，所以未知档位落在付费集上；
     * 一旦五档的能力开始分化，这条断言会变成「未知落在最低的那一档」。
     * 无论如何，{@code tierKnown} 必须为 false，让这件事可被看见。
     */
    @Test
    void marksAnUnknownTierAsUnknownEvenWhileServingIt() {
        assertThat(BidCapability.isKnownTier("free")).isTrue();
        assertThat(BidCapability.isKnownTier("FREE"))
                .as("档位比对不该被大小写绊倒").isTrue();
        assertThat(BidCapability.isKnownTier("enterprise-plus")).isFalse();
        assertThat(BidCapability.isKnownTier(null)).isFalse();
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
