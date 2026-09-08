// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.application.command.cmd.LoginCommand;
import com.td.czghagent.application.command.service.AuthCommandService;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.rest.dto.LoginRequest;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
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

    public AuthController(AuthCommandService authCommandService) {
        this.authCommandService = authCommandService;
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
     * 登出。
     *
     * <p><strong>此前是 {@code DELETE /api/auth/session}</strong>。会话失效是一次状态迁移，
     * 不是「从目录移除一个叫 session 的资源」——{@code DELETE} 表达状态迁移，
     * 会让同一个动词在这个 API 里同时意味着两件事（B-4）。具名路由说清了做什么。
     *
     * <p>返回 204：没有载荷要回显，就不要造一个空对象来占位。
     */
    @PostMapping("/logout")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authCommandService.logout(
                RequestIdentity.token(request), RequestIdentity.user(request),
                RequestIdentity.traceId(request), request.getRemoteAddr()
        );
    }
}
