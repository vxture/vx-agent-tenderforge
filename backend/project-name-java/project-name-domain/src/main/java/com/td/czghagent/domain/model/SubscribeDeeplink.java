// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.domain.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 转化深链（产品接入通则 C2）。
 *
 * <p>产品内<strong>没有价格、没有商业推断</strong>：能不能试用、该买哪一档、多少钱，
 * 全部归 console。这里只负责把用户带到正确的入口，并说明他来这里想干什么。
 *
 * <p><strong>不带 workspace_id</strong>——console 从会话解析。带上它等于让一个
 * 可被伪造的参数决定给谁开通。
 */
public final class SubscribeDeeplink {

    /**
     * 意图。四个值来自通则，<strong>不自造第五个</strong>：
     * console 对未知 intent 会降级到订阅管理首页并保留 product 上下文，
     * 所以平台新增意图时本产品零改动——前提是我们没有发明自己的。
     */
    public enum Intent {
        SUBSCRIBE, UPGRADE, RENEW, ADDON
    }

    private SubscribeDeeplink() {
    }

    /**
     * 按订阅状态选意图。
     *
     * <p>这是本产品允许做的<strong>唯一一次</strong>状态判断，而且它不是商业推断——
     * 它只回答「这个人是没买过还是买过失效了」，答案直接来自 {@code status}。
     *
     * <p>{@code null} 是「从未订阅」而不是一个状态值：对一个从没买过的人说「续费」，
     * 或者对刚过期的人说「开始订阅」，都是把用户推向一个不存在的页面。
     */
    public static Intent intentFor(Entitlement entitlement) {
        if (entitlement != null && entitlement.allowsProductSurface()) {
            return Intent.UPGRADE;
        }
        String status = entitlement == null ? null : entitlement.status();
        if (status == null) {
            return Intent.SUBSCRIBE;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "expired", "cancelled", "suspended", "overdue" -> Intent.RENEW;
            // 未知状态保守处理成首购：它至少会把人带到一个能看懂的页面。
            default -> Intent.SUBSCRIBE;
        };
    }

    public static String of(String consoleBaseUrl, String productCode, Intent intent) {
        String base = consoleBaseUrl == null ? "" : consoleBaseUrl.replaceAll("/+$", "");
        return base + "/subscribe"
                + "?product=" + encode(productCode)
                + "&intent=" + intent.name().toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
