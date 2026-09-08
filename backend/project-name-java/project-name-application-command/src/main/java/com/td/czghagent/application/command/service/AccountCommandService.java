// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.ProcessedImage;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.model.UserAccount;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.port.ImageProcessor;
import com.td.czghagent.domain.port.PasswordHasher;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.repository.AuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class AccountCommandService {

    private static final int MAX_AVATAR_UPLOAD_SIZE = 15 * 1024 * 1024;

    private final AuthRepository authRepository;
    private final AuditRepository auditRepository;
    private final PasswordHasher passwordHasher;
    private final ImageProcessor imageProcessor;
    private final FileStorage fileStorage;

    public AccountCommandService(AuthRepository authRepository,
                                 AuditRepository auditRepository,
                                 PasswordHasher passwordHasher,
                                 ImageProcessor imageProcessor,
                                 FileStorage fileStorage) {
        this.authRepository = authRepository;
        this.auditRepository = auditRepository;
        this.passwordHasher = passwordHasher;
        this.imageProcessor = imageProcessor;
        this.fileStorage = fileStorage;
    }

    /**
     * <p><b>Preconditions:</b>用户已登录，上传内容可解码为受支持图片。</p>
     * <p><b>Side Effects:</b>头像规范化为PNG后写入私有存储并更新账号头像版本。</p>
     * <p><b>Error Semantics:</b>事务回滚时删除新文件，提交后再清理旧头像文件。</p>
     */
    @Transactional
    public CurrentUser updateAvatar(byte[] content, OperationContext context) {
        if (content == null || content.length == 0 || content.length > MAX_AVATAR_UPLOAD_SIZE) {
            throw new BusinessException("AVATAR_FILE_INVALID", "头像文件无效或超过15MB", 400);
        }
        UserAccount account = requireAccount(context.user().id());
        ProcessedImage image = imageProcessor.normalizeAvatar(content);
        StoredFile stored = fileStorage.store(
                "users/" + account.id() + "/avatar", image.fileName(),
                image.mediaType(), image.content()
        );
        String avatarRevision = UUID.randomUUID().toString();
        try {
            authRepository.updateAvatar(account.id(), stored.objectKey(), avatarRevision);
            auditRepository.append(account.id(), "ACCOUNT_AVATAR_UPDATE", "USER", account.id(),
                    "SUCCESS", "更新个人头像", context.traceId(), context.ipAddress());
        } catch (RuntimeException exception) {
            fileStorage.delete(stored.objectKey());
            throw exception;
        }
        cleanupAvatarAfterTransaction(account.avatarUrl(), stored.objectKey());
        return requireAccount(account.id()).toCurrentUser();
    }

    /**
     * <p><b>Preconditions:</b>当前密码正确，新密码长度为8至64个字符且与当前密码不同。</p>
     * <p><b>Side Effects:</b>更新BCrypt密码哈希、撤销用户全部会话并写审计。</p>
     * <p><b>Error Semantics:</b>当前密码错误返回ACCOUNT_PASSWORD_INVALID。</p>
     */
    @Transactional
    public void changePassword(String currentPassword, String newPassword, OperationContext context) {
        UserAccount account = requireAccount(context.user().id());
        if (!passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw new BusinessException("ACCOUNT_PASSWORD_INVALID", "当前密码不正确", 400);
        }
        validateNewPassword(newPassword);
        if (passwordHasher.matches(newPassword, account.passwordHash())) {
            throw new BusinessException("ACCOUNT_PASSWORD_UNCHANGED", "新密码不能与当前密码相同", 400);
        }
        authRepository.updatePassword(account.id(), passwordHasher.hash(newPassword));
        authRepository.deleteSessionsByUserId(account.id());
        auditRepository.append(account.id(), "ACCOUNT_PASSWORD_CHANGE", "USER", account.id(),
                "SUCCESS", "用户修改个人密码并撤销全部会话",
                context.traceId(), context.ipAddress());
    }

    @Transactional
    public CurrentUser updateProfile(String displayName, OperationContext context) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.length() < 2 || normalized.length() > 64) {
            throw new BusinessException("ACCOUNT_DISPLAY_NAME_INVALID", "显示名称长度必须为2至64个字符", 400);
        }
        UserAccount account = requireAccount(context.user().id());
        authRepository.updateProfile(account.id(), normalized);
        auditRepository.append(account.id(), "ACCOUNT_PROFILE_UPDATE", "USER", account.id(),
                "SUCCESS", "更新个人显示名称", context.traceId(), context.ipAddress());
        return requireAccount(account.id()).toCurrentUser();
    }

    private UserAccount requireAccount(String userId) {
        return authRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在", 404));
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BusinessException(
                    "ACCOUNT_PASSWORD_FORMAT_INVALID", "新密码长度必须为8至64个字符", 400
            );
        }
    }

    private void cleanupAvatarAfterTransaction(String oldObjectKey, String newObjectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (oldObjectKey != null) {
                fileStorage.delete(oldObjectKey);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (oldObjectKey != null && !oldObjectKey.equals(newObjectKey)) {
                        fileStorage.delete(oldObjectKey);
                    }
                } else {
                    fileStorage.delete(newObjectKey);
                }
            }
        });
    }
}

