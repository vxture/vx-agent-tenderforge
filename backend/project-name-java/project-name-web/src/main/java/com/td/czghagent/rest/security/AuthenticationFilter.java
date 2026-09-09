// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.application.query.service.AuthQueryService;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.rest.support.ErrorEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationFilter extends OncePerRequestFilter {

    private final AuthQueryService authQueryService;
    private final PlatformSessionResolver platformSessions;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(AuthQueryService authQueryService,
                                PlatformSessionResolver platformSessions,
                                ObjectMapper objectMapper) {
        this.authQueryService = authQueryService;
        this.platformSessions = platformSessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                || "/api/auth/login".equals(path)
                // 登录回路本身不能要求先登录。这三条是 C1 的入口、回调与
                // 平台反向登出通知，调用它们的时候按定义还没有会话。
                || "/api/auth/oidc/login".equals(path)
                || "/api/auth/oidc/callback".equals(path)
                || "/api/auth/oidc/backchannel-logout".equals(path)
                // 平台下发的开通/停用事件。调用方是平台，按定义没有会话；
                // 它的鉴权全部来自 HMAC 验签，而验签在控制器里是第一件事。
                || "/api/platform/provisioning/webhook".equals(path)
                // 运行时探针必须公开：探测方是编排器和平台健康页，它们没有会话。
                || "/api/health".equals(path)
                || "/api/ready".equals(path)
                || path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 两条身份通道并存：平台 RP 会话（cookie）优先，本地口令会话（Bearer）其次。
        // 顺序不是偏好——RP 会话是目标形态，本地那条是待退役的过渡通道，
        // 反过来会让一个残留的旧 Bearer 盖掉刚建立的平台身份。
        String cookieValue = RpSessionCookie.read(request);
        CurrentUser user = null;
        String token = null;
        if (cookieValue != null) {
            user = platformSessions.resolve(cookieValue).orElse(null);
        }
        if (user == null) {
            token = bearerToken(request);
            user = token == null ? null : authQueryService.resolve(token).orElse(null);
        }
        if (user == null) {
            writeUnauthorized(request, response);
            return;
        }
        if (!hasRoleAccess(request.getRequestURI(), user)) {
            writeForbidden(request, response);
            return;
        }
        request.setAttribute(RequestIdentity.USER, user);
        request.setAttribute(RequestIdentity.TOKEN, token);
        filterChain.doFilter(request, response);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    private boolean hasRoleAccess(String path, CurrentUser user) {
        if (path.startsWith("/api/admin")) {
            return user.isAdmin();
        }
        return true;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorEnvelope.of(
                "AUTH_SESSION_INVALID", "登录状态已失效，请重新登录", false
        ));
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorEnvelope.of(
                "AUTH_ROLE_DENIED", "当前账号无权访问该业务入口", false
        ));
    }
}
