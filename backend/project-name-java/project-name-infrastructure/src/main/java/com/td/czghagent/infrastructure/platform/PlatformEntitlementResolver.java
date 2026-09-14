// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.model.QuotaPool;
import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.port.EntitlementResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从平台读权益（{@code GET /platform/entitlements}）。
 *
 * <p>凭证是一张为该工作空间铸的 S2S 票（见 {@link PlatformCallCredentials}）。
 * 平台按<strong>票上的</strong>工作空间作答，查询串里声明的那个会被丢弃——
 * 两者本来就是同一个值，查询串照旧带上，是给平台日志与对账看的。
 */
public class PlatformEntitlementResolver implements EntitlementResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformEntitlementResolver.class);

    /**
     * 缓存时长。
     *
     * <p>45 秒不是拍的，是平台在响应头里给的
     * {@code Cache-Control: private, max-age=45}——按它缓存，配合失效链做秒级失效。
     * 自己拍一个更长的值等于单方面延长了错误答案的窗口。
     */
    private static final Duration TTL = Duration.ofSeconds(45);

    private final RestClient client;
    private final String baseUrl;
    private final PlatformCallCredentials credentials;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public PlatformEntitlementResolver(RestClient.Builder builder, String baseUrl,
                                       PlatformCallCredentials credentials) {
        this.client = builder.build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.credentials = credentials;
    }

    @Override
    public Entitlement resolve(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            // 没有工作空间就没有权益可言。这不是错误——用户可能还没被开通到任何空间。
            return Entitlement.none(workspaceId, ProductIdentity.PRODUCT_CODE);
        }
        Cached cached = cache.get(workspaceId);
        if (cached != null && cached.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return cached.entitlement();
        }
        Entitlement fetched = fetch(workspaceId);
        cache.put(workspaceId, new Cached(fetched, Instant.now()));
        return fetched;
    }

    @Override
    public void invalidate(String workspaceId) {
        cache.remove(workspaceId);
    }

    @Override
    public boolean isMock() {
        return false;
    }

    /**
     * 读一次权益。
     *
     * <p><strong>永不抛出</strong>：读不到就返回空信封（fail-closed）。
     * 把平台的一次抖动变成产品的错误页面，是把别人的可用性直接接到自己头上；
     * 而空信封会让界面降级成「未订阅」——一个用户看得懂、且可以刷新重试的状态。
     */
    private Entitlement fetch(String workspaceId) {
        String url = baseUrl + "/platform/entitlements"
                + "?workspace_id=" + encode(workspaceId)
                + "&product=" + encode(ProductIdentity.PRODUCT_CODE);
        try {
            return parse(workspaceId, get(url, workspaceId, true));
        } catch (RuntimeException exception) {
            // 也包括铸不出票：平台答 invalid_target 意味着本产品在该工作空间
            // 没有有效的订阅或开通，那本来就是「没有权益」。
            LOGGER.warn("Entitlement lookup failed for workspace {}", workspaceId, exception);
            return Entitlement.none(workspaceId, ProductIdentity.PRODUCT_CODE);
        }
    }

    /**
     * 带票发一次；票被拒（401）就作废并<strong>重铸一次</strong>。
     *
     * <p>只重试一次：票在平台侧已不被接受时，新票能立刻恢复；新票仍被拒说明
     * 问题不在票上，再试只会把一次失败放大成一串。
     */
    private JsonNode get(String url, String workspaceId, boolean mayRetry) {
        S2SToken token = credentials.mint(workspaceId);
        try {
            return client.get().uri(url)
                    .headers(headers -> PlatformCallCredentials.apply(headers, token))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().value() != 401) {
                throw rejected;
            }
            credentials.invalidate(token);
            if (!mayRetry) {
                throw rejected;
            }
            return get(url, workspaceId, false);
        }
    }

    /**
     * 宽容解析：已知字段做类型收敛，未知字段忽略，缺失字段取默认。
     *
     * <p>「容忍未知」是通则的硬要求。做不到的表现很具体：平台给信封加一个字段、
     * 或给 {@code status} 加一个枚举值，本产品整个挂掉——而那是一次
     * 对平台侧完全正常的向后兼容变更。
     */
    static Entitlement parse(String workspaceId, JsonNode body) {
        if (body == null || !body.isObject()) {
            return Entitlement.none(workspaceId, ProductIdentity.PRODUCT_CODE);
        }
        Map<String, Long> limits = new HashMap<>();
        JsonNode limitsNode = body.path("limits");
        if (limitsNode.isObject()) {
            limitsNode.properties().forEach(entry -> {
                if (entry.getValue().isNumber()) {
                    limits.put(entry.getKey(), entry.getValue().asLong());
                }
            });
        }
        List<QuotaPool> pools = new ArrayList<>();
        for (JsonNode pool : body.path("quota_pools")) {
            String metric = pool.path("metric").asText(null);
            if (metric != null && !metric.isBlank()) {
                pools.add(new QuotaPool(metric,
                        pool.path("limit").asLong(0),
                        pool.path("remaining").asLong(0),
                        pool.path("priority").asInt(0)));
            }
        }
        return new Entitlement(
                workspaceId,
                text(body, "product", ProductIdentity.PRODUCT_CODE),
                // status 原样保留，包括未知枚举值：门控看的是 tier / bundled，
                // 不是 status，所以一个没见过的状态不该让任何判断失效。
                text(body, "status", null),
                text(body, "trial_ends_at", null),
                text(body, "current_period_end", null),
                body.path("cancel_at_period_end").asBoolean(false),
                text(body, "data_retention_until", null),
                text(body, "tier", null),
                body.path("bundled").asBoolean(false),
                limits, pools);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Cached(Entitlement entitlement, Instant fetchedAt) {
    }
}
