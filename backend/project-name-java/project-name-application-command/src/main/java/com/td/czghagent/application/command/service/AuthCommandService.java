// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.application.command.service;

import com.td.czghagent.application.command.cmd.LoginCommand;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.UserAccount;
import com.td.czghagent.domain.port.PasswordHasher;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.AuthRepository;
import com.td.czghagent.domain.service.SessionToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthCommandService {

    private final AuthRepository authRepository;
    private final AuditRepository auditRepository;
    private final PasswordHasher passwordHasher;
    private final long sessionHours;

    public AuthCommandService(
            AuthRepository authRepository,
            AuditRepository auditRepository,
            PasswordHasher passwordHasher,
            @Value("${app.auth.session-hours:12}") long sessionHours
    ) {
        this.authRepository = authRepository;
        this.auditRepository = auditRepository;
        this.passwordHasher = passwordHasher;
        this.sessionHours = sessionHours;
    }

    /**
     * <p><b>Preconditions:</b>用户名和密码均已通过Web层非空校验。</p>
     * <p><b>Side Effects:</b>创建可撤销会话并写入登录审计。</p>
     * <p><b>Error Semantics:</b>账号不存在、停用或密码错误统一返回AUTH_INVALID_CREDENTIALS。</p>
     */
    @Transactional
    public LoginResult login(LoginCommand command, String traceId, String ipAddress) {
        UserAccount account = authRepository.findByUsername(command.username().trim())
                .filter(UserAccount::enabled)
                .filter(user -> passwordHasher.matches(command.password(), user.passwordHash()))
                .orElseThrow(() -> new BusinessException(
                        "AUTH_INVALID_CREDENTIALS", "用户名或密码错误", 401));

        String token = SessionToken.generate();
        authRepository.deleteExpiredSessions(LocalDateTime.now());
        authRepository.insertSession(
                UUID.randomUUID().toString(),
                account.id(),
                SessionToken.hash(token),
                LocalDateTime.now().plusHours(sessionHours)
        );
        auditRepository.append(account.id(), "AUTH_LOGIN", "USER", account.id(),
                "SUCCESS", "账号登录成功", traceId, ipAddress);
        return new LoginResult(token, account.toCurrentUser(), sessionHours * 3600);
    }

    @Transactional
    public void logout(String token, CurrentUser user, String traceId, String ipAddress) {
        authRepository.deleteSession(SessionToken.hash(token));
        auditRepository.append(user.id(), "AUTH_LOGOUT", "USER", user.id(),
                "SUCCESS", "账号退出登录", traceId, ipAddress);
    }

    @Transactional
    public void createSeedUser(String username, String password, String displayName, String roleCode) {
        if (authRepository.findByUsername(username).isPresent()) {
            return;
        }
        authRepository.insertUser(new UserAccount(
                UUID.randomUUID().toString(),
                username,
                passwordHasher.hash(password),
                displayName,
                roleCode,
                null,
                null,
                true
        ));
    }

    public record LoginResult(String token, CurrentUser user, long expiresInSeconds) {
    }
}
