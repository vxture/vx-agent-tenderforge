// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.model.ConsoleLinks;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.security.RpSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话的两个非跳转端点：我是谁、登出。
 *
 * <p>登录本身在 {@link OidcAuthController}——身份只来自平台 IdP。
 * <strong>本地口令登录 2026-09-15 退役</strong>：{@code POST /api/auth/login} 与
 * Bearer 会话通道一起删除。留着它的代价是具体的：登录页早已不提供口令入口，
 * 接口却仍然活着，任何知道 {@code admin} 口令的人都能绕过平台身份，
 * C2 权益与 C3 计量随之失效，而界面上看不出任何异样。
 * {@code LocalPasswordChannelRetiredIntegrationTest} 守着它回不来。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OidcLoginService oidcLoginService;
    private final boolean secureCookie;
    private final String consoleBaseUrl;

    public AuthController(OidcLoginService oidcLoginService,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${app.oidc.secure-cookie:false}") boolean secureCookie,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${app.platform.console-url:}") String consoleBaseUrl) {
        this.oidcLoginService = oidcLoginService;
        this.secureCookie = secureCookie;
        this.consoleBaseUrl = consoleBaseUrl;
    }

    /**
     * 当前用户，外加它在平台控制台的资料页地址。
     *
     * <p>显示名与头像归平台 IdP，本产品只读——修改入口是控制台，不是这里。
     * 用户字段原样平铺（前端的会话恢复与角色判断读的就是它们），只在同一层多出
     * {@code consoleProfileUrl}；未配置控制台地址时为 {@code null}，界面据此不渲染入口。
     */
    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        return new MeResponse(RequestIdentity.user(request), ConsoleLinks.profile(consoleBaseUrl));
    }

    public record MeResponse(@JsonUnwrapped CurrentUser user, String consoleProfileUrl) {
    }

    /**
     * 登出——<strong>唯一的登出入口</strong>。
     *
     * <p>它撤销这次请求携带的平台 RP 会话并清掉 cookie。本地口令会话已随 Bearer 通道退役。
     *
     * <p><strong>此前是 {@code DELETE /api/auth/session}</strong>。会话失效是一次状态迁移，
     * 不是「从目录移除一个叫 session 的资源」（B-4）。
     *
     * <p>返回 204：没有载荷要回显，就不要造一个空对象来占位。
     */
    @PostMapping("/logout")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = RpSessionCookie.read(request);
        if (cookieValue != null) {
            oidcLoginService.logout(cookieValue, RequestIdentity.user(request),
                    RequestIdentity.traceId(request), request.getRemoteAddr());
            RpSessionCookie.clear(response, secureCookie);
        }
    }
}
