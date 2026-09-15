// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * 把产品根路径变成「已退出登录」确认页的那张便条。
 *
 * <p><strong>它解决的问题。</strong>IdP 的退出回跳地址是平台<strong>登记</strong>的——本产品登记的是
 * 根路径，也就是未登录引导页。按定义那里没有会话，于是主动退出被回以「登录」，看起来像退出失败。
 * 改成专门的已退出地址是平台侧的改动，产品改不了。
 *
 * <p><strong>所以产品给自己留一张便条。</strong>{@code POST /api/auth/logout} 在去 IdP 的路上种下它；
 * 前端在没有会话时读到它，就渲染确认页而不是引导页。它不是会话、不带身份、不授予任何东西——
 * 值只有字符串 {@code "1"}。
 *
 * <p><strong>第一次渲染即消费。</strong>确认页挂载时由客户端清掉，只显示一次；下一次打开任何地址
 * 回到引导页。120 秒的寿命是兜底（比如人停在 IdP 关了浏览器），不是机制本身。
 *
 * <p><strong>刻意不设 HttpOnly。</strong>清除必须在客户端做（渲染确认页的是前端，服务端此时拿不到
 * 那次页面访问），而脚本读到的只是它本来就知道的一个比特。
 *
 * <p>参照实现：vx-agent-yucer {@code app/auth/lib/signed-out-marker.ts}（门禁页规范）。
 */
public final class SignedOutMarker {

    public static final String NAME = "tenderforge_signed_out";

    /** 两分钟：够走完一次 IdP 往返，又短到一次中途放弃的退出不会影响半小时后的访问。 */
    public static final Duration MAX_AGE = Duration.ofSeconds(120);

    private SignedOutMarker() {
    }

    public static void write(HttpServletResponse response, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, "1")
                .httpOnly(false)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(MAX_AGE)
                .build()
                .toString());
    }
}
