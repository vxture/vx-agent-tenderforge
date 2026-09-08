// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest;

import com.td.czghagent.application.command.cmd.CreateManagedUserCommand;
import com.td.czghagent.application.command.cmd.UpdateManagedUserCommand;
import com.td.czghagent.application.command.service.AdminCommandService;
import com.td.czghagent.application.query.service.AdminQueryService;
import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.model.PageResult;
import com.td.czghagent.rest.dto.CreateManagedUserRequest;
import com.td.czghagent.rest.dto.UpdateManagedUserRequest;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminQueryService queryService;
    private final AdminCommandService commandService;

    public AdminController(AdminQueryService queryService, AdminCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<ManagedUser>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest request
    ) {
        return ApiResponse.success(queryService.listUsers(
                RequestIdentity.user(request), page, size, keyword, roleCode, enabled
        ), RequestIdentity.traceId(request));
    }

    @PostMapping("/users")
    public ApiResponse<ManagedUser> createUser(@Valid @RequestBody CreateManagedUserRequest body,
                                                HttpServletRequest request) {
        ManagedUser user = commandService.create(new CreateManagedUserCommand(
                body.username(), body.displayName(), body.roleCode(), body.password()
        ), RequestIdentity.operation(request));
        return ApiResponse.success(user, RequestIdentity.traceId(request));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<ManagedUser> getUser(@PathVariable String userId, HttpServletRequest request) {
        return ApiResponse.success(queryService.getUser(RequestIdentity.user(request), userId),
                RequestIdentity.traceId(request));
    }

    @PatchMapping("/users/{userId}")
    public ApiResponse<ManagedUser> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateManagedUserRequest body,
            HttpServletRequest request
    ) {
        ManagedUser user = commandService.update(userId, new UpdateManagedUserCommand(
                body.displayName(), body.roleCode(), body.enabled(), body.password(), body.revision()
        ), RequestIdentity.operation(request));
        return ApiResponse.success(user, RequestIdentity.traceId(request));
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<ManagedUser> deactivateUser(@PathVariable String userId,
                                                    HttpServletRequest request) {
        return ApiResponse.success(
                commandService.deactivate(userId, RequestIdentity.operation(request)),
                RequestIdentity.traceId(request)
        );
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AuditLogEntry>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            HttpServletRequest request
    ) {
        return ApiResponse.success(queryService.listAuditLogs(
                RequestIdentity.user(request), page, size, keyword,
                actionCode, resultCode, startAt, endAt
        ), RequestIdentity.traceId(request));
    }
}
