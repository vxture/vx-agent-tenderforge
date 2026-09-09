// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * RP 会话 cookie 的读写。
 *
 * <p>浏览器<strong>只</strong>拿这一个不透明值，平台 token 全部留在服务端
 * （产品接入通则 C1）。这不只是防窃取——access token 同时是 S2S 换票的原料，
 * 一旦下发前端，OBO 那条链就断了。
 */
public final class RpSessionCookie {

    /**
     * 生产环境用 {@code __Host-} 前缀。
     *
     * <p>这个前缀是浏览器强制的一组约束：必须 Secure、必须 Path=/、
     * <strong>不得带 Domain</strong>。最后一条是关键——它让 cookie 无法被
     * 同一父域下的兄弟站点写入，而子域投毒正是会话固定攻击的常见入口。
     *
     * <p>本地开发走 http，浏览器会因为 Secure 直接拒收带前缀的 cookie，
     * 所以另给一个不带前缀的名字。名字不同是有意的：它让「这是一个降级环境」
     * 在开发者工具里一眼可见，而不是靠记忆。
     */
    public static final String SECURE_NAME = "__Host-vx_rp_session";
    public static final String PLAIN_NAME = "vx_rp_session";

    private RpSessionCookie() {
    }

    public static String nameFor(boolean secure) {
        return secure ? SECURE_NAME : PLAIN_NAME;
    }

    /** 读出当前请求携带的会话值；两个名字都认，因为同一个浏览器可能残留旧的。 */
    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String plain = null;
        for (Cookie cookie : cookies) {
            if (SECURE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                // 带前缀的那个更可信，优先返回。
                return cookie.getValue();
            }
            if (PLAIN_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                plain = cookie.getValue();
            }
        }
        return plain;
    }

    /**
     * 写入会话 cookie。
     *
     * <p>{@code SameSite=Lax} 而不是 {@code Strict}：登录回调是从 IdP 域发起的
     * <strong>顶层导航</strong>，Strict 会让浏览器不带上刚种下的 cookie，
     * 表现为「登录成功后仍然是未登录」——一个只在真实跳转里才复现的故障。
     * Lax 放行顶层 GET 导航，同时仍然挡住跨站的表单提交与子资源请求。
     */
    public static void write(HttpServletResponse response, String value,
                             Duration maxAge, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(nameFor(secure), value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build()
                .toString());
    }

    /**
     * 清除。
     *
     * <p>两个名字都清：环境切换或前缀策略变化后，浏览器里可能同时躺着两个，
     * 只清一个会让用户「登出后刷新又回来了」。
     */
    public static void clear(HttpServletResponse response, boolean secure) {
        for (String name : new String[]{SECURE_NAME, PLAIN_NAME}) {
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "")
                    .httpOnly(true)
                    .secure(secure || SECURE_NAME.equals(name))
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(Duration.ZERO)
                    .build()
                    .toString());
        }
    }
}
