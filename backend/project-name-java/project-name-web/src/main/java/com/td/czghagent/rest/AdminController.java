// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.application.command.cmd.CreateManagedUserCommand;
import com.td.czghagent.application.command.cmd.UpdateManagedUserCommand;
import com.td.czghagent.application.command.service.AdminCommandService;
import com.td.czghagent.application.query.service.AdminQueryService;
import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.rest.dto.CreateManagedUserRequest;
import com.td.czghagent.rest.dto.UpdateManagedUserRequest;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地账号与审计的管理面。
 *
 * <p><strong>登记的偏离，带失效条件</strong>：账号的「算不算数」这里仍是布尔 {@code enabled}，
 * 而 B-3 要求单一字符串字段 {@code state}。理由不是迁移成本——是这整个资源即将被
 * 「平台 IdP 提供身份 + 本地只存 workspace 内业务角色」替换，那份新资源会一出生就用 {@code state}。
 * <strong>失效条件：本地账号体系被替换的那一刻，本条豁免作废。</strong>
 * 在此之前动词语义已经先行纠正（见下方 deactivate / activate）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminQueryService queryService;
    private final AdminCommandService commandService;

    public AdminController(AdminQueryService queryService, AdminCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** 有界管理面对象，返回裸数组——没有第二个键要回显，就不套信封（A-4）。 */
    @GetMapping("/users")
    public List<ManagedUser> listUsers(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest request
    ) {
        return queryService.listUsers(RequestIdentity.user(request), limit, keyword, roleCode, enabled);
    }

    @PostMapping("/users")
    public ManagedUser createUser(@Valid @RequestBody CreateManagedUserRequest body,
                                  HttpServletRequest request) {
        return commandService.create(new CreateManagedUserCommand(
                body.username(), body.displayName(), body.roleCode(), body.password()
        ), RequestIdentity.operation(request));
    }

    @GetMapping("/users/{userId}")
    public ManagedUser getUser(@PathVariable String userId, HttpServletRequest request) {
        return queryService.getUser(RequestIdentity.user(request), userId);
    }

    /** 部分更新走 PATCH：请求体里没出现的字段不改（B-1）。 */
    @PatchMapping("/users/{userId}")
    public ManagedUser updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateManagedUserRequest body,
            HttpServletRequest request
    ) {
        return commandService.update(userId, new UpdateManagedUserCommand(
                body.displayName(), body.roleCode(), body.enabled(), body.password(), body.revision()
        ), RequestIdentity.operation(request));
    }

    /**
     * 停用。<strong>此前是 {@code DELETE /users/{userId}}</strong>，而它做的从来不是删除——
     * 同一个动词在两处一个表软删、一个表停用，消费方写确认文案时无法只看动词判断后果，
     * 而这类操作的后果恰恰最需要在点之前说清（B-4）。
     */
    @PostMapping("/users/{userId}/deactivate")
    public ManagedUser deactivateUser(@PathVariable String userId, HttpServletRequest request) {
        return commandService.deactivate(userId, RequestIdentity.operation(request));
    }

    /** 启用。二元开关成对提供（B-3）。 */
    @PostMapping("/users/{userId}/activate")
    public ManagedUser activateUser(@PathVariable String userId, HttpServletRequest request) {
        return commandService.activate(userId, RequestIdentity.operation(request));
    }

    /**
     * 审计流水：无界，返回 {@code {items, nextCursor}}（A-3 / A-4）。
     *
     * <p>筛选一律走查询参数，路径段只留给资源标识（A-2）。
     */
    @GetMapping("/audit-logs")
    public CursorPage<AuditLogEntry> listAuditLogs(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            HttpServletRequest request
    ) {
        return queryService.listAuditLogs(
                RequestIdentity.user(request), limit, cursor, keyword,
                actionCode, resultCode, startAt, endAt
        );
    }
}
