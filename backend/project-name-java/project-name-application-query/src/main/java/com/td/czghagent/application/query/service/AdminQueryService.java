// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.application.query.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.model.PageResult;
import com.td.czghagent.domain.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AdminQueryService {

    private final AdminRepository adminRepository;

    public AdminQueryService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public PageResult<ManagedUser> listUsers(CurrentUser user, int requestedPage, int requestedSize,
                                              String keyword, String roleCode, Boolean enabled) {
        requireAdmin(user);
        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        AdminRepository.UserFilter filter = new AdminRepository.UserFilter(
                trimToNull(keyword), normalizeRoleFilter(roleCode), enabled, (page - 1) * size, size
        );
        return PageResult.of(
                adminRepository.listUsers(filter), adminRepository.countUsers(filter), page, size
        );
    }

    public ManagedUser getUser(CurrentUser user, String userId) {
        requireAdmin(user);
        return adminRepository.findUserById(userId).orElseThrow(() ->
                new BusinessException("USER_NOT_FOUND", "用户不存在", 404));
    }

    public PageResult<AuditLogEntry> listAuditLogs(
            CurrentUser user, int requestedPage, int requestedSize, String keyword,
            String actionCode, String resultCode, LocalDateTime startAt, LocalDateTime endAt
    ) {
        requireAdmin(user);
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new BusinessException("AUDIT_TIME_RANGE_INVALID", "开始时间不能晚于结束时间", 400);
        }
        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        AdminRepository.AuditFilter filter = new AdminRepository.AuditFilter(
                trimToNull(keyword), trimToNull(actionCode), trimToNull(resultCode),
                startAt, endAt, (page - 1) * size, size
        );
        return PageResult.of(
                adminRepository.listAuditLogs(filter), adminRepository.countAuditLogs(filter), page, size
        );
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

    private int normalizePage(int value) {
        return Math.max(1, value);
    }

    private int normalizeSize(int value) {
        return Math.max(1, Math.min(value, 100));
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
