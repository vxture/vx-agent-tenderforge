// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话 cookie 的属性。
 *
 * <p>这几条全是<strong>安全属性</strong>，而安全属性的共同点是：写错了功能照常，
 * 只是防线没了。少一个 HttpOnly，一次 XSS 就能把会话读走；
 * SameSite 写成 Strict，登录回调那一跳浏览器不带 cookie，
 * 表现为「登录成功后仍然未登录」——一个只在真实跳转里复现的故障。
 */
class RpSessionCookieTest {

    @Test
    void alwaysMarksTheSessionCookieHttpOnly() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        RpSessionCookie.write(response, "opaque-value", Duration.ofHours(12), false);

        assertThat(setCookieHeaders(response))
                .singleElement()
                .satisfies(header -> {
                    assertThat(header).contains("HttpOnly");
                    assertThat(header).contains("Path=/");
                });
    }

    /**
     * {@code SameSite=Lax} 而不是 Strict。
     *
     * <p>登录回调是从 IdP 域发起的顶层导航；Strict 会让浏览器不带上刚种下的 cookie。
     * Lax 放行顶层 GET 导航，同时仍挡住跨站表单提交与子资源请求。
     */
    @Test
    void usesLaxSoTheLoginRedirectCarriesTheCookieBack() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        RpSessionCookie.write(response, "opaque-value", Duration.ofHours(12), false);

        assertThat(setCookieHeaders(response).get(0)).contains("SameSite=Lax");
    }

    /**
     * 生产用 {@code __Host-} 前缀，且必须 Secure。
     *
     * <p>这个前缀是浏览器强制的一组约束，其中「不得带 Domain」是关键——
     * 它让 cookie 无法被同一父域下的兄弟站点写入，而子域投毒正是会话固定的常见入口。
     */
    @Test
    void usesTheHostPrefixOnlyTogetherWithSecure() {
        MockHttpServletResponse secure = new MockHttpServletResponse();
        RpSessionCookie.write(secure, "v", Duration.ofHours(1), true);

        String header = setCookieHeaders(secure).get(0);
        assertThat(header).startsWith(RpSessionCookie.SECURE_NAME + "=");
        assertThat(header).contains("Secure");
        assertThat(header)
                .as("__Host- 前缀要求不得带 Domain，带了浏览器会整个拒收")
                .doesNotContain("Domain");
    }

    @Test
    void usesAPlainNameWhenNotSecureSoLocalHttpStillWorks() {
        MockHttpServletResponse plain = new MockHttpServletResponse();
        RpSessionCookie.write(plain, "v", Duration.ofHours(1), false);

        assertThat(setCookieHeaders(plain).get(0)).startsWith(RpSessionCookie.PLAIN_NAME + "=");
    }

    /** 带前缀的更可信，同时存在时优先读它。 */
    @Test
    void prefersThePrefixedCookieWhenBothAreCarried() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(RpSessionCookie.PLAIN_NAME, "plain-value"),
                new Cookie(RpSessionCookie.SECURE_NAME, "secure-value"));

        assertThat(RpSessionCookie.read(request)).isEqualTo("secure-value");
    }

    @Test
    void readsNothingWhenNoSessionCookieIsPresent() {
        assertThat(RpSessionCookie.read(new MockHttpServletRequest())).isNull();

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie(RpSessionCookie.PLAIN_NAME, ""));
        assertThat(RpSessionCookie.read(blank)).isNull();
    }

    /**
     * 登出必须清掉<strong>两个</strong>名字。
     *
     * <p>环境切换或前缀策略变化后，浏览器里可能同时躺着两个；
     * 只清一个会让用户「登出后刷新又回来了」。
     */
    @Test
    void clearsBothCookieNamesOnLogout() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        RpSessionCookie.clear(response, false);

        assertThat(setCookieHeaders(response)).hasSize(2)
                .allSatisfy(header -> assertThat(header).contains("Max-Age=0"));
    }

    private static List<String> setCookieHeaders(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie");
    }
}
