// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.security;

import com.td.czghagent.domain.model.TaskContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 捕获调用方送来的 {@code task_id}（产品接入通则 X-2）。
 *
 * <p>{@code task_id} 是<strong>跨产品唯一的聚合键</strong>：一次 agent 任务同时用了能力和模型时，
 * 只有这个键能把消耗、动作和失败点拼回一起。所以被调方的义务是
 * <strong>原样落库</strong>——自产一个 request id 就算完是不够的，那个 id 在别人的库里查不到。
 *
 * <p>本过滤器只负责「接住并原样保留」。当调用方没有送（浏览器直接访问就没有），
 * 这里保持为空，由发起长任务的业务侧铸一个与业务聚合绑定的稳定值，
 * 而不是每个请求现编一个——每请求一个新值等于没有聚合键。
 *
 * <p>与 {@code traceId} 的分工：traceId 是本服务自产的诊断关联 id，进日志与响应头；
 * taskId 是跨产品契约字段，进调用记录与出站请求。两者不可互相顶替，
 * 也不合并成一个字段——合并之后就无法回答「这一条是谁给的」。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TaskIdFilter extends OncePerRequestFilter {

    /** HTTP 面的承载位置。MCP 面走 {@code _meta.vxture.task_id}，是同一个值的另一种承载。 */
    public static final String HEADER = "X-Vxture-Task-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String taskId = TaskContext.normalize(request.getHeader(HEADER));
        if (taskId != null) {
            request.setAttribute(RequestIdentity.TASK_ID, taskId);
            MDC.put("taskId", taskId);
        }
        try {
            TaskContext.run(taskId, () -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException exception) {
                    // 受检异常穿不过 Runnable，包一层交给外面原样重抛——
                    // 在这里吞掉会把业务失败变成一个没有堆栈的 500。
                    throw new FilterExecutionException(exception);
                }
            });
        } catch (FilterExecutionException wrapper) {
            wrapper.rethrow();
        } finally {
            MDC.remove("taskId");
        }
    }

    /** 只为把受检异常抬过 {@link TaskContext#run} 而存在，不逃出本类。 */
    private static final class FilterExecutionException extends RuntimeException {
        private FilterExecutionException(Exception cause) {
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
}
