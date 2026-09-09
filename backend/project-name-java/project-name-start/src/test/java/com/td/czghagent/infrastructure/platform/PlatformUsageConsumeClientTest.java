// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository.BufferedUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * consume 的线上形状：发出去的是什么，收回来怎么读。
 *
 * <p>用一个真会说 HTTP 的假平台而不是打桩 RestClient——要验的一半在
 * 「网络上真的发生了什么」：请求体的字段名是蛇形还是驼峰、幂等键有没有
 * 同时落在头上、平台答一个空身体时我们当成成功还是失败。
 */
class PlatformUsageConsumeClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private PlatformUsageConsumeClient client;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<Headers> lastHeaders = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/usage/consume", exchange -> {
            requestCount.incrementAndGet();
            lastHeaders.set(exchange.getRequestHeaders());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = new PlatformUsageConsumeClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "internal-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ── 请求形状 ────────────────────────────────────────────────────────────

    @Test
    void sendsTheFieldsThePlatformActuallyReads() throws Exception {
        client.consume(usage("k-1", "ws-1", "task-77"));

        JsonNode body = JSON.readTree(lastBody.get());
        assertThat(body.path("workspace_id").asText()).isEqualTo("ws-1");
        assertThat(body.path("product").asText()).isEqualTo("tenderforge");
        assertThat(body.path("metric").asText()).isEqualTo("tenderforge.bid.generations");
        assertThat(body.path("amount").asLong()).isEqualTo(1);
        assertThat(body.path("idempotency_key").asText()).isEqualTo("k-1");
        assertThat(body.path("end_user_id").asText()).isEqualTo("user-1");
    }

    /**
     * 幂等键同时作为 {@code x-request-id} 落在平台的事件旁边。
     *
     * <p>对账要靠两侧按<strong>同一个 id</strong> 找同一件事，而幂等键是双方
     * 本来就共有的那个 id。不带的话，出问题时只能按时间和金额去猜哪条对哪条。
     */
    @Test
    void carriesTheIdempotencyKeyOnTheWireForReconciliation() {
        client.consume(usage("k-1", "ws-1", null));

        assertThat(lastHeaders.get().getFirst("x-request-id")).isEqualTo("k-1");
        assertThat(lastHeaders.get().getFirst("x-vxture-internal-auth")).isEqualTo("internal-token");
    }

    /** 有 task_id 就带上（X-2），没有就不要发一个空头。 */
    @Test
    void passesTheAggregationKeyOnlyWhenThereIsOne() {
        client.consume(usage("k-1", "ws-1", "task-77"));
        assertThat(lastHeaders.get().getFirst("x-task-id")).isEqualTo("task-77");

        client.consume(usage("k-2", "ws-1", null));
        assertThat(lastHeaders.get().getFirst("x-task-id")).isNull();
    }

    /** 没有 end_user_id 时字段整个不出现，而不是一个 null——空值不是归属。 */
    @Test
    void omitsTheEndUserRatherThanSendingNull() throws Exception {
        client.consume(new BufferedUsage("k-1", "ws-1", "tenderforge.bid.generations", 1,
                null, null, LocalDateTime.parse("2026-09-09T10:00:00"), 0));

        assertThat(JSON.readTree(lastBody.get()).has("end_user_id")).isFalse();
    }

    // ── 回答的读法 ──────────────────────────────────────────────────────────

    @Test
    void readsTheGatedAndReplayedFlags() {
        responseBody.set("""
                {"gated":true,"replayed":true,"consumed":0,
                 "remaining_total":0,"event_id":"evt-1","reason":"pool exhausted"}
                """);

        UsageConsumeClient.Outcome outcome = client.consume(usage("k-1", "ws-1", null));

        assertThat(outcome.recorded()).as("gated 仍然是已记下").isTrue();
        assertThat(outcome.gated()).isTrue();
        assertThat(outcome.replayed()).isTrue();
        assertThat(outcome.eventId()).isEqualTo("evt-1");
        assertThat(outcome.reason()).isEqualTo("pool exhausted");
    }

    /**
     * 200 但身体读不出来，仍然算已记下。
     *
     * <p>反过来判成失败会让同一条用量被反复重报——平台每次都记下（幂等键兜住了），
     * 产品每次都认为没成功，于是缓冲区里那一行永远清不掉。
     */
    @Test
    void trustsA200EvenWhenTheBodyIsUnreadable() {
        responseBody.set("not json at all");

        assertThat(client.consume(usage("k-1", "ws-1", null)).recorded()).isTrue();
    }

    @Test
    void treatsANonSuccessAsNotRecordedInsteadOfThrowing() {
        responseStatus.set(503);
        responseBody.set("{\"code\":\"UPSTREAM_UNAVAILABLE\"}");

        UsageConsumeClient.Outcome outcome = client.consume(usage("k-1", "ws-1", null));

        assertThat(outcome.recorded()).isFalse();
        assertThat(outcome.status()).isEqualTo(503);
        assertThat(outcome.reason()).contains("UPSTREAM_UNAVAILABLE");
    }

    /** 平台整个不可达时也不抛——冲洗任务要能把这一批留在缓冲区里继续跑。 */
    @Test
    void survivesAPlatformThatIsNotThere() {
        PlatformUsageConsumeClient offline = new PlatformUsageConsumeClient(
                RestClient.builder(), "http://127.0.0.1:1", "internal-token");

        UsageConsumeClient.Outcome outcome = offline.consume(usage("k-1", "ws-1", null));

        assertThat(outcome.recorded()).isFalse();
        assertThat(outcome.status()).isZero();
    }

    @Test
    void neverClaimsToBeAMock() {
        assertThat(client.isMock()).isFalse();
    }

    private static BufferedUsage usage(String key, String workspaceId, String taskId) {
        return new BufferedUsage(key, workspaceId, "tenderforge.bid.generations", 1,
                "user-1", taskId, LocalDateTime.parse("2026-09-09T10:00:00"), 0);
    }
}
