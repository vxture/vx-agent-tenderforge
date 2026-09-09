// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.application.command.service;

import com.td.czghagent.application.command.cmd.CreateManagedUserCommand;
import com.td.czghagent.application.command.cmd.UpdateManagedUserCommand;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.ManagedUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.model.UserAccount;
import com.td.czghagent.domain.port.PasswordHasher;
import com.td.czghagent.domain.repository.AdminRepository;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.AuthRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号管理的几条护栏。
 *
 * <p>这一组保护的全是<strong>把自己或所有人锁在门外</strong>那一类后果：
 * 停用最后一个管理员、把自己降级、并发覆盖别人的修改。没有一条会在写入时报错，
 * 它们的表现都是事后——没人能再登录进来改回去。
 */
class AdminCommandServiceTest {

    private static final String ADMIN_ID = "admin-1";
    private static final String TARGET_ID = "user-2";

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AuthRepository authRepository = mock(AuthRepository.class);
    private final AuditRepository auditRepository = mock(AuditRepository.class);
    private final PasswordHasher hasher = new FakeHasher();
    private final AdminCommandService service =
            new AdminCommandService(adminRepository, authRepository, auditRepository, hasher);

    // ── 只有管理员能进 ──────────────────────────────────────────────────────

    /**
     * 非管理员一律 403，四个入口都挡。
     *
     * <p>漏掉任何一个都不会报错——那个入口会照常工作，只是谁都能用。
     */
    @Test
    void refusesEveryEntryPointToANonAdministrator() {
        OperationContext planner = context(ADMIN_ID, false);

        assertThat(List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                () -> service.create(createCommand("PLANNER"), planner),
                () -> service.update(TARGET_ID, updateCommand("PLANNER", true), planner),
                () -> service.deactivate(TARGET_ID, planner),
                () -> service.activate(TARGET_ID, planner)
        )).allSatisfy(call -> assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("ADMIN_ACCESS_DENIED")));
    }

    /** 没有上下文、或上下文里没有用户，同样是拒绝而不是空指针。 */
    @Test
    void treatsAMissingCallerAsDeniedRatherThanCrashing() {
        assertThatThrownBy(() -> service.deactivate(TARGET_ID, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deactivate(
                TARGET_ID, new OperationContext(null, "trace", "127.0.0.1")))
                .isInstanceOf(BusinessException.class);
    }

    // ── 自我保护与最后一个管理员 ────────────────────────────────────────────

    /**
     * 管理员不能停用自己，也不能把自己降级。
     *
     * <p>两者都是单向门：改完那一刻起，本人就没有权限把自己改回来。
     */
    @Test
    void refusesToLetAnAdministratorLockThemselvesOut() {
        givenUser(ADMIN_ID, "ADMIN", true);

        assertThatThrownBy(() -> service.update(
                ADMIN_ID, updateCommand("ADMIN", false), context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_SELF_PROTECTION"));
        assertThatThrownBy(() -> service.update(
                ADMIN_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_SELF_PROTECTION"));
    }

    /** 改自己的姓名是允许的——护栏挡的是权限，不是所有自我修改。 */
    @Test
    void stillLetsAnAdministratorEditTheirOwnProfile() {
        givenUser(ADMIN_ID, "ADMIN", true);
        givenUpdateSucceeds();

        service.update(ADMIN_ID, updateCommand("ADMIN", true), context(ADMIN_ID, true));

        verify(authRepository).updateManagedUser(
                eq(ADMIN_ID), anyString(), eq("ADMIN"), eq(true), any(), anyLong());
    }

    /**
     * 系统必须至少留一个可用管理员。
     *
     * <p>这是整个类里唯一一条<strong>无法事后补救</strong>的护栏：停掉最后一个
     * 管理员之后，没有任何入口可以把它启用回来——只能改数据库。
     */
    @Test
    void refusesToRemoveTheLastAdministrator() {
        givenUser(TARGET_ID, "ADMIN", true);
        when(authRepository.countEnabledAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.update(
                TARGET_ID, updateCommand("ADMIN", false), context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_LAST_ADMIN"));
        assertThatThrownBy(() -> service.update(
                TARGET_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true)))
                .as("降级和停用同样会让管理员消失")
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_LAST_ADMIN"));
    }

    @Test
    void allowsRemovingAnAdministratorWhileAnotherOneRemains() {
        givenUser(TARGET_ID, "ADMIN", true);
        givenUpdateSucceeds();
        when(authRepository.countEnabledAdmins()).thenReturn(2L);

        service.update(TARGET_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true));

        verify(authRepository).updateManagedUser(
                eq(TARGET_ID), anyString(), eq("PLANNER"), eq(true), any(), anyLong());
    }

    /** 目标本来就是停用的，不算「移除一个管理员」——它已经不在计数里了。 */
    @Test
    void doesNotCountAnAlreadyDisabledAdministratorAsTheLastOne() {
        givenUser(TARGET_ID, "ADMIN", false);
        givenUpdateSucceeds();
        when(authRepository.countEnabledAdmins()).thenReturn(1L);

        service.update(TARGET_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true));

        verify(authRepository).updateManagedUser(
                eq(TARGET_ID), anyString(), eq("PLANNER"), eq(true), any(), anyLong());
    }

    // ── 停用即撤销会话 ──────────────────────────────────────────────────────

    /**
     * 停用和改密码都要撤掉该用户的全部会话。
     *
     * <p>不撤的话，一个刚被停用的账号会带着已经发出去的令牌继续用到过期——
     * 而「停用」在管理员看来是立即生效的。
     */
    @Test
    void revokesEverySessionWhenAnAccountIsDisabledOrItsPasswordReset() {
        givenUser(TARGET_ID, "PLANNER", true);
        givenUpdateSucceeds();

        service.update(TARGET_ID, updateCommand("PLANNER", false), context(ADMIN_ID, true));
        verify(authRepository).deleteSessionsByUserId(TARGET_ID);

        service.update(TARGET_ID, new UpdateManagedUserCommand(
                "张三", "PLANNER", true, "NewPass@123", 1L), context(ADMIN_ID, true));
        verify(authRepository, org.mockito.Mockito.times(2))
                .deleteSessionsByUserId(TARGET_ID);
    }

    /** 只是改个姓名不该把人踢下线。 */
    @Test
    void leavesSessionsAloneForAHarmlessEdit() {
        givenUser(TARGET_ID, "PLANNER", true);
        givenUpdateSucceeds();

        service.update(TARGET_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true));

        verify(authRepository, never()).deleteSessionsByUserId(anyString());
    }

    // ── 并发与幂等 ──────────────────────────────────────────────────────────

    /**
     * revision 对不上就拒绝，而不是覆盖。
     *
     * <p>覆盖会让两个管理员同时编辑时，后提交的那个静默抹掉前一个的修改——
     * 包括他可能刚刚做的停用。
     */
    @Test
    void refusesToOverwriteAConcurrentEdit() {
        givenUser(TARGET_ID, "PLANNER", true);
        when(authRepository.updateManagedUser(
                anyString(), anyString(), anyString(), anyBoolean(), any(), anyLong()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.update(
                TARGET_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_CONCURRENT_UPDATE"));
    }

    /**
     * 重复停用是无操作：不报错，也<strong>不再写一条审计</strong>。
     *
     * <p>写了的话，审计流里会出现一串「停用了同一个已经停用的账号」，
     * 而按操作类型统计的人会以为发生了很多次状态变更。
     */
    @Test
    void treatsARepeatedDeactivationAsANoOp() {
        givenUser(TARGET_ID, "PLANNER", false);

        ManagedUser result = service.deactivate(TARGET_ID, context(ADMIN_ID, true));

        assertThat(result.enabled()).isFalse();
        verify(authRepository, never()).updateManagedUser(
                anyString(), anyString(), anyString(), anyBoolean(), any(), anyLong());
        verify(auditRepository, never()).append(any());
    }

    @Test
    void treatsARepeatedActivationAsANoOp() {
        givenUser(TARGET_ID, "PLANNER", true);

        service.activate(TARGET_ID, context(ADMIN_ID, true));

        verify(auditRepository, never()).append(any());
    }

    // ── 审计与角色 ──────────────────────────────────────────────────────────

    /**
     * 状态变更的审计动作要分得开。
     *
     * <p>全记成 USER_UPDATE 的话，「谁在什么时候停用了这个账号」就只能靠
     * 读摘要文本去猜，而按动作筛查的审计流会漏掉它。
     */
    @Test
    void namesTheStatusTransitionInTheAuditAction() {
        givenUser(TARGET_ID, "PLANNER", true);
        givenUpdateSucceeds();

        service.update(TARGET_ID, updateCommand("PLANNER", false), context(ADMIN_ID, true));
        assertThat(capturedAudit().action()).isEqualTo("USER_DISABLE");

        givenUser(TARGET_ID, "PLANNER", false);
        service.update(TARGET_ID, updateCommand("PLANNER", true), context(ADMIN_ID, true));
        assertThat(capturedAudit().action()).isEqualTo("USER_ENABLE");
    }

    /** 审计摘要里<strong>绝不出现口令</strong>，只说「已重置密码」。 */
    @Test
    void neverWritesAPasswordIntoTheAuditTrail() {
        givenUser(TARGET_ID, "PLANNER", true);
        givenUpdateSucceeds();

        service.update(TARGET_ID, new UpdateManagedUserCommand(
                "张三", "PLANNER", true, "SuperSecret@2026", 1L), context(ADMIN_ID, true));

        AuditEvent audit = capturedAudit();
        assertThat(audit.detailSummary()).contains("已重置密码").doesNotContain("SuperSecret");
    }

    @Test
    void refusesARoleItDoesNotRecognise() {
        assertThatThrownBy(() -> service.create(
                createCommand("SUPERUSER"), context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_ROLE_INVALID"));
    }

    /** 角色大小写和空格不该成为拒绝的理由——它们不携带任何信息。 */
    @Test
    void normalisesTheRoleBeforeCheckingIt() {
        givenUser(TARGET_ID, "PLANNER", true);
        givenUpdateSucceeds();

        service.update(TARGET_ID, updateCommand("  admin  ", true), context(ADMIN_ID, true));

        verify(authRepository).updateManagedUser(
                anyString(), anyString(), eq("ADMIN"), anyBoolean(), any(), anyLong());
    }

    // ── 创建 ────────────────────────────────────────────────────────────────

    @Test
    void refusesADuplicateUsername() {
        when(authRepository.findByUsername("zhangsan")).thenReturn(
                Optional.of(new UserAccount("x", "zhangsan", "h", "张三", "PLANNER",
                        null, null, true)));

        assertThatThrownBy(() -> service.create(
                createCommand("PLANNER"), context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getErrorCode())
                        .isEqualTo("USER_USERNAME_EXISTS"));
    }

    /** 用户名归一成小写后再查重，否则 Zhang 和 zhang 会是两个账号。 */
    @Test
    void storesTheUsernameInACanonicalForm() {
        when(authRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        // create 结尾会按新铸的 UUID 回读一次，桩要覆盖任意 id。
        when(adminRepository.findUserById(anyString())).thenReturn(Optional.of(new ManagedUser(
                "new-id", "zhangsan", "张三", "PLANNER", null, true,
                LocalDateTime.now(), LocalDateTime.now(), 1L)));

        service.create(new CreateManagedUserCommand(
                "  ZhangSan  ", "张三", "PLANNER", "Pass@123"), context(ADMIN_ID, true));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(authRepository).insertUser(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("zhangsan");
        assertThat(captor.getValue().passwordHash())
                .as("入库的是哈希，不是原文口令").isEqualTo("hash:Pass@123");
        assertThat(captor.getValue().enabled()).isTrue();
    }

    @Test
    void reportsAMissingUserAsNotFound() {
        when(adminRepository.findUserById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(TARGET_ID, context(ADMIN_ID, true)))
                .satisfies(failure -> assertThat(
                        ((BusinessException) failure).getHttpStatus()).isEqualTo(404));
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private void givenUser(String id, String roleCode, boolean enabled) {
        when(adminRepository.findUserById(id)).thenReturn(Optional.of(new ManagedUser(
                id, "zhangsan", "张三", roleCode, null, enabled,
                LocalDateTime.now(), LocalDateTime.now(), 1L)));
    }

    private void givenUpdateSucceeds() {
        when(authRepository.updateManagedUser(
                anyString(), anyString(), anyString(), anyBoolean(), any(), anyLong()))
                .thenReturn(true);
        when(authRepository.countEnabledAdmins()).thenReturn(5L);
    }

    private AuditEvent capturedAudit() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepository, org.mockito.Mockito.atLeastOnce()).append(captor.capture());
        List<AuditEvent> all = new ArrayList<>(captor.getAllValues());
        return all.get(all.size() - 1);
    }

    private static OperationContext context(String userId, boolean admin) {
        return new OperationContext(
                new CurrentUser(userId, "admin", "管理员", admin ? "ADMIN" : "PLANNER",
                        null, TenantScope.local(userId)),
                "trace-1", "127.0.0.1");
    }

    private static CreateManagedUserCommand createCommand(String roleCode) {
        return new CreateManagedUserCommand("zhangsan", "张三", roleCode, "Pass@123");
    }

    private static UpdateManagedUserCommand updateCommand(String roleCode, boolean enabled) {
        return new UpdateManagedUserCommand("张三", roleCode, enabled, null, 1L);
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return hash(rawPassword).equals(passwordHash);
        }
    }
}
