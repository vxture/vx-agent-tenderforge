// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest;

import com.td.czghagent.application.query.service.AdminQueryService;
import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.rest.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理面：审计流水。
 *
 * <p><strong>本地账号管理（{@code /api/admin/users*}）2026-09-15 退役。</strong>身份与账号归平台
 * IdP，本地口令通道此前已退役——那组端点管理的是再也登录不了的账号，在那里停用、改角色、
 * 重置口令都不再改变任何人能做什么，只制造「管过了」的错觉。原先登记的偏离（启停用布尔
 * {@code enabled} 而非 B-3 的 {@code state}）随资源一起销号。
 * {@code LocalAccountSurfacesRetiredIntegrationTest} 守着它们不会回来。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminQueryService queryService;

    public AdminController(AdminQueryService queryService) {
        this.queryService = queryService;
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
