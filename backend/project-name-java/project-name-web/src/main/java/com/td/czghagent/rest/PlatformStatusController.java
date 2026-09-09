// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.domain.model.DeployStage;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.domain.port.S2STokenMinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台接入自证端点。
 *
 * <p>写在文档里的「已接通」是快照，会烂。这个端点<strong>每次都重新回答</strong>：
 * 四条通道现在各自处于什么状态、有没有在用替身、卡在哪个凭证上。
 * 与平台线对量时互发链接，而不是互发截图。
 *
 * <p><strong>只报状态，不报值。</strong>这里出现的是「已配置 / 未配置」和 issuer 主机名，
 * 绝不出现 client_secret、内部令牌或 webhook 密钥——一个会泄露密钥的自证端点，
 * 本身就是它要证明的那类问题。
 *
 * <p>需要鉴权。它暴露的是本部署的接入拓扑，属于运维信息：未登录能读到
 * 「哪条通道没配」，等于给踩点的人省了一步。
 */
@RestController
@RequestMapping("/api/status")
public class PlatformStatusController {

    private final OidcGateway oidcGateway;
    private final S2STokenMinter s2sTokenMinter;
    private final EntitlementResolver entitlementResolver;
    private final UsageConsumeClient usageConsumeClient;
    private final String version;
    private final DeployStage deployStage;
    private final boolean oidcEnabled;
    private final String oidcIssuer;
    private final boolean allowMockOnDeploy;
    private final boolean platformApiConfigured;
    private final boolean provisionWebhookConfigured;
    private final boolean atlasConfigured;

    public PlatformStatusController(
            OidcGateway oidcGateway,
            S2STokenMinter s2sTokenMinter,
            EntitlementResolver entitlementResolver,
            UsageConsumeClient usageConsumeClient,
            @Value("${app.version:dev}") String version,
            @Value("${app.deploy-stage:local}") String deployStage,
            @Value("${app.oidc.enabled:false}") boolean oidcEnabled,
            @Value("${app.oidc.issuer:}") String oidcIssuer,
            @Value("${app.allow-mock-on-deploy:false}") boolean allowMockOnDeploy,
            @Value("${app.platform.api-url:}") String platformApiUrl,
            @Value("${app.platform.provision-webhook-secret:}") String provisionWebhookSecret,
            @Value("${app.atlas.api-url:}") String atlasApiUrl
    ) {
        this.oidcGateway = oidcGateway;
        this.s2sTokenMinter = s2sTokenMinter;
        this.entitlementResolver = entitlementResolver;
        this.usageConsumeClient = usageConsumeClient;
        this.version = version;
        this.deployStage = DeployStage.parse(deployStage);
        this.oidcEnabled = oidcEnabled;
        this.oidcIssuer = oidcIssuer;
        this.allowMockOnDeploy = allowMockOnDeploy;
        this.platformApiConfigured = notBlank(platformApiUrl);
        this.provisionWebhookConfigured = notBlank(provisionWebhookSecret);
        this.atlasConfigured = notBlank(atlasApiUrl);
    }

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", ProductIdentity.PRODUCT_CODE);
        body.put("version", version);
        body.put("deployStage", deployStage.name().toLowerCase(java.util.Locale.ROOT));
        // 降级必须自报，而且是<strong>三个通道的并集</strong>。
        // 只看身份的话，一套真身份配上替身权益会报 degraded=false——
        // 而那时每个人拿到的能力都是编造的，用量一笔也没入账。
        body.put("degraded", oidcGateway.isMock()
                || entitlementResolver.isMock() || usageConsumeClient.isMock());
        body.put("channels", List.of(
                channel("C1", "身份（OIDC RP）", oidcChannelState(), oidcDetail()),
                channel("C1b", "S2S 换票",
                        s2sTokenMinter.isConfigured() ? "configured" : "not_configured",
                        "RFC 8693；凭据即 C1 的 client 对，无需另行申请。"
                                + "票只活 300 秒且不可刷新，每次调用现铸"),
                channel("C2", "权益", mockableState(entitlementResolver.isMock()),
                        entitlementResolver.isMock()
                                ? "正在使用替身权益，所有人拿到的能力都是编造的"
                                : "GET /platform/entitlements；45 秒短缓存、不落库"),
                channel("C3-up", "用量上报", mockableState(usageConsumeClient.isMock()),
                        usageConsumeClient.isMock()
                                ? "正在使用替身上报，所有用量都没有入账"
                                : "POST /usage/consume；永远 200，gated 是信息不是指令"),
                channel("C3-down", "开通 webhook",
                        provisionWebhookConfigured ? "configured" : "not_configured",
                        "HMAC 对原始字节验签、按 id 幂等、按 seq 拒倒序"),
                channel("atlas", "模型出口", atlasConfigured ? "configured" : "not_configured",
                        atlasConfigured ? "POST /v1/chat" : "当前仍直连模型供应商，属已知契约违规")
        ));
        return body;
    }

    /**
     * C1 的状态有四种，刻意不合并成布尔。
     *
     * <p>「关着」「开着但缺配置」「在用替身」「已接通」对排障是四件不同的事，
     * 而一个布尔只能表达其中一刀，剩下的差别就得靠猜。
     */
    private String oidcChannelState() {
        if (oidcGateway.isMock()) {
            return deployStage.isDeployed() ? "degraded_mock" : "mock";
        }
        return oidcEnabled ? "active" : "configured_but_disabled";
    }

    /**
     * 与 C1 同一套四态里的三态：替身、部署态的替身、已接通。
     *
     * <p>问对象自己是不是替身，而不是看 URL 配没配。两者<strong>不总是一致</strong>：
     * 配了 URL 却没配令牌时装配会落到替身上，而只看 URL 会报「已配置」。
     */
    private String mockableState(boolean mock) {
        if (!mock) {
            return "active";
        }
        return deployStage.isDeployed() ? "degraded_mock" : "mock";
    }

    private String oidcDetail() {
        if (oidcGateway.isMock()) {
            String base = "正在使用替身身份，所有登录都是编造的";
            return deployStage.isDeployed() && allowMockOnDeploy
                    ? base + "；由 ALLOW_MOCK_ON_DEPLOY 显式开启" : base;
        }
        // 只报主机名不报完整配置：issuer 本身不是秘密，但把它和 client_id
        // 一起摆出来会让这个端点变成一份现成的接入清单。
        return "issuer=" + oidcIssuer;
    }

    private static Map<String, Object> channel(String code, String name,
                                               String state, String detail) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("code", code);
        channel.put("name", name);
        channel.put("state", state);
        channel.put("detail", detail);
        return channel;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
