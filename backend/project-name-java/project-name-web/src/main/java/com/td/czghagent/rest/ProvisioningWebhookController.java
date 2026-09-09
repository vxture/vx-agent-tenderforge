// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.application.command.service.ProvisioningCommandService;
import com.td.czghagent.domain.model.ProvisioningEvent;
import com.td.czghagent.domain.model.TaskContext;
import com.td.czghagent.domain.port.WebhookSignatureVerifier;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台开通/停用事件的接收端（C3 下发）。
 *
 * <p>这个端点<strong>不要求会话</strong>——调用方是平台，不是浏览器。
 * 它的鉴权全部来自 HMAC 验签，所以验签必须是第一件事，
 * 且未配置密钥时一律拒绝（见 {@link WebhookSignatureVerifier}）。
 *
 * <p>回什么码决定平台重不重试，这是整个端点最容易接错的地方：
 * <ul>
 *   <li>401 —— 验签不过。平台不该重试一个签错了的请求。</li>
 *   <li>400 —— 身体不是 JSON、或缺投递标识。重试也不会变好。</li>
 *   <li>200 —— 已处理，<strong>以及</strong>重复、过期、发错产品、类型不认识。
 *       后面这几种都不是错误，是「至少一次」投递的正常产物；
 *       答成失败会让平台永远重试一件已经办好的事。</li>
 *   <li>500 —— 只在<em>我们自己</em>没处理成时。这一条才是要平台重试的信号。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/platform/provisioning/webhook")
public class ProvisioningWebhookController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProvisioningWebhookController.class);

    private final WebhookSignatureVerifier verifier;
    private final ProvisioningCommandService provisioning;
    private final ObjectMapper objectMapper;

    public ProvisioningWebhookController(WebhookSignatureVerifier verifier,
                                         ProvisioningCommandService provisioning,
                                         ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.provisioning = provisioning;
        this.objectMapper = objectMapper;
    }

    /**
     * 接一次投递。
     *
     * <p>身体用 {@code byte[]} 接，<strong>不是</strong> {@code String}：
     * HMAC 要对原始字节算，而 {@code String} 已经过一次按请求字符集的解码。
     * 那个 bug 只在 payload 里出现非 ASCII 字符时才发作。
     */
    @PostMapping
    public ResponseEntity<?> receive(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "X-Vxture-Signature", required = false) String signature,
            @RequestHeader(value = "X-Vxture-Delivery", required = false) String deliveryHeader,
            HttpServletRequest request) {

        if (!verifier.verify(rawBody, signature)) {
            // 不区分「没配密钥」「签错了」「时间戳过期」——对调用方一律是同一句话。
            // 分开说等于告诉一个正在试探的人他离对还差多少。
            LOGGER.warn("Rejected provisioning webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorEnvelope("WEBHOOK_SIGNATURE_INVALID",
                            "签名校验失败", false, null));
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (java.io.IOException malformed) {
            return badRequest("WEBHOOK_PAYLOAD_INVALID", "请求体不是合法 JSON");
        }
        if (payload == null || !payload.isObject()) {
            return badRequest("WEBHOOK_PAYLOAD_INVALID", "请求体不是 JSON 对象");
        }

        String deliveryId = text(deliveryHeader, text(payload.path("id").asText(null), null));
        if (deliveryId == null) {
            // 没有投递标识就没有幂等键，处理它等于放弃去重。
            return badRequest("WEBHOOK_DELIVERY_ID_MISSING", "缺少投递标识");
        }
        String workspaceId = text(payload.path("workspace_id").asText(null), null);
        if (workspaceId == null) {
            return badRequest("WEBHOOK_WORKSPACE_MISSING", "缺少 workspace_id");
        }

        ProvisioningEvent event = new ProvisioningEvent(
                deliveryId,
                payload.path("type").asText(""),
                payload.path("seq").asLong(0),
                workspaceId,
                text(payload.path("tenant_id").asText(null), null),
                // 平台把产品码放在 application 里；缺省成本产品会让一个
                // 漏填字段的事件被当成发给我们的，所以取不到就留空并被拒。
                payload.path("application").asText(""));

        try {
            // X-2：把投递标识当作这次处理的聚合键，本地日志与平台侧投递记录
            // 从此按同一个 id 对得上。
            ProvisioningCommandService.Outcome outcome = TaskContext.run(deliveryId, () ->
                    provisioning.handle(event, RequestIdentity.traceId(request),
                            LocalDateTime.now()));
            return ResponseEntity.ok(Map.of("outcome", outcome.name().toLowerCase(
                    java.util.Locale.ROOT)));
        } catch (RuntimeException failure) {
            // 只有这一条该让平台重试。
            LOGGER.error("Provisioning delivery {} failed", deliveryId, failure);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorEnvelope("WEBHOOK_PROCESSING_FAILED",
                            "处理失败，请重试", true, null));
        }
    }

    private static ResponseEntity<ErrorEnvelope> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(new ErrorEnvelope(code, message, false, null));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
