// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.S2STokenMinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RFC 8693 token exchange 的实现。
 *
 * <p>票只活 300 秒且不可刷新，所以<strong>每次调用现铸</strong>，按上下文缓存。
 */
public class PlatformS2STokenMinter implements S2STokenMinter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformS2STokenMinter.class);

    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";

    /**
     * 提前重铸的余量。
     *
     * <p>一次调用可能在票有效时开始、在票过期后才到达被调方；
     * 300 秒的票上留 30 秒，覆盖正常的网络与排队延迟。
     */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient client;
    private final OidcProperties properties;
    private final OidcDiscovery discovery;
    private final Map<String, S2SToken> cache = new ConcurrentHashMap<>();

    public PlatformS2STokenMinter(RestClient.Builder builder, OidcProperties properties,
                                  OidcDiscovery discovery) {
        this.client = builder.build();
        this.properties = properties;
        this.discovery = discovery;
    }

    @Override
    public S2SToken onBehalfOf(String audience, String userAccessToken) {
        MultiValueMap<String, String> form = baseForm(audience);
        form.add("subject_token", userAccessToken);
        return mint(oboKey(audience, userAccessToken), audience,
                S2SToken.Mode.ON_BEHALF_OF, form);
    }

    @Override
    public S2SToken forService(String audience, TenantScope tenant) {
        MultiValueMap<String, String> form = baseForm(audience);
        form.add("workspace_id", tenant.workspaceId());
        form.add("org_id", tenant.orgId());
        return mint(serviceKey(audience, tenant), audience, S2SToken.Mode.SERVICE, form);
    }

    @Override
    public void invalidate(S2SToken token) {
        cache.values().removeIf(cached -> cached.value().equals(token.value()));
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    private S2SToken mint(String cacheKey, String audience, S2SToken.Mode mode,
                          MultiValueMap<String, String> form) {
        LocalDateTime now = LocalDateTime.now();
        S2SToken cached = cache.get(cacheKey);
        if (cached != null && now.plus(EXPIRY_MARGIN).isBefore(cached.expiresAt())) {
            return cached;
        }
        S2SToken minted = exchange(audience, mode, form, now);
        evictExpired(now);
        cache.put(cacheKey, minted);
        return minted;
    }

    private S2SToken exchange(String audience, S2SToken.Mode mode,
                              MultiValueMap<String, String> form, LocalDateTime now) {
        try {
            JsonNode body = client.post()
                    .uri(discovery.document().tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String value = body == null ? null : body.path("access_token").asText(null);
            if (value == null || value.isBlank()) {
                throw failure(audience, "exchange_failed", "换票端点未返回 access_token", false);
            }
            JWTClaimsSet claims = decodeWithoutVerification(value);
            return new S2SToken(value, audience, mode,
                    claims == null ? null : claims.getSubject(),
                    stringClaim(claims, "tenant_id"),
                    now.plusSeconds(body.path("expires_in").asLong(300)));
        } catch (RestClientResponseException exception) {
            throw translate(audience, exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("S2S token exchange to {} failed", audience, exception);
            throw failure(audience, "unavailable", "身份服务暂时不可用", true);
        }
    }

    /**
     * 把换票端点的拒绝翻成本地错误。
     *
     * <p>{@code invalid_target} 是<strong>重载</strong>的：平台先查 audience，
     * 再查本产品是否覆盖该工作空间。audience 是我们自己写死的常量，所以拿到这个码
     * 几乎一定是<strong>后者</strong>——平台还没在那个工作空间里给本产品开通。
     * 这正是一个新登记产品最先撞上的失败，所以它值得一句说人话的提示，
     * 而不是把 OAuth 码原样丢给用户。
     */
    private BusinessException translate(String audience, RestClientResponseException exception) {
        String code = oauthErrorOf(exception.getResponseBodyAsString());
        boolean retryable = exception.getStatusCode().is5xxServerError()
                || exception.getStatusCode().value() == 429
                || "temporarily_unavailable".equals(code);
        if ("invalid_target".equals(code)) {
            return failure(audience, code,
                    "平台尚未在当前工作空间为本产品开通 " + audience + " 的调用权限", false);
        }
        return failure(audience, code, "换票被拒绝", retryable);
    }

    /**
     * 取 OAuth 错误码。
     *
     * <p>两种形状都认：RFC 6749 的 {@code {"error": "..."}}，以及平台换票端点
     * 实际返回的 NestJS 异常体 {@code {"message":"invalid_target","error":"Bad Request"}}
     * ——后者把 OAuth 码放在 {@code message} 里，而 {@code error} 装的是 HTTP 原因短语。
     * 只读 {@code error} 的客户端会得到「Bad Request」，什么也说明不了。
     *
     * <p>只取这一个字段，不把整个响应体带进异常：换票表单里有 client_secret，
     * 一个回显请求参数的端点会让密钥进日志。
     */
    private String oauthErrorOf(String body) {
        if (body == null || body.isBlank()) {
            return "unknown";
        }
        try {
            JsonNode parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            String message = parsed.path("message").asText(null);
            if (message != null && !message.isBlank() && !message.contains(" ")) {
                return message;
            }
            String error = parsed.path("error").asText(null);
            return error == null || error.isBlank() || error.contains(" ") ? "unknown" : error;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return "unknown";
        }
    }

    private BusinessException failure(String audience, String code, String message,
                                      boolean retryable) {
        LOGGER.warn("S2S token exchange to {} rejected: {}", audience, code);
        return new BusinessException("S2S_EXCHANGE_FAILED", message, 502, retryable, null);
    }

    private MultiValueMap<String, String> baseForm(String audience) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("audience", audience);
        return form;
    }

    /**
     * OBO 的缓存键含用户票的摘要。
     *
     * <p>用 SHA-256 而不是便宜的哈希：这里的碰撞意味着<strong>把一个用户的票
     * 发给另一个用户的请求</strong>，而 32 位哈希只能让它「不太可能」而不是不可能。
     * 这不是热路径——每个用户最多每 270 秒铸一次。
     *
     * <p>用摘要而不是票本身作键，是为了不让凭证原文成为一个长期驻留的 map key。
     */
    private String oboKey(String audience, String userAccessToken) {
        return "obo|" + audience + "|" + sha256(userAccessToken);
    }

    private String serviceKey(String audience, TenantScope tenant) {
        return "svc|" + audience + "|" + tenant.orgId() + "|" + tenant.workspaceId();
    }

    /**
     * 清掉过期项。
     *
     * <p>不清的话这个 map 只增不减：OBO 键里嵌着用户票的摘要，而用户票每次静默续期
     * 都会换一张，于是一个长会话每几分钟就产生一个再也用不到的新键。
     * 在一个连续跑几周的容器里，那是一处稳定泄漏的死凭证。
     */
    private void evictExpired(LocalDateTime now) {
        cache.values().removeIf(token -> token.isExpiredAt(now));
    }

    private static String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 不验签地读刚铸出来的票。
     *
     * <p>安全，且只在这里安全：它刚由签发方经已认证的 TLS 通道发来，
     * 我们读它是为了决定自己出站请求里带什么（{@code tenant_id}），
     * 不是为了做授权判定——那发生在被调方，那里会验签。
     */
    private static JWTClaimsSet decodeWithoutVerification(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet();
        } catch (ParseException exception) {
            return null;
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        if (claims == null) {
            return null;
        }
        try {
            return claims.getStringClaim(name);
        } catch (ParseException exception) {
            return null;
        }
    }
}
