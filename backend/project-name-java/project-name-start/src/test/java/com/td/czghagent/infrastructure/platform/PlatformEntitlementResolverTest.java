// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.port.EntitlementResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台权益读取：宽容解析、缓存、以及读不到时的处置。
 *
 * <p>用一个真会说 HTTP 的假平台，而不是打桩 RestClient：这里要验的东西
 * 有一半在「网络上真的发生了什么」——请求发了几次、平台答不上来时我们怎么办。
 */
class PlatformEntitlementResolverTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private EntitlementResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/platform/entitlements", exchange -> {
            requestCount.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        resolver = new PlatformEntitlementResolver(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "internal-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ── 请求形状 ────────────────────────────────────────────────────────────

    @Test
    void asksForThisProductInThatWorkspace() {
        responseBody.set("{\"tier\":\"free\"}");

        resolver.resolve("ws-1");

        assertThat(lastQuery.get())
                .contains("workspace_id=ws-1")
                .contains("product=tenderforge");
    }

    // ── 缓存与失效链 ────────────────────────────────────────────────────────

    @Test
    void cachesSoEveryPageDoesNotHitThePlatform() {
        responseBody.set("{\"tier\":\"free\"}");

        resolver.resolve("ws-1");
        resolver.resolve("ws-1");
        resolver.resolve("ws-1");

        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void keepsWorkspacesApartInTheCache() {
        responseBody.set("{\"tier\":\"free\"}");

        resolver.resolve("ws-1");
        resolver.resolve("ws-2");

        assertThat(requestCount.get())
                .as("一个工作空间的权益不能被当成另一个的").isEqualTo(2);
    }

    /**
     * 失效链的那一端。
     *
     * <p>只做 45 秒 TTL 不接失效，档位变更会有最长 45 秒的错误答案窗口——
     * 而那正好是用户刚付完钱回来点一下的那几十秒。
     */
    @Test
    void refetchesAfterInvalidation() {
        responseBody.set("{\"tier\":\"free\"}");
        resolver.resolve("ws-1");

        resolver.invalidate("ws-1");
        responseBody.set("{\"tier\":\"pro\"}");

        assertThat(resolver.resolve("ws-1").tier()).isEqualTo("pro");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void invalidatingOneWorkspaceLeavesTheOthersCached() {
        responseBody.set("{\"tier\":\"free\"}");
        resolver.resolve("ws-1");
        resolver.resolve("ws-2");

        resolver.invalidate("ws-1");
        resolver.resolve("ws-2");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    // ── 读不到时 fail-closed ────────────────────────────────────────────────

    /**
     * 平台答不上来时返回空信封，<strong>不抛异常</strong>。
     *
     * <p>把「读不到权益」变成错误页面，等于把平台的可用性直接接到自己头上；
     * 而 fail-closed 的空信封会让界面降级成「未订阅」——一个用户看得懂、
     * 且刷新一下就可能恢复的状态。
     */
    @Test
    void failsClosedWhenThePlatformIsUnhappy() {
        responseStatus.set(500);
        responseBody.set("{\"message\":\"boom\"}");

        Entitlement entitlement = resolver.resolve("ws-1");

        assertThat(entitlement.tier()).isNull();
        assertThat(entitlement.allowsProductSurface())
                .as("读不到就当没有——绝不「暂时放行」").isFalse();
    }

    @Test
    void failsClosedWithoutAWorkspaceAndWithoutCallingThePlatform() {
        Entitlement entitlement = resolver.resolve(null);

        assertThat(entitlement.allowsProductSurface()).isFalse();
        assertThat(requestCount.get())
                .as("没有工作空间就没有可问的问题").isZero();
    }

    // ── 宽容解析 ────────────────────────────────────────────────────────────

    @Test
    void readsEveryFieldOfTheEnvelope() throws Exception {
        Entitlement entitlement = PlatformEntitlementResolver.parse("ws-1", json("""
                {
                  "product": "tenderforge",
                  "status": "trialing",
                  "trial_ends_at": "2026-10-01T00:00:00Z",
                  "current_period_end": "2026-11-01T00:00:00Z",
                  "cancel_at_period_end": true,
                  "tier": "pro",
                  "bundled": true,
                  "limits": {"bid.concurrent_generations": 5, "asset.library_items": -1},
                  "quota_pools": [
                    {"metric": "tenderforge.bid.generations", "limit": 100,
                     "remaining": 42, "priority": 1}
                  ]
                }
                """));

        assertThat(entitlement.status()).isEqualTo("trialing");
        assertThat(entitlement.tier()).isEqualTo("pro");
        assertThat(entitlement.bundled()).isTrue();
        assertThat(entitlement.cancelAtPeriodEnd()).isTrue();
        assertThat(entitlement.limitOf("bid.concurrent_generations", 1)).isEqualTo(5);
        assertThat(entitlement.isUnlimited("asset.library_items", 1)).isTrue();
        assertThat(entitlement.quotaPools()).singleElement()
                .satisfies(pool -> assertThat(pool.remaining()).isEqualTo(42));
    }

    /**
     * 新字段与新枚举值必须能容忍。
     *
     * <p>做不到的表现是：平台做一次对它自己完全正常的向后兼容变更，
     * 本产品整个挂掉。
     */
    @Test
    void toleratesFieldsAndEnumValuesItHasNeverSeen() throws Exception {
        Entitlement entitlement = PlatformEntitlementResolver.parse("ws-1", json("""
                {
                  "tier": "enterprise",
                  "status": "some_future_status",
                  "brand_new_field": {"nested": true},
                  "limits": {"known": 1, "not_a_number": "oops"}
                }
                """));

        assertThat(entitlement.tier()).isEqualTo("enterprise");
        assertThat(entitlement.status()).isEqualTo("some_future_status");
        assertThat(entitlement.allowsProductSurface())
                .as("未知档位仍然是一个档位，门控照开").isTrue();
        assertThat(entitlement.limits())
                .as("非数字的上限被丢弃而不是让整个解析失败")
                .containsOnlyKeys("known");
    }

    @Test
    void treatsAMissingEnvelopeAsNoEntitlement() throws Exception {
        assertThat(PlatformEntitlementResolver.parse("ws-1", null).allowsProductSurface())
                .isFalse();
        assertThat(PlatformEntitlementResolver.parse("ws-1", json("[]")).allowsProductSurface())
                .isFalse();
    }

    /** 缺 metric 的池子被丢弃：一个没有指标名的配额池什么也说明不了。 */
    @Test
    void dropsQuotaPoolsWithoutAMetricName() throws Exception {
        Entitlement entitlement = PlatformEntitlementResolver.parse("ws-1", json("""
                {"tier":"free","quota_pools":[{"limit":10},{"metric":"a.b","limit":5}]}
                """));

        assertThat(entitlement.quotaPools()).singleElement()
                .satisfies(pool -> assertThat(pool.metric()).isEqualTo("a.b"));
    }

    private static JsonNode json(String raw) throws Exception {
        return JSON.readTree(raw);
    }
}
