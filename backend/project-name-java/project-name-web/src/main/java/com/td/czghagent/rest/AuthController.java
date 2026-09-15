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
import com.td.czghagent.rest.security.SignedOutMarker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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
     * <p>它撤销这次请求携带的平台 RP 会话、清掉 cookie，并回显平台登出端点地址
     * {@code logoutUrl}。浏览器必须顶层导航过去，账户中心的会话才会结束、
     * 人才会回到登记的 {@code post_logout_redirect_uri}（通则 C1）；
     * 只删本地会话时，再点登录会被静默 SSO 直接送回来，换不了账号。
     * {@code logoutUrl} 为 {@code null} 时（替身身份、配置不全、身份服务不可达）退回站内登录页。
     *
     * <p>本地 cookie 已经没了也照样回显地址：本地会话过期不代表账户中心会话也结束了。
     *
     * <p><strong>此前是 {@code DELETE /api/auth/session}</strong>。会话失效是一次状态迁移，
     * 不是「从目录移除一个叫 session 的资源」（B-4）。
     */
    @PostMapping("/logout")
    public LogoutResponse logout(HttpServletRequest request, HttpServletResponse response) {
        return new LogoutResponse(endSession(request, response));
    }

    /**
     * 同一个登出，由真实的 {@code <form method="post">} 提交。
     *
     * <p>门禁页上的「退出登录」是表单而不是脚本点击：这几页各自只有一个出路，出路不能依赖脚本
     * 是否已经跑起来（门禁页规范）。表单拿不到 JSON，所以这一支直接 302 到平台登出端点；
     * 没有登出地址时回根路径——那里读到已退出便条，显示确认页。
     */
    @PostMapping(path = "/logout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> logoutByForm(HttpServletRequest request, HttpServletResponse response) {
        String logoutUrl = endSession(request, response);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(logoutUrl == null ? "/" : logoutUrl))
                .build();
    }

    private String endSession(HttpServletRequest request, HttpServletResponse response) {
        // 两支都种：页头账号菜单走 fetch，门禁页走表单，回到根路径时都该看到确认页。
        SignedOutMarker.write(response, secureCookie);
        String cookieValue = RpSessionCookie.read(request);
        if (cookieValue == null) {
            return oidcLoginService.endSessionUrl();
        }
        String logoutUrl = oidcLoginService.logout(cookieValue, RequestIdentity.user(request),
                RequestIdentity.traceId(request), request.getRemoteAddr());
        RpSessionCookie.clear(response, secureCookie);
        return logoutUrl;
    }

    public record LogoutResponse(String logoutUrl) {
    }
}
