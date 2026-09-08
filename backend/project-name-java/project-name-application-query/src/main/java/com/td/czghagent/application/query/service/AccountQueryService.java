// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
package com.td.czghagent.application.query.service;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.model.UserAccount;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.AuthRepository;
import org.springframework.stereotype.Service;

@Service
public class AccountQueryService {

    private final AuthRepository authRepository;
    private final FileStorage fileStorage;

    public AccountQueryService(AuthRepository authRepository, FileStorage fileStorage) {
        this.authRepository = authRepository;
        this.fileStorage = fileStorage;
    }

    public StoredFile readAvatar(CurrentUser user) {
        UserAccount account = authRepository.findById(user.id())
                .filter(item -> item.avatarUrl() != null && !item.avatarUrl().isBlank())
                .orElseThrow(() -> new BusinessException("AVATAR_NOT_FOUND", "用户尚未设置头像", 404));
        return fileStorage.read(account.avatarUrl(), "avatar.png", "image/png");
    }
}

