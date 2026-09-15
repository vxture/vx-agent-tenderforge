// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登出不要求会话仍然有效——而且只有登出不要求。
 *
 * <p>会话过期的标签页点「退出登录」：以前在过滤器这里就被 401 挡下，门禁页的表单提交出去拿到一页 JSON，
 * 已退出便条也没种上。放宽只能放宽这一条：任何别的业务入口没有会话仍然是 401。
 */
class AuthenticationFilterLogoutTest {

    private PlatformSessionResolver sessions;
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        sessions = mock(PlatformSessionResolver.class);
        when(sessions.resolveSession(any())).thenReturn(Optional.empty());
        filter = new AuthenticationFilter(sessions, new ObjectMapper());
    }

    @Test
    void aLogoutWithoutAnySessionReachesTheController() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/logout"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aLogoutWithAnExpiredSessionCookieReachesTheController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.setCookies(new Cookie(RpSessionCookie.PLAIN_NAME, "expired"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void everyOtherRouteWithoutASessionIsStillUnauthorized() throws Exception {
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
                new MockHttpServletRequest("GET", "/api/auth/logout"),
                new MockHttpServletRequest("POST", "/api/bids"),
                new MockHttpServletRequest("GET", "/api/auth/me")}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as(request.getMethod() + " " + request.getRequestURI()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }
}
