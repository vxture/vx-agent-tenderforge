// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.rest.security.RpSessionCookie;
import com.td.czghagent.rest.security.SignedOutMarker;
import com.td.czghagent.rest.support.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登出的两个入口与已退出便条。
 *
 * <p>便条漏种的样子不报错：人从 IdP 回到根路径，看到的是「登录」，以为退出失败了。
 * 表单那一支漏了，门禁页上的「退出登录」提交出去拿到一页 JSON。
 */
class AuthLogoutTest {

    private static final String END_SESSION =
            "https://accounts.example.test/oidc/end_session?client_id=tenderforge";

    private OidcLoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = mock(OidcLoginService.class);
    }

    private MockMvc mvc(boolean secureCookie) {
        return MockMvcBuilders.standaloneSetup(new AuthController(loginService, secureCookie, ""))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void theHeaderMenuLogoutEchoesTheEndSessionUrlAndLeavesTheMarker() throws Exception {
        when(loginService.logout(eq("session-1"), any(), any(), any())).thenReturn(END_SESSION);

        MockHttpServletResponse response = mvc(false).perform(post("/api/auth/logout")
                        .cookie(new Cookie(RpSessionCookie.PLAIN_NAME, "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoutUrl").value(END_SESSION))
                .andReturn().getResponse();

        assertThat(marker(response)).isNotNull();
        assertThat(sessionCookiesCleared(response)).isTrue();
    }

    /** 门禁页的表单：直接 302 去平台登出端点，便条一并种下。 */
    @Test
    void aFormLogoutRedirectsToTheEndSessionUrlWithTheMarker() throws Exception {
        when(loginService.logout(eq("session-1"), any(), any(), any())).thenReturn(END_SESSION);

        MockHttpServletResponse response = mvc(false).perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .cookie(new Cookie(RpSessionCookie.PLAIN_NAME, "session-1")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, END_SESSION))
                .andReturn().getResponse();

        assertThat(marker(response)).isNotNull();
        assertThat(sessionCookiesCleared(response)).isTrue();
    }

    /** 没有登出地址（替身身份、身份服务不可达）：回根路径，由便条换成确认页，而不是停在一页 JSON。 */
    @Test
    void aFormLogoutWithoutAnEndSessionUrlReturnsToTheRoot() throws Exception {
        when(loginService.logout(anyString(), any(), any(), any())).thenReturn(null);

        MockHttpServletResponse response = mvc(false).perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .cookie(new Cookie(RpSessionCookie.PLAIN_NAME, "session-1")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/"))
                .andReturn().getResponse();

        assertThat(marker(response)).isNotNull();
    }

    /** 本地 cookie 已经没了：不去撤销不存在的会话，照样去平台登出端点、照样留便条。 */
    @Test
    void aLogoutWithoutASessionCookieStillEndsThePlatformSession() throws Exception {
        when(loginService.endSessionUrl()).thenReturn(END_SESSION);

        MockHttpServletResponse response = mvc(false).perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, END_SESSION))
                .andReturn().getResponse();

        verify(loginService, never()).logout(any(), any(), any(), any());
        assertThat(marker(response)).isNotNull();
    }

    /**
     * 便条的属性逐项核对：值只是 1；不设 HttpOnly（确认页要在客户端清掉它）；两分钟；
     * 全站路径；SameSite=Lax 让它跟着从 IdP 回来的顶层导航带回来。
     */
    @Test
    void theMarkerIsAReadableTwoMinuteSiteWideLaxCookie() throws Exception {
        when(loginService.endSessionUrl()).thenReturn(END_SESSION);

        String local = marker(mvc(false).perform(post("/api/auth/logout")).andReturn().getResponse());
        String https = marker(mvc(true).perform(post("/api/auth/logout")).andReturn().getResponse());

        assertThat(local).startsWith(SignedOutMarker.NAME + "=1;")
                .contains("Path=/", "Max-Age=120", "SameSite=Lax")
                .doesNotContain("HttpOnly", "Secure");
        assertThat(https).contains("Secure").doesNotContain("HttpOnly");
    }

    private static String marker(MockHttpServletResponse response) {
        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        return cookies.stream().filter(value -> value.startsWith(SignedOutMarker.NAME + "=")).findFirst().orElse(null);
    }

    private static boolean sessionCookiesCleared(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(value -> value.startsWith(RpSessionCookie.PLAIN_NAME + "=;") && value.contains("Max-Age=0"));
    }
}
