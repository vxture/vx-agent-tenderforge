// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest;

import com.td.czghagent.application.command.cmd.LoginCommand;
import com.td.czghagent.application.command.service.AuthCommandService;
import com.td.czghagent.rest.dto.LoginRequest;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthCommandService authCommandService;

    public AuthController(AuthCommandService authCommandService) {
        this.authCommandService = authCommandService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthCommandService.LoginResult> login(@Valid @RequestBody LoginRequest request,
                                                             HttpServletRequest servletRequest) {
        AuthCommandService.LoginResult result = authCommandService.login(
                new LoginCommand(request.username(), request.password()),
                RequestIdentity.traceId(servletRequest), servletRequest.getRemoteAddr()
        );
        return ApiResponse.success(result, RequestIdentity.traceId(servletRequest));
    }

    @GetMapping("/me")
    public ApiResponse<com.td.czghagent.domain.model.CurrentUser> me(HttpServletRequest request) {
        return ApiResponse.success(RequestIdentity.user(request), RequestIdentity.traceId(request));
    }

    @DeleteMapping("/session")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authCommandService.logout(
                RequestIdentity.token(request), RequestIdentity.user(request),
                RequestIdentity.traceId(request), request.getRemoteAddr()
        );
        return ApiResponse.success(null, RequestIdentity.traceId(request));
    }
}
