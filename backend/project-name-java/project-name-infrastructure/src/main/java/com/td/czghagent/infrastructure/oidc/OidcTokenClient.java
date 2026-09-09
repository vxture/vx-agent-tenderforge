// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.czghagent.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 与 IdP 的 token 端点交互：授权码换票、刷新续期。
 *
 * <p>client 认证用 {@code client_secret_post}（表单里带 client_id / client_secret）
 * 而不是 Basic：两者都被规范允许，但表单形式在日志与抓包里更容易看清是哪个 client，
 * 而 Basic 头会被大多数日志脱敏规则整个抹掉，排障时反而看不见。
 */
@Component
public class OidcTokenClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcTokenClient.class);

    private final RestClient client;
    private final OidcProperties properties;
    private final OidcDiscovery discovery;

    public OidcTokenClient(RestClient.Builder builder, OidcProperties properties,
                           OidcDiscovery discovery) {
        this.client = builder.build();
        this.properties = properties;
        this.discovery = discovery;
    }

    /** 授权码 + PKCE verifier 换票。 */
    public TokenResponse exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("code_verifier", codeVerifier);
        return post(form, false);
    }

    /**
     * 刷新续期。
     *
     * <p>{@code invalid_grant} 即<strong>会话死亡</strong>，不重试：
     * 刷新令牌已被撤销或已轮换过一次，再试一次只会得到同一个答案。
     * 把它当成可重试的瞬时故障，会让用户卡在一个永远转圈的界面上。
     */
    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return post(form, true);
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private TokenResponse post(MultiValueMap<String, String> form, boolean refreshing) {
        try {
            JsonNode body = client.post()
                    .uri(discovery.document().tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("access_token").asText("").isBlank()) {
                throw failure(refreshing, "token 端点未返回 access_token", false);
            }
            return new TokenResponse(
                    body.path("access_token").asText(),
                    body.path("refresh_token").asText(null),
                    body.path("id_token").asText(null),
                    body.path("expires_in").asLong(300)
            );
        } catch (RestClientResponseException exception) {
            String oauthError = oauthErrorOf(exception.getResponseBodyAsString());
            // 只有服务端故障与限流值得重试；invalid_grant / invalid_client
            // 都需要人或流程介入，等待改变不了任何事。
            boolean retryable = exception.getStatusCode().is5xxServerError()
                    || exception.getStatusCode().value() == 429;
            throw failure(refreshing, "token 端点拒绝：" + oauthError, retryable);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("token endpoint call failed", exception);
            throw failure(refreshing, "身份服务暂时不可用", true);
        }
    }

    /**
     * 从错误体里取出 OAuth 错误码。
     *
     * <p>只取 {@code error} 字段，不把整个响应体带进异常消息：那里面可能回显请求参数，
     * 而请求参数里有 client_secret。
     */
    private String oauthErrorOf(String body) {
        if (body == null || body.isBlank()) {
            return "unknown";
        }
        int index = body.indexOf("\"error\"");
        return index < 0 ? "unknown" : body.substring(index, Math.min(body.length(), index + 64));
    }

    private BusinessException failure(boolean refreshing, String detail, boolean retryable) {
        LOGGER.warn("OIDC token exchange failed (refresh={}): {}", refreshing, detail);
        return refreshing
                ? new BusinessException(
                        "AUTH_SESSION_EXPIRED", "登录状态已失效，请重新登录", 401, false, null)
                : new BusinessException(
                        "AUTH_OIDC_EXCHANGE_FAILED", "登录未能完成，请重试", 401, retryable, null);
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String idToken,
            long expiresInSeconds
    ) {
    }
}
