// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.repository;

import com.td.czghagent.domain.model.UserAccount;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthRepository {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(String userId);

    Optional<UserAccount> findBySessionTokenHash(String tokenHash, LocalDateTime now);

    long countUsers();

    void insertUser(UserAccount account);

    boolean updateManagedUser(String userId, String displayName, String roleCode,
                              boolean enabled, String passwordHash, long expectedRevision);

    void updateAvatar(String userId, String objectKey, String avatarRevision);

    void updateProfile(String userId, String displayName);

    void updatePassword(String userId, String passwordHash);

    long countEnabledAdmins();

    void insertSession(String id, String userId, String tokenHash, LocalDateTime expiresAt);

    void touchSession(String tokenHash, LocalDateTime seenAt);

    void deleteSession(String tokenHash);

    void deleteSessionsByUserId(String userId);

    void deleteExpiredSessions(LocalDateTime now);
}
