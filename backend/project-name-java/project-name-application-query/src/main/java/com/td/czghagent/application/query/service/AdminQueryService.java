// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.application.query.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.model.CursorPage;
import com.td.czghagent.domain.model.PageCursor;
import com.td.czghagent.domain.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminQueryService {

    private final AdminRepository adminRepository;

    public AdminQueryService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    /**
     * 账号列表：有界管理面对象，返回裸数组（产品接入通则 A-4）。
     *
     * <p>{@code limit} 由服务端钳制到 {@link CursorPage#MAX_LIMIT}，超过不报错也不静默丢弃语义
     * ——调用方看到返回条数等于上限就知道还有；账号集合不会无限增长，所以不给游标。
     */
    public List<ManagedUser> listUsers(CurrentUser user, Integer requestedLimit,
                                       String keyword, String roleCode, Boolean enabled) {
        requireAdmin(user);
        AdminRepository.UserFilter filter = new AdminRepository.UserFilter(
                trimToNull(keyword), normalizeRoleFilter(roleCode), enabled,
                CursorPage.clampLimit(requestedLimit)
        );
        return adminRepository.listUsers(filter);
    }

    public ManagedUser getUser(CurrentUser user, String userId) {
        requireAdmin(user);
        return adminRepository.findUserById(userId).orElseThrow(() ->
                new BusinessException("USER_NOT_FOUND", "用户不存在", 404));
    }

    /**
     * 审计流水：无界流水，返回 {@code {items, nextCursor}}（产品接入通则 A-3 / A-4）。
     *
     * <p>多取一条来判断有没有下一页，比再发一次 count 便宜，而且不会因为两条查询之间
     * 有新事件写入而给出自相矛盾的答案。多出来的那条不返回，只用来铸游标。
     */
    public CursorPage<AuditLogEntry> listAuditLogs(
            CurrentUser user, Integer requestedLimit, String cursor, String keyword,
            String actionCode, String resultCode, LocalDateTime startAt, LocalDateTime endAt
    ) {
        requireAdmin(user);
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new BusinessException("AUDIT_TIME_RANGE_INVALID", "开始时间不能晚于结束时间", 400);
        }
        int limit = CursorPage.clampLimit(requestedLimit);
        PageCursor from = PageCursor.decode(cursor);
        AdminRepository.AuditFilter filter = new AdminRepository.AuditFilter(
                trimToNull(keyword), trimToNull(actionCode), trimToNull(resultCode),
                startAt, endAt,
                from == null ? null : from.createdAt(), from == null ? null : from.id(),
                limit + 1
        );
        List<AuditLogEntry> rows = adminRepository.listAuditLogs(filter);
        if (rows.size() <= limit) {
            return CursorPage.of(rows, null);
        }
        List<AuditLogEntry> page = rows.subList(0, limit);
        AuditLogEntry last = page.get(limit - 1);
        return CursorPage.of(page, new PageCursor(last.occurredAt(), last.eventId()).encode());
    }

    private String normalizeRoleFilter(String value) {
        String role = trimToNull(value);
        if (role == null) {
            return null;
        }
        role = role.toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(role) && !"PLANNER".equals(role)) {
            throw new BusinessException("USER_ROLE_INVALID", "用户角色筛选不合法", 400);
        }
        return role;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.isAdmin()) {
            throw new BusinessException("ADMIN_ACCESS_DENIED", "仅系统管理员可访问该功能", 403);
        }
    }
}
