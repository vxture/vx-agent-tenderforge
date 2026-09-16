// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.rest;

import com.td.czghagent.domain.model.BidCapability;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.model.PricingDeeplink;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权益视图：告诉前端「这个工作空间能做什么」。
 *
 * <p>返回的是<strong>能力集</strong>而不是原始信封的全部字段。区别在于：
 * 能力集是本产品对档位的解读，而档位与配额数字是平台的商业事实。
 * 把原始 {@code tier} 直接交给前端，等于允许每个页面各自解读一次档位——
 * 而那正是「唯一映射点」要避免的。
 *
 * <p>转化深链在这里生成，因为它需要知道<strong>这次请求用的是哪种语言</strong>——官网定价页按语言分路径。
 * 前端只负责在用户<strong>显式点击</strong>时打开它——<strong>永不自动跳转</strong>。
 */
@RestController
@RequestMapping("/api/entitlement")
public class EntitlementController {

    private final EntitlementResolver resolver;
    private final String websiteBaseUrl;

    public EntitlementController(
            EntitlementResolver resolver,
            @Value("${app.platform.website-url:}") String websiteBaseUrl) {
        this.resolver = resolver;
        this.websiteBaseUrl = websiteBaseUrl;
    }

    @GetMapping
    public Map<String, Object> current(HttpServletRequest request) {
        String workspaceId = workspaceOf(request);
        return view(workspaceId, resolver.resolve(workspaceId), localeOf(request));
    }

    /**
     * 用户在官网定价页 / console 完成订阅后切回本产品时重问一次（门禁页切回前台即触发）。
     *
     * <p>先驱逐<strong>自己工作空间</strong>的缓存再问：否则最长 45 秒里拿回的还是订阅前那份
     * 「尚未订阅」——平台的 {@code subscription_changed} 通知本该驱逐它，但通知可能晚到或丢失，
     * 而用户正盯着这 45 秒。只驱逐自己的，不接受工作空间参数。
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(HttpServletRequest request) {
        String workspaceId = workspaceOf(request);
        if (workspaceId != null) {
            resolver.invalidate(workspaceId);
        }
        return view(workspaceId, resolver.resolve(workspaceId), localeOf(request));
    }

    private static String workspaceOf(HttpServletRequest request) {
        CurrentUser user = RequestIdentity.user(request);
        return user == null || user.tenant() == null ? null : user.tenant().workspaceId();
    }

    /**
     * 这次请求用哪种语言：先看用户显式选过的 {@code NEXT_LOCALE} cookie，再看浏览器偏好。
     *
     * <p>官网定价页按语言分路径，拼错就是一个 404 或者一页读者看不懂的语言。
     */
    private static String localeOf(HttpServletRequest request) {
        String chosen = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (PricingDeeplink.LOCALE_COOKIE.equals(cookie.getName())) {
                    chosen = cookie.getValue();
                    break;
                }
            }
        }
        return PricingDeeplink.localeFrom(chosen, request.getHeader("Accept-Language"));
    }

    private Map<String, Object> view(String workspaceId, Entitlement entitlement, String locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", ProductIdentity.PRODUCT_CODE);
        body.put("workspaceId", workspaceId);
        // 门控两条公式都发出去：界面用前者，需要判断数据面时用后者。
        // 让前端自己从 tier 推导，等于把公式复制到第二个地方。
        body.put("allowsProductSurface", entitlement.allowsProductSurface());
        body.put("allowsDataPlane", entitlement.allowsDataPlane());
        body.put("capabilities", BidCapability.of(entitlement).stream()
                .map(Enum::name).sorted().toList());
        body.put("limits", entitlement.limits());
        body.put("quotaPools", entitlement.quotaPools().stream()
                .map(pool -> Map.of(
                        "metric", pool.metric(),
                        "limit", pool.limit(),
                        "remaining", pool.remaining()))
                .toList());
        // 订阅事实按原样透出，供界面渲染「试用还剩几天」这类提示。
        // 产品不据此做任何商业推断——那归 console。
        body.put("subscription", subscriptionFacts(entitlement));
        // 转化深链：官网定价页，按这次请求的语言分路径（owner 2026-09-16——套餐在官网发布）。
        body.put("subscribeUrl", PricingDeeplink.of(
                websiteBaseUrl, ProductIdentity.PRODUCT_CODE, locale));
        // 「没问到」与「没订阅」在门控上相同，对人说的话不同：前者请用户稍后再试。
        body.put("unavailable", entitlement.unavailable());
        body.put("degraded", resolver.isMock());
        return body;
    }

    /**
     * 订阅事实。
     *
     * <p>{@code status} 保留 {@code null}，<strong>不折成空串</strong>：
     * 「从未订阅」和「状态未知」是两件事，而空串会把前者伪装成后者。
     */
    private static Map<String, Object> subscriptionFacts(Entitlement entitlement) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("status", entitlement.status());
        facts.put("tier", entitlement.tier());
        facts.put("bundled", entitlement.bundled());
        facts.put("trialEndsAt", entitlement.trialEndsAt());
        facts.put("currentPeriodEnd", entitlement.currentPeriodEnd());
        facts.put("cancelAtPeriodEnd", entitlement.cancelAtPeriodEnd());
        facts.put("dataRetentionUntil", entitlement.dataRetentionUntil());
        facts.put("tierKnown", BidCapability.isKnownTier(entitlement.tier()));
        return facts;
    }
}
