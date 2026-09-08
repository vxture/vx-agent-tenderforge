// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.security;

import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.OperationContext;
import jakarta.servlet.http.HttpServletRequest;

public final class RequestIdentity {

    public static final String USER = RequestIdentity.class.getName() + ".user";
    public static final String TOKEN = RequestIdentity.class.getName() + ".token";
    public static final String TRACE_ID = RequestIdentity.class.getName() + ".traceId";
    public static final String TASK_ID = RequestIdentity.class.getName() + ".taskId";

    private RequestIdentity() {
    }

    /**
     * 调用方送来的 task_id，没送时为 null（产品接入通则 X-2）。
     *
     * <p>为空是合法状态，不要在这里兜底成 traceId：那会让调用记录里出现一个
     * 别人查不到的假聚合键，比空值更难排查。需要一个稳定值的长任务自己铸。
     */
    public static String taskId(HttpServletRequest request) {
        return (String) request.getAttribute(TASK_ID);
    }

    public static CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(USER);
    }

    public static String token(HttpServletRequest request) {
        return (String) request.getAttribute(TOKEN);
    }

    public static String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TRACE_ID);
    }

    public static OperationContext operation(HttpServletRequest request) {
        return new OperationContext(user(request), traceId(request), request.getRemoteAddr());
    }
}
