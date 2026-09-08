// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.application.query.service.AuthQueryService;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.rest.support.ApiResponse;
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
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(AuthQueryService authQueryService, ObjectMapper objectMapper) {
        this.authQueryService = authQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                || "/api/auth/login".equals(path)
                || path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request);
        CurrentUser user = token == null ? null : authQueryService.resolve(token).orElse(null);
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
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(
                "AUTH_REQUIRED", "登录状态已失效，请重新登录", RequestIdentity.traceId(request)
        ));
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(
                "ROLE_ACCESS_DENIED", "当前账号无权访问该业务入口", RequestIdentity.traceId(request)
        ));
    }
}
