// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.rest;

import com.td.czghagent.domain.model.BidCapability;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.model.SubscribeDeeplink;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
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
 * <p>转化深链在这里生成，因为它需要知道 intent，而 intent 由订阅状态决定。
 * 前端只负责在用户<strong>显式点击</strong>时打开它——<strong>永不自动跳转</strong>。
 */
@RestController
@RequestMapping("/api/entitlement")
public class EntitlementController {

    private final EntitlementResolver resolver;
    private final String consoleBaseUrl;

    public EntitlementController(
            EntitlementResolver resolver,
            @Value("${app.platform.console-url:}") String consoleBaseUrl) {
        this.resolver = resolver;
        this.consoleBaseUrl = consoleBaseUrl;
    }

    @GetMapping
    public Map<String, Object> current(HttpServletRequest request) {
        CurrentUser user = RequestIdentity.user(request);
        String workspaceId = user == null || user.tenant() == null
                ? null : user.tenant().workspaceId();
        Entitlement entitlement = resolver.resolve(workspaceId);

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
        body.put("subscribeUrl", SubscribeDeeplink.of(
                consoleBaseUrl, ProductIdentity.PRODUCT_CODE,
                SubscribeDeeplink.intentFor(entitlement)));
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
