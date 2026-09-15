// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C1 登录回路的 HTTP 面：跳转、回调种会话、反向登出。
 *
 * <p>这一面出错的样子都很安静：用户在 IdP 点「取消」看到内部错误页；
 * 会话 cookie 少了 {@code HttpOnly} 或误用 {@code SameSite=Strict}，登录成功后仍是未登录；
 * 反向登出答 401 或被缓存，平台以为送达了，会话其实没撤。
 */
class OidcAuthControllerTest {

    private static final long SESSION_SECONDS = 43_200;

    private OidcLoginService loginService;
    private OidcGateway gateway;

    @BeforeEach
    void setUp() {
        loginService = mock(OidcLoginService.class);
        gateway = mock(OidcGateway.class);
    }

    private MockMvc mvc(boolean secureCookie) {
        return MockMvcBuilders.standaloneSetup(new OidcAuthController(loginService, gateway, SESSION_SECONDS, secureCookie))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── 发起登录 ──────────────────────────────────────────────────────────

    /** 回跳地址原样交给服务层白名单化，控制器不自己拼、不自己放行。 */
    @Test
    void loginRedirectsToTheAuthorizationUrlTheServiceBuilt() throws Exception {
        when(loginService.beginAuthorization("/planner/bids/42"))
                .thenReturn("https://idp.example.test/authorize?state=s1");

        mvc(false).perform(get("/api/auth/oidc/login").param("returnTo", "/planner/bids/42"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://idp.example.test/authorize?state=s1"));

        verify(loginService).beginAuthorization("/planner/bids/42");
    }

    // ── 回调 ─────────────────────────────────────────────────────────────

    /** 用户在 IdP 点了取消：给出具名的 401，而不是让回调去换一个不存在的 code。 */
    @Test
    void anIdpErrorIsANamedRejectionAndNeverReachesTheTokenExchange() throws Exception {
        mvc(false).perform(get("/api/auth/oidc/callback")
                        .param("error", "access_denied").param("state", "s1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OIDC_REJECTED"))
                .andExpect(jsonPath("$.message").value("身份服务未完成授权：access_denied"))
                .andExpect(jsonPath("$.retryable").value(false));

        verifyNoInteractions(loginService);
    }

    @Test
    void aCallbackWithoutStateIsAnExpiredLogin() throws Exception {
        mvc(false).perform(get("/api/auth/oidc/callback").param("code", "c1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OIDC_STATE_INVALID"));

        verifyNoInteractions(loginService);
    }

    @Test
    void aCallbackWithoutCodeIsAnExpiredLogin() throws Exception {
        mvc(false).perform(get("/api/auth/oidc/callback").param("state", "s1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OIDC_STATE_INVALID"));

        verifyNoInteractions(loginService);
    }

    /** 空白的 error 参数不算 IdP 报错——有的代理会把空参数带上。 */
    @Test
    void aBlankErrorParameterDoesNotBlockAValidCallback() throws Exception {
        when(loginService.completeAuthorization(anyString(), anyString(), any(), any()))
                .thenReturn(new OidcLoginService.CallbackResult("cookie-value", null, "/planner"));

        mvc(false).perform(get("/api/auth/oidc/callback")
                        .param("error", " ").param("state", "s1").param("code", "c1"))
                .andExpect(status().isFound());
    }

    /**
     * 回调成功：种会话 cookie、跳回原页面。
     *
     * <p>cookie 属性逐项核对：{@code HttpOnly} 挡脚本读取；{@code SameSite=Lax} 而不是 Strict——
     * 回调是从 IdP 域发起的顶层导航，Strict 会让浏览器不带上刚种下的 cookie；
     * 本地（非 HTTPS）不带 {@code Secure}，否则浏览器直接丢弃。
     */
    @Test
    void aSuccessfulCallbackPlantsTheSessionCookieAndReturnsToTheOriginalPage() throws Exception {
        when(loginService.completeAuthorization("s1", "c1", "trace-1", "203.0.113.9"))
                .thenReturn(new OidcLoginService.CallbackResult("cookie-value", null, "/planner/bids/42"));

        mvc(false).perform(get("/api/auth/oidc/callback").param("state", "s1").param("code", "c1")
                        .requestAttr(RequestIdentity.TRACE_ID, "trace-1")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        }))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/planner/bids/42"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        startsWith("vx_rp_session=cookie-value;"),
                        containsString("Path=/"),
                        containsString("Max-Age=" + SESSION_SECONDS),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                        not(containsString("Secure")))));

        verify(loginService).completeAuthorization("s1", "c1", "trace-1", "203.0.113.9");
    }

    /** 部署态走 HTTPS：带 __Host- 前缀与 Secure，浏览器据此拒绝子域覆写。 */
    @Test
    void onHttpsTheSessionCookieIsHostPrefixedAndSecure() throws Exception {
        when(loginService.completeAuthorization(anyString(), anyString(), any(), any()))
                .thenReturn(new OidcLoginService.CallbackResult("cookie-value", null, "/planner"));

        mvc(true).perform(get("/api/auth/oidc/callback").param("state", "s1").param("code", "c1"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        startsWith("__Host-vx_rp_session=cookie-value;"),
                        containsString("Secure"),
                        containsString("HttpOnly"))));
    }

    /** 服务层判定 state 失效或 code 换票失败：原码原样作答，不种 cookie。 */
    @Test
    void aFailedExchangeKeepsTheServiceCodeAndPlantsNoCookie() throws Exception {
        when(loginService.completeAuthorization(anyString(), anyString(), any(), any()))
                .thenThrow(new BusinessException("AUTH_OIDC_STATE_INVALID", "登录请求已失效，请重新登录", 401, false, null));

        mvc(false).perform(get("/api/auth/oidc/callback").param("state", "s1").param("code", "c1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OIDC_STATE_INVALID"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ── 反向登出 ──────────────────────────────────────────────────────────

    @Test
    void aValidLogoutTokenRevokesEverySessionOfItsSubjectAndIsNotCacheable() throws Exception {
        when(gateway.subjectOfLogoutToken("signed-token")).thenReturn("subject-1");

        mvc(false).perform(post("/api/auth/oidc/backchannel-logout").param("logout_token", "signed-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verify(loginService).backChannelLogout("subject-1");
    }

    /**
     * 验签不过是 400 而不是 401：401 的语义是「换张凭证再来」，平台会当成可重试；
     * 一张签名不过的 logout_token 重投多少次都一样。
     */
    @Test
    void aLogoutTokenThatFailsVerificationIs400AndRevokesNothing() throws Exception {
        when(gateway.subjectOfLogoutToken("forged-token"))
                .thenThrow(new BusinessException("AUTH_LOGOUT_TOKEN_INVALID", "logout_token 验签失败", 401, false, null));

        mvc(false).perform(post("/api/auth/oidc/backchannel-logout").param("logout_token", "forged-token"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verify(loginService, never()).backChannelLogout(anyString());
    }

    @Test
    void aMissingLogoutTokenIs400WithoutAskingTheGateway() throws Exception {
        mvc(false).perform(post("/api/auth/oidc/backchannel-logout").param("logout_token", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verifyNoInteractions(gateway, loginService);
    }

    @Test
    void reportsWhetherTheIdentityGatewayIsAStandIn() {
        when(gateway.isMock()).thenReturn(true);

        assertThat(new OidcAuthController(loginService, gateway, SESSION_SECONDS, false).usesMockIdentity()).isTrue();
    }
}
