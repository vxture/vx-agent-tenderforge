// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.PlatformCallerContext;
import com.td.czghagent.domain.model.ProductIdentity;
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

    private final PlatformSessionResolver platformSessions;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(PlatformSessionResolver platformSessions,
                                ObjectMapper objectMapper) {
        this.platformSessions = platformSessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                // 登录回路本身不能要求先登录。这三条是 C1 的入口、回调与
                // 平台反向登出通知，调用它们的时候按定义还没有会话。
                || "/api/auth/oidc/login".equals(path)
                || "/api/auth/oidc/callback".equals(path)
                || "/api/auth/oidc/backchannel-logout".equals(path)
                // 本地口令登录 /api/auth/login 已退役，刻意不在这张名单里：
                // 放行它等于重新打开一条绕过平台身份的入口。
                // 平台下发的开通/停用事件。调用方是平台，按定义没有会话；
                // 它的鉴权全部来自 HMAC 验签，而验签在控制器里是第一件事。
                // 取常量而不是再写一遍字面量：这一处与控制器映射分叉时，
                // 表现是平台的投递被登录过滤器挡在控制器之前，验签代码根本没跑，
                // 而平台那边只看见一个非 2xx。
                || ProductIdentity.PLATFORM_WEBHOOK_PATH.equals(path)
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
        // 身份只有一条通道：平台 RP 会话（HttpOnly cookie）。本地口令的 Bearer 通道
        // 2026-09-15 退役——Authorization 头在这里不被读取，库里残留的 user_session
        // 行因此全部失效，不需要逐条清理。
        String cookieValue = RpSessionCookie.read(request);
        CurrentUser user = null;
        String platformAccessToken = null;
        if (cookieValue != null) {
            com.td.czghagent.domain.model.RpSession session =
                    platformSessions.resolveSession(cookieValue).orElse(null);
            if (session != null) {
                user = session.toCurrentUser();
                platformAccessToken = session.accessToken();
            }
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
        // 出站调用点埋在十几层业务函数底下，而只有这里知道这次请求替谁在跑。
        try {
            PlatformCallerContext.run(user.tenant(), platformAccessToken, () -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException exception) {
                    // 受检异常穿不过 Runnable，包一层交给外面原样重抛——
                    // 在这里吞掉会把业务失败变成一个没有堆栈的 500。
                    throw new FilterFailure(exception);
                }
            });
        } catch (FilterFailure wrapper) {
            wrapper.rethrow();
        }
    }

    /** 只为把受检异常抬过 {@link PlatformCallerContext#run} 而存在，不逃出本类。 */
    private static final class FilterFailure extends RuntimeException {
        private FilterFailure(Exception cause) {
            super(cause);
        }

        private void rethrow() throws IOException, ServletException {
            Throwable cause = getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw (ServletException) cause;
        }
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
