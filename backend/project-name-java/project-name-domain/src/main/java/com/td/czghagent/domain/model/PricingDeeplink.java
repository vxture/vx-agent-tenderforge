// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
package com.td.czghagent.domain.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 转化深链：官网定价页。
 *
 * <p>owner 2026-09-16 裁定：订阅入口是官网定价页 {@code {官网}/pricing?product={产品码}}——<strong>套餐在那里发布</strong>。
 *
 * <p>此前按通则 C2 拼的是 {@code {CONSOLE_BASE}/subscribe?product=&intent=}，而那不是订阅页面：
 * console 的 subscribe 对目录里没有套餐的产品降级回订阅首页（本产品正是这种情形，
 * {@code /api/products/tenderforge/plans} 返回空），于是「前往订阅」把人带到一个与订阅无关的页面。
 * <strong>通则 C2 的「转化深链」一节已同步更正</strong>（2026-09-16 修订记录）。
 *
 * <p><strong>不拼语言段。</strong>官网自己按访客的 {@code NEXT_LOCALE} cookie、其次 {@code Accept-Language}
 * 307 分流到 {@code /zh-CN/} 或 {@code /en-US/}（2026-09-16 实测）。产品侧写的那个语言 cookie 是
 * <strong>host-scoped</strong>（种在 {@code tenderforge.vxture.com} 上），{@code vxture.com} 根本读不到它，
 * 所以拼一个语言段只是用产品侧的猜测去覆盖访客在官网的选择；拼错还会落到官网没有的语言段上。
 * 代价说清楚：在产品里选了英文、浏览器却是中文且从没在官网选过语言的人，会落到中文定价页。
 *
 * <p>产品内<strong>没有价格、没有商业推断</strong>：能不能试用、该买哪一档、多少钱，全部归官网与 console。
 * 这里只把用户带到入口，并且只在用户<strong>显式点击</strong>时打开。
 *
 * <p><strong>不带 workspace_id</strong>：开通对象由会话决定，带上它等于让一个可被伪造的参数决定给谁开通。
 * 也<strong>不带 intent</strong>：定价页只认 product，多拼一个参数是拼给自己看的。
 */
public final class PricingDeeplink {

    private PricingDeeplink() {
    }

    /** {@code {官网}/pricing?product={产品码}}。语言由官网自己分流，这里不拼。 */
    public static String of(String websiteBaseUrl, String productCode) {
        String base = websiteBaseUrl == null ? "" : websiteBaseUrl.replaceAll("/+$", "");
        return base + "/pricing?product=" + encode(productCode);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
