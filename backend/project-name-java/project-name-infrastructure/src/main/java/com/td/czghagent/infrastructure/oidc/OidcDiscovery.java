// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.czghagent.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OIDC 发现文档。
 *
 * <p>端点地址<strong>从发现文档读，不写进配置</strong>：把 authorize / token / jwks
 * 三个地址各配一遍，等于把 IdP 的内部布局复制到每个接入方的 .env 里，
 * 而 IdP 换一次路径就要所有接入方同步改——那正是发现文档存在的理由。
 *
 * <p>缓存但会过期。永不过期的缓存会让 JWKS 轮换后全线失败；
 * 每次调用都拉一遍则会把 IdP 打成单点。
 */
@Component
public class OidcDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcDiscovery.class);

    private static final String WELL_KNOWN = "/.well-known/openid-configuration";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final RestClient client;
    private final OidcProperties properties;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public OidcDiscovery(RestClient.Builder builder, OidcProperties properties) {
        this.client = builder.build();
        this.properties = properties;
    }

    public Document document() {
        Cached current = cache.get();
        if (current != null && current.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return current.document();
        }
        Document fetched = fetch();
        cache.set(new Cached(fetched, Instant.now()));
        return fetched;
    }

    private Document fetch() {
        String url = properties.issuer() + WELL_KNOWN;
        try {
            JsonNode body = client.get().uri(url).retrieve().body(JsonNode.class);
            if (body == null) {
                throw configurationFailure("发现文档为空");
            }
            Document document = new Document(
                    text(body, "issuer"),
                    text(body, "authorization_endpoint"),
                    text(body, "token_endpoint"),
                    text(body, "jwks_uri"),
                    body.path("end_session_endpoint").asText(null)
            );
            // issuer 必须与配置精确一致。不一致意味着我们正在跟一个
            // 自称是它的东西对话——这正是发现文档最先要挡住的。
            if (!properties.issuer().equals(trimTrailingSlash(document.issuer()))) {
                throw configurationFailure("发现文档的 issuer 与配置不一致");
            }
            return document;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("OIDC discovery fetch failed for {}", url, exception);
            throw new BusinessException(
                    "AUTH_ISSUER_UNREACHABLE", "身份服务暂时不可用，请稍后重试", 503, true, null);
        }
    }

    /**
     * 配置问题不伪装成鉴权失败。
     *
     * <p>返回 500 而不是 401：监控要能区分「服务器配错了」和「调用方没权限」。
     * 这条是 Runos 的接口文档里点名过的一处。
     */
    private BusinessException configurationFailure(String message) {
        LOGGER.error("OIDC discovery invalid: {}", message);
        return new BusinessException(
                "AUTH_ISSUER_NOT_CONFIGURED", "身份服务配置有误：" + message, 500, false, null);
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("发现文档缺少必需字段：" + field);
        }
        return value;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** 只保留本 RP 用得到的端点；其余字段刻意不映射，用不到的字段没有维护理由。 */
    public record Document(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri,
            String endSessionEndpoint
    ) {
    }

    private record Cached(Document document, Instant fetchedAt) {
    }
}
