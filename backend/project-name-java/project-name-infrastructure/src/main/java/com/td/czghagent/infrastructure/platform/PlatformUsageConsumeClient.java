// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository.BufferedUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向平台 consume 服务上报用量。平台是用量的<strong>唯一写入方</strong>。
 */
public class PlatformUsageConsumeClient implements UsageConsumeClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PlatformUsageConsumeClient.class);

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final RestClient client;
    private final String baseUrl;
    private final String internalAuthToken;

    public PlatformUsageConsumeClient(RestClient.Builder builder, String baseUrl,
                                      String internalAuthToken) {
        this.client = builder.build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.internalAuthToken = internalAuthToken;
    }

    @Override
    public Outcome consume(BufferedUsage usage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspace_id", usage.workspaceId());
        body.put("product", ProductIdentity.PRODUCT_CODE);
        body.put("metric", usage.metric());
        body.put("amount", usage.amount());
        body.put("idempotency_key", usage.idempotencyKey());
        if (usage.endUserId() != null) {
            body.put("end_user_id", usage.endUserId());
        }
        try {
            // 取字符串再自己解析，<strong>不</strong>直接反序列化成 JsonNode：
            // 后者在身体不是合法 JSON 时会抛，于是一个已经被平台记下的 200
            // 会被判成失败，那一行就在缓冲区里被无限重报。状态码才是「记没记下」
            // 的判据，身体只是细节——细节读不出来不该推翻判据。
            String raw = client.post()
                    .uri(baseUrl + "/usage/consume")
                    .header("x-vxture-internal-auth", internalAuthToken)
                    // 幂等键同时作为请求标识落在平台的事件旁边——
                    // 对账时两侧要按同一个 id 找同一件事，而这是双方本来就共有的那个 id。
                    .header("x-request-id", usage.idempotencyKey())
                    // X-2：有 task_id 就带上，让这次计量能和触发它的那条链对上。
                    .headers(headers -> {
                        if (usage.taskId() != null) {
                            headers.add("x-task-id", usage.taskId());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(200, readTree(raw));
        } catch (org.springframework.web.client.RestClientResponseException failure) {
            // 非 200 = 还没记下。不抛给冲洗任务，让它把这一行留在缓冲区里重试。
            return new Outcome(failure.getStatusCode().value(), false, false, null,
                    failure.getResponseBodyAsString());
        } catch (RuntimeException exception) {
            LOGGER.warn("Usage consume call failed for {}", usage.idempotencyKey(), exception);
            return new Outcome(0, false, false, null, exception.getMessage());
        }
    }

    @Override
    public boolean isMock() {
        return false;
    }

    /** 宽容解析：读不出来就当没有细节，而不是当作一次失败。 */
    private static JsonNode readTree(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            LOGGER.warn("Consume answered 200 with an unparseable body: {}",
                    raw.length() > 200 ? raw.substring(0, 200) : raw);
            return null;
        }
    }

    static Outcome parse(int status, JsonNode body) {
        if (body == null || !body.isObject()) {
            // 200 但答不出信封：平台已经记下了，只是没告诉我们细节。
            // 当成已记下——重判成失败会让同一条用量被反复重报。
            return new Outcome(status, false, false, null, null);
        }
        return new Outcome(status,
                body.path("gated").asBoolean(false),
                body.path("replayed").asBoolean(false),
                body.path("event_id").isTextual() ? body.path("event_id").asText() : null,
                body.path("reason").isTextual() ? body.path("reason").asText() : null);
    }
}
