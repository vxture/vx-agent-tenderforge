// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.application.command.service;

import com.td.czghagent.application.command.cmd.CreateManagedUserCommand;
import com.td.czghagent.application.command.cmd.UpdateManagedUserCommand;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.UserAccount;
import com.td.czghagent.domain.port.PasswordHasher;
import com.td.czghagent.domain.repository.AdminRepository;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.AuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AdminCommandService {

    private final AdminRepository adminRepository;
    private final AuthRepository authRepository;
    private final AuditRepository auditRepository;
    private final PasswordHasher passwordHasher;

    public AdminCommandService(AdminRepository adminRepository, AuthRepository authRepository,
                               AuditRepository auditRepository, PasswordHasher passwordHasher) {
        this.adminRepository = adminRepository;
        this.authRepository = authRepository;
        this.auditRepository = auditRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * <p><b>Preconditions:</b>操作者为管理员，用户名、姓名、角色和密码已通过Web层校验。</p>
     * <p><b>Side Effects:</b>创建启用账号并写入不含密码的审计日志。</p>
     * <p><b>Error Semantics:</b>用户名重复返回USER_USERNAME_EXISTS。</p>
     */
    @Transactional
    public ManagedUser create(CreateManagedUserCommand command, OperationContext context) {
        requireAdmin(context);
        String username = command.username().trim().toLowerCase(Locale.ROOT);
        String roleCode = requireRole(command.roleCode());
        if (authRepository.findByUsername(username).isPresent()) {
            throw new BusinessException("USER_USERNAME_EXISTS", "该用户名已存在", 409);
        }
        String userId = UUID.randomUUID().toString();
        authRepository.insertUser(new UserAccount(
                userId, username, passwordHasher.hash(command.password()),
                command.displayName().trim(), roleCode, null, null, true
        ));
        appendAudit(context, userId, "USER_CREATE",
                "创建用户：" + command.displayName().trim() + "（" + roleLabel(roleCode) + "）");
        return requireUser(userId);
    }

    /**
     * <p><b>Preconditions:</b>操作者为管理员，目标用户存在且revision匹配。</p>
     * <p><b>Side Effects:</b>更新账号；停用或重置密码时撤销目标用户全部会话，并写审计。</p>
     * <p><b>Error Semantics:</b>拒绝自停用、自降级、移除最后管理员和并发覆盖。</p>
     */
    @Transactional
    public ManagedUser update(String userId, UpdateManagedUserCommand command,
                              OperationContext context) {
        requireAdmin(context);
        ManagedUser current = requireUser(userId);
        String roleCode = requireRole(command.roleCode());
        protectCurrentAdministrator(current, roleCode, command.enabled(), context);
        protectLastAdministrator(current, roleCode, command.enabled());

        boolean resetPassword = command.password() != null && !command.password().isBlank();
        String passwordHash = resetPassword ? passwordHasher.hash(command.password()) : null;
        boolean updated = authRepository.updateManagedUser(
                userId, command.displayName().trim(), roleCode,
                command.enabled(), passwordHash, command.revision()
        );
        if (!updated) {
            throw new BusinessException("USER_CONCURRENT_UPDATE", "用户信息已变化，请刷新后重试", 409);
        }
        if (!command.enabled() || resetPassword) {
            authRepository.deleteSessionsByUserId(userId);
        }
        String action = statusAction(current.enabled(), command.enabled());
        appendAudit(context, userId, action, auditSummary(command, resetPassword));
        return requireUser(userId);
    }

    @Transactional
    public ManagedUser deactivate(String userId, OperationContext context) {
        requireAdmin(context);
        ManagedUser current = requireUser(userId);
        if (!current.enabled()) {
            return current;
        }
        return update(userId, new UpdateManagedUserCommand(
                current.displayName(), current.roleCode(), false, null, current.revision()
        ), context);
    }

    private void protectCurrentAdministrator(ManagedUser current, String roleCode,
                                               boolean enabled, OperationContext context) {
        if (current.id().equals(context.user().id()) && (!enabled || !"ADMIN".equals(roleCode))) {
            throw new BusinessException("USER_SELF_PROTECTION", "不能停用自己或变更自己的管理员角色", 409);
        }
    }

    private void protectLastAdministrator(ManagedUser current, String roleCode, boolean enabled) {
        boolean removesAdmin = current.enabled() && "ADMIN".equals(current.roleCode())
                && (!enabled || !"ADMIN".equals(roleCode));
        if (removesAdmin && authRepository.countEnabledAdmins() <= 1) {
            throw new BusinessException("USER_LAST_ADMIN", "系统必须至少保留一个可用管理员", 409);
        }
    }

    private String auditSummary(UpdateManagedUserCommand command, boolean resetPassword) {
        String summary = "更新用户：" + command.displayName().trim() + "，"
                + roleLabel(command.roleCode()) + "，" + (command.enabled() ? "已启用" : "已停用");
        return resetPassword ? summary + "，已重置密码" : summary;
    }

    private String statusAction(boolean wasEnabled, boolean enabled) {
        if (wasEnabled && !enabled) {
            return "USER_DISABLE";
        }
        if (!wasEnabled && enabled) {
            return "USER_ENABLE";
        }
        return "USER_UPDATE";
    }

    private ManagedUser requireUser(String userId) {
        return adminRepository.findUserById(userId).orElseThrow(() ->
                new BusinessException("USER_NOT_FOUND", "用户不存在", 404));
    }

    private String requireRole(String value) {
        String roleCode = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(roleCode) && !"PLANNER".equals(roleCode)) {
            throw new BusinessException("USER_ROLE_INVALID", "用户角色不合法", 400);
        }
        return roleCode;
    }

    private void requireAdmin(OperationContext context) {
        if (context == null || context.user() == null || !context.user().isAdmin()) {
            throw new BusinessException("ADMIN_ACCESS_DENIED", "仅系统管理员可访问该功能", 403);
        }
    }

    private void appendAudit(OperationContext context, String targetId,
                             String actionCode, String summary) {
        auditRepository.append(
                context.user().id(), actionCode, "USER", targetId,
                "SUCCESS", summary, context.traceId(), context.ipAddress()
        );
    }

    private String roleLabel(String roleCode) {
        return "ADMIN".equals(roleCode) ? "系统管理员" : "投标编制员";
    }
}
