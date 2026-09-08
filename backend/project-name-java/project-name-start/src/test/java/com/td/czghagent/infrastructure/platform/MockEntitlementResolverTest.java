// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.BidCapability;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.SubscribeDeeplink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 替身发出的信封必须是平台<strong>可能</strong>发出的。
 *
 * <p>给替身写测试听起来像是在测试测试工具，但这一组防的是一件真实发生过的事：
 * 替身曾经发出 {@code tier=pro} + {@code status=expired}，
 * 于是 {@code MOCK_STATUS=expired} 走到的其实是「仍然有权限」那条路。
 * 那时界面看起来是对的，本地也「验过了」——只是验的是一个平台永远不会送来的信封。
 *
 * <p>替身的价值全部来自它的<strong>可信度</strong>。一个会编造不可能状态的替身，
 * 比没有替身更坏。
 */
class MockEntitlementResolverTest {

    /**
     * 失效即无档位——这是替身与平台之间唯一的语义约定。
     *
     * <p>「过期」不是「有 pro 档但过期了」，而是「没有生效的直接购买，
     * 且此前有过」。前者会让门控继续放行，后者才是用户真实看到的东西。
     */
    @ParameterizedTest
    @ValueSource(strings = {"expired", "cancelled", "suspended", "EXPIRED"})
    void neverKeepsATierOnALapsedSubscription(String status) {
        Entitlement entitlement =
                new MockEntitlementResolver("pro", status, false).resolve("ws-1");

        assertThat(entitlement.tier())
                .as("失效了就没有生效的直接购买，即使 MOCK_TIER 写着 pro").isNull();
        assertThat(entitlement.allowsProductSurface()).isFalse();
        assertThat(entitlement.allowsDataPlane()).isFalse();
        assertThat(BidCapability.of(entitlement)).isEmpty();
    }

    /**
     * 失效保留 status，未订阅不保留——两条路要通向不同的行动号召。
     *
     * <p>把失效折成「未订阅」会对一个付过钱的人说「开始订阅」；
     * 反过来会对一个新用户说「续费」。都不会报错，都会把人带错地方。
     */
    @Test
    void keepsLapsedApartFromNeverSubscribed() {
        Entitlement lapsed = new MockEntitlementResolver("pro", "expired", false).resolve("ws-1");
        Entitlement never = new MockEntitlementResolver("none", "active", false).resolve("ws-1");

        assertThat(SubscribeDeeplink.intentFor(lapsed))
                .isEqualTo(SubscribeDeeplink.Intent.RENEW);
        assertThat(SubscribeDeeplink.intentFor(never))
                .isEqualTo(SubscribeDeeplink.Intent.SUBSCRIBE);
        assertThat(never.status())
                .as("从未订阅没有状态可言").isNull();
    }

    /**
     * 失效后数据仍在保留期内。
     *
     * <p>这条让本地能走到「你的数据还在，续费即可恢复」那一屏——
     * 一个只在过期后出现、因而最容易带着 bug 上线的界面。
     */
    @Test
    void stillReportsARetentionDeadlineAfterLapsing() {
        Entitlement lapsed = new MockEntitlementResolver("pro", "expired", false).resolve("ws-1");

        assertThat(lapsed.dataRetentionUntil()).isNotNull();
    }

    @Test
    void drivesTheTierAndBundlingFromConfiguration() {
        Entitlement free = new MockEntitlementResolver("free", "active", false).resolve("ws-1");
        assertThat(free.tier()).isEqualTo("free");
        assertThat(BidCapability.of(free))
                .doesNotContain(BidCapability.CONSISTENCY_REVIEW);

        Entitlement bundled = new MockEntitlementResolver("", "", true).resolve("ws-1");
        assertThat(bundled.tier()).as("空值落在 pro 上，好让空 .env 能看到完整界面")
                .isEqualTo("pro");
        assertThat(bundled.bundled()).isTrue();
    }

    /**
     * 替身必须<strong>自报家门</strong>。
     *
     * <p>{@code /api/entitlement} 据此发出 {@code degraded: true}，
     * 而阶段守卫据此拒绝它在部署态启动。一个安静的替身可以一路混到生产，
     * 在那里给每个人发 pro 档。
     */
    @Test
    void admitsToBeingAMock() {
        assertThat(new MockEntitlementResolver("pro", "active", false).isMock()).isTrue();
    }
}
