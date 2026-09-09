// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.rest;

import com.td.czghagent.PostgresBackedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开通 webhook 走完整条 HTTP 链路。
 *
 * <p>单测已经验过验签算法和处理逻辑，这里验的是<strong>它们之间的接缝</strong>：
 * Spring 交给控制器的请求体到底是不是原始字节、这个端点会不会被鉴权过滤器拦下、
 * 以及各种处置最终落成什么状态码。这三件事都只在真实请求上才成立或不成立。
 */
@SpringBootTest(properties = {
        "app.bootstrap.enabled=false",
        "app.platform.usage-flush-enabled=false",
        "app.platform.provision-webhook-secret=" + ProvisioningWebhookIntegrationTest.SECRET,
        "app.storage.root=${java.io.tmpdir}/provisioning-${random.uuid}"
})
@AutoConfigureMockMvc
class ProvisioningWebhookIntegrationTest extends PostgresBackedTest {

    static final String SECRET = "whsec_integration";
    private static final String PATH = "/api/platform/provisioning/webhook";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM platform_provision_delivery");
        jdbcTemplate.update("DELETE FROM platform_workspace_provision");
    }

    // ── 正常投递 ────────────────────────────────────────────────────────────

    /**
     * 一次开通投递，<strong>没有任何会话</strong>。
     *
     * <p>没有 Authorization 头、没有 cookie。这条同时验了鉴权白名单：
     * 漏加白名单的表现是平台每一条投递都收到 401，而平台侧看到的只是
     * 「这个产品拒收」——它没有办法知道原因是我们把它当成了未登录的浏览器。
     */
    @Test
    void acceptsASignedDeliveryWithoutAnySession() throws Exception {
        String body = payload("d-1", "tenant.provisioned", 1, "ws-1");

        post(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("processed"));

        assertThat(instanceState("ws-1")).isEqualTo("provisioned");
        assertThat(deliveries()).extracting(row -> row.get("outcome"))
                .containsExactly("PROCESSED");
    }

    /**
     * 非 ASCII 的 payload 能走通。
     *
     * <p><strong>这一条并不能证明字节精确。</strong>实测确认过：把控制器改成
     * 用 {@code String} 接收再 {@code getBytes(UTF_8)}，它照样全绿——
     * 因为 {@code application/json} 下 Spring 就是按 UTF-8 解码的，往返无损。
     * 它守的是「中文内容不会莫名其妙挂掉」这条底线，
     * 字节精确由 {@link #survivesAContentTypeThatLiesAboutItsCharset()} 覆盖。
     */
    @Test
    void handlesANonAsciiPayload() throws Exception {
        String body = "{\"id\":\"d-cn\",\"type\":\"tenant.provisioned\",\"seq\":1,"
                + "\"workspace_id\":\"ws-1\",\"application\":\"tenderforge\","
                + "\"note\":\"华东区投标中心\"}";

        post(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("processed"));
    }

    /**
     * 声明的字符集和实际字节不一致时，验签仍然成立。
     *
     * <p>这是「必须对原始字节验签」唯一能被真正验到的地方：发送方声明
     * {@code charset=ISO-8859-1} 却送来 UTF-8 字节。用 {@code byte[]} 接收的实现
     * 根本不看这个声明，签名照样对得上；任何在中途按声明解码再编码的实现，
     * 在这里会把字节改写掉，于是这条投递永远验不过、被平台无限重试。
     *
     * <p>发送方声明错字符集听起来像是它的 bug——是的，但它的 bug 会变成
     * 我们的故障，而我们本来可以完全不受影响。
     */
    @Test
    void survivesAContentTypeThatLiesAboutItsCharset() throws Exception {
        byte[] raw = ("{\"id\":\"d-lie\",\"type\":\"tenant.provisioned\",\"seq\":1,"
                + "\"workspace_id\":\"ws-1\",\"application\":\"tenderforge\","
                + "\"note\":\"投标\"}").getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();

        mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post(PATH)
                        .header("Content-Type", "application/json; charset=ISO-8859-1")
                        .header("X-Vxture-Signature",
                                "t=" + timestamp + ",v1=" + sign(timestamp, raw))
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("processed"));
    }

    @Test
    void recordsADeprovisionAsAStateChangeAndKeepsTheRow() throws Exception {
        post(payload("d-1", "tenant.provisioned", 1, "ws-1")).andExpect(status().isOk());
        post(payload("d-2", "tenant.deprovisioned", 2, "ws-1")).andExpect(status().isOk());

        assertThat(instanceState("ws-1")).isEqualTo("deprovisioned");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM platform_workspace_provision WHERE workspace_id = 'ws-1'
                """, Integer.class))
                .as("停用改状态，不删行").isEqualTo(1);
    }

    // ── 至少一次投递 ────────────────────────────────────────────────────────

    /**
     * 重投同一条也回 2xx。
     *
     * <p>把重复答成 4xx/5xx 会让平台永远重试一件已经办好的事——
     * 而重试是有退避的，最终表现是这个产品的 webhook 队列越积越长。
     */
    @Test
    void acknowledgesARepeatedDeliveryInsteadOfAskingForARetry() throws Exception {
        String body = payload("d-1", "tenant.provisioned", 1, "ws-1");

        post(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("processed"));
        post(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("duplicate"));

        assertThat(deliveries()).hasSize(1);
    }

    @Test
    void acknowledgesAStaleEventWithoutRewritingTheState() throws Exception {
        post(payload("d-2", "tenant.deprovisioned", 2, "ws-1")).andExpect(status().isOk());

        post(payload("d-1", "tenant.provisioned", 1, "ws-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("stale"));

        assertThat(instanceState("ws-1")).isEqualTo("deprovisioned");
    }

    @Test
    void acknowledgesAnEventForAnotherProduct() throws Exception {
        String body = "{\"id\":\"d-9\",\"type\":\"tenant.provisioned\",\"seq\":1,"
                + "\"workspace_id\":\"ws-1\",\"application\":\"some-other-product\"}";

        post(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("wrong_product"));

        assertThat(deliveries()).isEmpty();
    }

    // ── 拒绝路径 ────────────────────────────────────────────────────────────

    /** 签名不对就 401，而且什么都不该落库。 */
    @Test
    void refusesAndRecordsNothingWhenTheSignatureIsWrong() throws Exception {
        String body = payload("d-1", "tenant.provisioned", 1, "ws-1");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Vxture-Signature",
                                "t=" + Instant.now().getEpochSecond() + ",v1=" + "0".repeat(64))
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(deliveries()).isEmpty();
        assertThat(instanceState("ws-1")).isNull();
    }

    /** 完全没有签名头也是 401，不是 500。 */
    @Test
    void refusesAnUnsignedRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 签名对但内容不成立 → 400，且明确 {@code retryable: false}。
     *
     * <p>重试一条缺字段的投递不会让它变好；把它标成可重试等于请平台
     * 反复送同一份坏数据。
     */
    @Test
    void refusesASignedButUnusablePayload() throws Exception {
        post("not json at all")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_PAYLOAD_INVALID"))
                .andExpect(jsonPath("$.retryable").value(false));

        post("{\"type\":\"tenant.provisioned\",\"application\":\"tenderforge\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_DELIVERY_ID_MISSING"));

        post("{\"id\":\"d-1\",\"type\":\"tenant.provisioned\",\"application\":\"tenderforge\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_WORKSPACE_MISSING"));
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions post(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();
        return mockMvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Vxture-Signature", "t=" + timestamp + ",v1=" + sign(timestamp, raw))
                .content(raw));
    }

    private static String payload(String id, String type, long seq, String workspaceId) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"seq\":" + seq
                + ",\"workspace_id\":\"" + workspaceId + "\",\"tenant_id\":\"org-1\""
                + ",\"application\":\"tenderforge\"}";
    }

    private static String sign(long timestamp, byte[] body) {
        try {
            byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.US_ASCII);
            byte[] payload = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, payload, 0, prefix.length);
            System.arraycopy(body, 0, payload, prefix.length, body.length);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String instanceState(String workspaceId) {
        return jdbcTemplate.query("""
                SELECT state FROM platform_workspace_provision WHERE workspace_id = ?
                """, rs -> rs.next() ? rs.getString("state") : null, workspaceId);
    }

    private List<Map<String, Object>> deliveries() {
        return jdbcTemplate.queryForList("SELECT * FROM platform_provision_delivery");
    }
}
