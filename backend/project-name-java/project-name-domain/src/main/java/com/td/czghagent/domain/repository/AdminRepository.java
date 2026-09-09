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

    /**
     * 有界的管理面对象，按服务端钳制的 limit 全量返回（产品接入通则 A-3 / A-4）。
     *
     * <p>刻意没有配套的 count：账号集合的规模由组织人数决定，不会因为系统自己跑而增长，
     * 所以它不是无界流水，不需要游标，也不需要一个立刻过期的总数。
     */
    List<ManagedUser> listUsers(UserFilter filter);

    Optional<ManagedUser> findUserById(String userId);

    List<AuditLogEntry> listAuditLogs(AuditFilter filter);

    record UserFilter(
            String keyword,
            String roleCode,
            Boolean enabled,
            int limit
    ) {
    }

    /**
     * 审计流水的游标过滤器（产品接入通则 A-3）。
     *
     * <p>用键集游标而不是 offset：审计表因为系统自己跑而无限增长，
     * 而 offset 分页在这种表上有两个真问题——翻页期间新事件插入表头会让记录左移，
     * 读者<strong>看不到自己漏掉了一条</strong>；而且深翻页要求数据库扫过并丢弃前 N 行。
     * 游标锚在 {@code (createdAt, id)} 上，两者都不发生。
     *
     * <p>刻意没有 {@code countAuditLogs}：总数在无界表上既昂贵又会立刻过期，
     * 而 A-3 要的是「能不能继续翻」，那由 {@code nextCursor} 回答。
     */
    record AuditFilter(
            String keyword,
            String actionCode,
            String resultCode,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime cursorCreatedAt,
            String cursorId,
            int limit
    ) {
    }
}
