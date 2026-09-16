// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
package com.td.czghagent.domain.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 转化深链：官网定价页。
 *
 * <p>owner 2026-09-16 裁定：订阅入口是官网定价页
 * {@code {官网}/{语言}/pricing?product={产品码}}——<strong>套餐在那里发布</strong>。
 *
 * <p>此前按通则 C2 拼的是 {@code {CONSOLE_BASE}/subscribe?product=&intent=}，而那不是订阅页面：
 * console 的 subscribe 对目录里没有套餐的产品降级回订阅首页（本产品正是这种情形，
 * {@code /api/products/tenderforge/plans} 返回空），于是「前往订阅」把人带到一个与订阅无关的页面。
 * <strong>通则 C2 的「转化深链」一节需要同步更新</strong>；本仓按 owner 的裁定实现。
 *
 * <p>产品内<strong>没有价格、没有商业推断</strong>：能不能试用、该买哪一档、多少钱，全部归官网与 console。
 * 这里只把用户带到入口，并且只在用户<strong>显式点击</strong>时打开。
 *
 * <p><strong>不带 workspace_id</strong>：开通对象由会话决定，带上它等于让一个可被伪造的参数决定给谁开通。
 * 也<strong>不带 intent</strong>：定价页只认 product，多拼一个参数是拼给自己看的。
 */
public final class PricingDeeplink {

    /** 官网支持的语言。与前端词典、平台 {@code NEXT_LOCALE} cookie 同一个值域。 */
    private static final List<String> SUPPORTED = List.of("zh-CN", "en-US");

    /** 语言缺失或不认得时的落点。 */
    public static final String DEFAULT_LOCALE = "zh-CN";

    /** 平台各产品共用的语言 cookie（前端 write-locale.ts 写的就是它）。 */
    public static final String LOCALE_COOKIE = "NEXT_LOCALE";

    private PricingDeeplink() {
    }

    /**
     * 按请求选语言：先看用户显式选过的 cookie，再看浏览器偏好，都没有就默认中文。
     *
     * <p>不认得的值一律回到默认——官网只有这两种语言，拼一个 {@code /fr-FR/} 出去就是一个 404。
     */
    public static String localeFrom(String cookieValue, String acceptLanguage) {
        String chosen = supported(cookieValue);
        if (chosen != null) {
            return chosen;
        }
        if (acceptLanguage != null) {
            for (String candidate : acceptLanguage.split(",")) {
                String tag = candidate.split(";")[0].trim();
                String exact = supported(tag);
                if (exact != null) {
                    return exact;
                }
                // zh、zh-HK、en-GB 这类：按主语言归到官网有的那一个，而不是直接放弃。
                String primary = tag.split("-")[0];
                for (String locale : SUPPORTED) {
                    if (locale.split("-")[0].equalsIgnoreCase(primary)) {
                        return locale;
                    }
                }
            }
        }
        return DEFAULT_LOCALE;
    }

    /** {@code {官网}/{语言}/pricing?product={产品码}}。语言不认得时用默认，不拼出一个 404。 */
    public static String of(String websiteBaseUrl, String productCode, String locale) {
        String base = websiteBaseUrl == null ? "" : websiteBaseUrl.replaceAll("/+$", "");
        String resolved = supported(locale);
        return base + "/" + (resolved == null ? DEFAULT_LOCALE : resolved)
                + "/pricing?product=" + encode(productCode);
    }

    private static String supported(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String locale : SUPPORTED) {
            if (locale.equalsIgnoreCase(value.trim())) {
                return locale;
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
