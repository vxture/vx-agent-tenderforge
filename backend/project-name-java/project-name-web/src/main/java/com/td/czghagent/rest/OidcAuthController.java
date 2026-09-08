// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.security.RpSessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * C1 登录回路的 HTTP 面。
 *
 * <p>这一组路由<strong>返回跳转而不是 JSON</strong>：它们是浏览器顶层导航的一部分，
 * 不是给前端 fetch 调的。前端只需要知道「把用户送到 /api/auth/oidc/login」。
 *
 * <p>本地口令登录（{@code AuthController}）在此期间并存。两条通道同时活着是过渡态，
 * 不是设计：平台身份接通并验证后，本地那条连同 {@code app_user} 一起退役，
 * 本地只保留 workspace 内的业务角色。
 */
@RestController
@RequestMapping("/api/auth/oidc")
public class OidcAuthController {

    private final OidcLoginService loginService;
    private final OidcGateway gateway;
    private final long sessionSeconds;
    private final boolean secureCookie;

    public OidcAuthController(
            OidcLoginService loginService,
            OidcGateway gateway,
            @Value("${app.oidc.session-seconds:43200}") long sessionSeconds,
            @Value("${app.oidc.secure-cookie:false}") boolean secureCookie
    ) {
        this.loginService = loginService;
        this.gateway = gateway;
        this.sessionSeconds = sessionSeconds;
        this.secureCookie = secureCookie;
    }

    /**
     * 发起登录：跳转到 IdP。
     *
     * <p>{@code returnTo} 已在服务层白名单化——只接受站内绝对路径。
     * 开放重定向是登录回路上最经典的一处漏洞：用户在<strong>真实的</strong>登录页
     * 完成认证后被送到钓鱼站，而地址栏全程可信。
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @RequestParam(required = false) String returnTo) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(loginService.beginAuthorization(returnTo)))
                .build();
    }

    /**
     * IdP 回调。
     *
     * <p>IdP 报错时（用户取消授权、client 配置错）参数是 {@code error} 而不是 {@code code}。
     * 这条路径必须显式处理：漏掉它，用户点「取消」会看到一个内部错误页面，
     * 而那让人以为是产品坏了。
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (error != null && !error.isBlank()) {
            throw new BusinessException(
                    "AUTH_OIDC_REJECTED", "身份服务未完成授权：" + error, 401, false, null);
        }
        if (state == null || code == null) {
            throw new BusinessException(
                    "AUTH_OIDC_STATE_INVALID", "登录请求已失效，请重新登录", 401, false, null);
        }
        OidcLoginService.CallbackResult result = loginService.completeAuthorization(
                state, code, RequestIdentity.traceId(request), request.getRemoteAddr());

        RpSessionCookie.write(response, result.cookieValue(),
                Duration.ofSeconds(sessionSeconds), secureCookie);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.returnTo()))
                .build();
    }

    /**
     * 登出。
     *
     * <p>返回 204 并清 cookie，<strong>不</strong>顺带跳转到 IdP 的 end_session：
     * 那是「从整个平台登出」，语义比「从这个产品登出」大得多，
     * 不该由一个产品的登出按钮替用户决定。需要全局登出时由 console 发起。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        loginService.logout(
                RpSessionCookie.read(request), RequestIdentity.user(request),
                RequestIdentity.traceId(request), request.getRemoteAddr());
        RpSessionCookie.clear(response, secureCookie);
    }

    /**
     * 反向登出接收端。
     *
     * <p>平台 POST 一个签名的 {@code logout_token}（表单字段，不是 JSON——
     * 这是规范定的，不是偏好）。验签通过后撤销该 subject 在本产品的<strong>全部</strong>会话。
     *
     * <p><strong>响应必须带 {@code Cache-Control: no-store}</strong>（规范 §2.8）：
     * 一个被缓存的登出响应会让后续的登出通知被中间层直接答复，而会话根本没被撤销。
     *
     * <p>验签失败返回 400 而不是 401。这一条容易搞反：401 的语义是
     * 「换张凭证再来」，会让平台把这次投递当成可重试的；而一张签名不过的
     * logout_token 重投多少次都一样。
     */
    @PostMapping("/backchannel-logout")
    public ResponseEntity<Void> backChannelLogout(
            @RequestParam(name = "logout_token", required = false) String logoutToken) {
        if (logoutToken == null || logoutToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        try {
            loginService.backChannelLogout(gateway.subjectOfLogoutToken(logoutToken));
        } catch (BusinessException exception) {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    /** 供 {@code /api/status} 与自证页读取的实现类型。 */
    public boolean usesMockIdentity() {
        return gateway.isMock();
    }
}
