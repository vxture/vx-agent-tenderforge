// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.application.command.cmd.LoginCommand;
import com.td.czghagent.application.command.service.AuthCommandService;
import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.rest.dto.LoginRequest;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.security.RpSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地口令登录。
 *
 * <p><strong>这是一条待退役的通道</strong>：平台契约下身份来自 IdP，
 * 走授权码 + PKCE，token 留在服务端会话里不下发浏览器。这里保留本地登录，
 * 是为了在 OIDC RP 接通之前产品仍然可用；接通之日整个 controller 被
 * {@code /auth/login} → {@code /auth/callback} 回路取代。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthCommandService authCommandService;
    private final OidcLoginService oidcLoginService;
    private final boolean secureCookie;

    public AuthController(AuthCommandService authCommandService,
                          OidcLoginService oidcLoginService,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${app.oidc.secure-cookie:false}") boolean secureCookie) {
        this.authCommandService = authCommandService;
        this.oidcLoginService = oidcLoginService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public AuthCommandService.LoginResult login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest servletRequest) {
        return authCommandService.login(
                new LoginCommand(request.username(), request.password()),
                RequestIdentity.traceId(servletRequest), servletRequest.getRemoteAddr()
        );
    }

    @GetMapping("/me")
    public CurrentUser me(HttpServletRequest request) {
        return RequestIdentity.user(request);
    }

    /**
     * 登出——<strong>唯一的登出入口</strong>。
     *
     * <p>它撤销这次请求携带的<strong>任何一种</strong>会话：平台 RP 会话（cookie）
     * 与本地口令会话（Bearer）。给两种登录各配一个登出端点，会让「登出本产品」
     * 这一件事有两个名字，而调用方得先判断自己是怎么进来的——那个判断本来就不该
     * 由调用方来做，它甚至不一定知道答案（cookie 是 HttpOnly 的）。
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
        String bearer = RequestIdentity.token(request);
        if (bearer != null) {
            authCommandService.logout(bearer, RequestIdentity.user(request),
                    RequestIdentity.traceId(request), request.getRemoteAddr());
        }
    }
}
