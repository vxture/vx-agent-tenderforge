// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.UserAccount;
import com.td.czghagent.domain.repository.AuthRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcAuthRepository implements AuthRepository {

    private static final RowMapper<UserAccount> USER_MAPPER = (resultSet, rowNumber) -> new UserAccount(
            resultSet.getString("id"),
            resultSet.getString("username"),
            resultSet.getString("password_hash"),
            resultSet.getString("display_name"),
            resultSet.getString("role_code"),
            resultSet.getString("avatar_url"),
            resultSet.getString("avatar_revision"),
            resultSet.getBoolean("enabled")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT * FROM app_user WHERE username = ?",
                USER_MAPPER,
                username
        ).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return jdbcTemplate.query("SELECT * FROM app_user WHERE id = ?", USER_MAPPER, userId)
                .stream().findFirst();
    }

    @Override
    public long countUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void insertUser(UserAccount account) {
        jdbcTemplate.update("""
                        INSERT INTO app_user(
                            id, username, password_hash, display_name, role_code, avatar_url, enabled
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                account.id(), account.username(), account.passwordHash(), account.displayName(),
                account.roleCode(), account.avatarUrl(), account.enabled());
    }

    @Override
    public boolean updateManagedUser(String userId, String displayName, String roleCode,
                                     boolean enabled, String passwordHash, long expectedRevision) {
        int updated = jdbcTemplate.update("""
                        UPDATE app_user
                        SET display_name = ?, role_code = ?, enabled = ?,
                            password_hash = COALESCE(?, password_hash),
                            updated_at = CURRENT_TIMESTAMP, revision = revision + 1
                        WHERE id = ? AND revision = ?
                        """,
                displayName, roleCode, enabled, passwordHash, userId, expectedRevision);
        return updated == 1;
    }

    @Override
    public void updateAvatar(String userId, String objectKey, String avatarRevision) {
        jdbcTemplate.update("""
                UPDATE app_user
                SET avatar_url = ?, avatar_revision = ?, updated_at = CURRENT_TIMESTAMP,
                    revision = revision + 1
                WHERE id = ?
                """, objectKey, avatarRevision, userId);
    }

    @Override
    public void updateProfile(String userId, String displayName) {
        jdbcTemplate.update("""
                UPDATE app_user SET display_name = ?, updated_at = CURRENT_TIMESTAMP,
                    revision = revision + 1 WHERE id = ?
                """, displayName, userId);
    }

    @Override
    public long countEnabledAdmins() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE role_code = 'ADMIN' AND enabled = TRUE",
                Long.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public void deleteSessionsByUserId(String userId) {
        jdbcTemplate.update("DELETE FROM user_session WHERE user_id = ?", userId);
    }
}
