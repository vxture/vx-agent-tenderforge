// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.model.QuotaPool;
import com.td.czghagent.domain.port.EntitlementResolver;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地开发用的权益替身。
 *
 * <p>存在的理由只有一个：<strong>整个界面要能在空 .env 下被探索</strong>。
 * 它不是「没配好时的兜底」——部署态由阶段守卫拒绝它启动。
 *
 * <p>档位与状态可由 {@code MOCK_TIER} / {@code MOCK_STATUS} / {@code MOCK_BUNDLED}
 * 驱动，这样本地能把「免费档看到什么」「过期后看到什么」逐个走一遍。
 * 这是替身真正值钱的地方——不是让界面不报错，而是让那些只在特定档位下
 * 才出现的分支<strong>可被走到</strong>；否则它们要等到线上遇见第一个免费用户
 * 才第一次被执行。
 */
public class MockEntitlementResolver implements EntitlementResolver {

    /**
     * 已失效的订阅状态。
     *
     * <p>这几个状态下平台<strong>不会</strong>再给 tier——失效了就没有「生效的直接购买」。
     * 替身必须照做：曾经有一版发出 {@code tier=pro} + {@code status=expired} 的组合，
     * 于是 {@code MOCK_STATUS=expired} 走到的是「仍然有权限」，
     * <strong>根本没走到过期分支</strong>。一个编造出不可能信封的替身，
     * 比没有替身更坏——它让人以为自己验过了。
     */
    private static final Set<String> LAPSED =
            Set.of("expired", "cancelled", "suspended");

    private final String tier;
    private final String status;
    private final boolean bundled;

    public MockEntitlementResolver(String tier, String status, boolean bundled) {
        this.tier = tier == null || tier.isBlank() ? "pro" : tier.trim();
        this.status = status == null || status.isBlank() ? "active" : status.trim();
        this.bundled = bundled;
    }

    @Override
    public Entitlement resolve(String workspaceId) {
        // "none" 用来在本地走一遍「未订阅」分支——那条分支在真实环境里很难复现，
        // 却是每个新用户看到的第一屏。
        if ("none".equalsIgnoreCase(tier)) {
            return Entitlement.none(workspaceId, ProductIdentity.PRODUCT_CODE);
        }
        if (LAPSED.contains(status.toLowerCase(Locale.ROOT))) {
            // 失效：保留 status（界面据此渲染「续费」而不是「首购」），但没有 tier。
            return new Entitlement(
                    workspaceId, ProductIdentity.PRODUCT_CODE, status,
                    null, null, false, "2026-12-31T00:00:00Z",
                    null, false, Map.of(), List.of());
        }
        return new Entitlement(
                workspaceId, ProductIdentity.PRODUCT_CODE,
                status, null, null, false, null,
                tier, bundled,
                Map.of("bid.concurrent_generations", 3L, "asset.library_items", -1L),
                List.of(new QuotaPool("tenderforge.bid.generations", 100, 100, 0)));
    }

    @Override
    public void invalidate(String workspaceId) {
        // 替身没有缓存可以失效——它每次现算。
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
