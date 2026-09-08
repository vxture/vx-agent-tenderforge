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

    private RequestIdentity() {
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
