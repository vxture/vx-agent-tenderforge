// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.domain.repository;

import com.td.czghagent.domain.model.AuditLogEntry;
import com.td.czghagent.domain.model.ManagedUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminRepository {

    List<ManagedUser> listUsers(UserFilter filter);

    long countUsers(UserFilter filter);

    Optional<ManagedUser> findUserById(String userId);

    List<AuditLogEntry> listAuditLogs(AuditFilter filter);

    long countAuditLogs(AuditFilter filter);

    record UserFilter(
            String keyword,
            String roleCode,
            Boolean enabled,
            int offset,
            int limit
    ) {
    }

    record AuditFilter(
            String keyword,
            String actionCode,
            String resultCode,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int offset,
            int limit
    ) {
    }
}
