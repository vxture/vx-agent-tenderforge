// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.repository;

import com.td.czghagent.domain.model.UserAccount;

import java.util.Optional;

public interface AuthRepository {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(String userId);

    long countUsers();

    void insertUser(UserAccount account);

    boolean updateManagedUser(String userId, String displayName, String roleCode,
                              boolean enabled, String passwordHash, long expectedRevision);

    void updateAvatar(String userId, String objectKey, String avatarRevision);

    void updateProfile(String userId, String displayName);

    long countEnabledAdmins();

    void deleteSessionsByUserId(String userId);
}
