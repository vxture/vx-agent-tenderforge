// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-10
package com.td.czghagent.infrastructure.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 时间列在 JDBC 边界上的一次转换。
 *
 * <p><strong>为什么需要它。</strong>库里所有时间列都是 {@code TIMESTAMPTZ}
 * （data_platform_100 §3.2.2：带时区时间戳），而领域模型用的是
 * {@link LocalDateTime}。PostgreSQL 驱动**拒绝**直接把 TIMESTAMPTZ 读成
 * LocalDateTime：
 *
 * <pre>
 * PSQLException: Cannot convert the column of type TIMESTAMPTZ
 *                to requested type java.time.LocalDateTime
 * </pre>
 *
 * <p>这不是驱动小气，是它拒绝替你猜「这个绝对时刻在哪个时区的挂钟上是几点」。
 * 猜这件事必须由调用方显式做，所以这里做——而不是把库列降级成无时区的
 * {@code TIMESTAMP} 把问题藏回去。库存的是绝对时刻，这是对的；领域用挂钟时间，
 * 这也是对的；**两者之间需要一次显式转换，位置就在这一层**。
 *
 * <p><strong>必须显式转到 JVM 时区，不能直接 toLocalDateTime()。</strong>
 * pgjdbc 把 {@code timestamptz} 读成**偏移为 UTC** 的 {@link OffsetDateTime}，
 * 所以 {@code value.toLocalDateTime()} 拿到的是 UTC 挂钟时间；而写入侧
 * {@code setObject(LocalDateTime)} 是按会话时区（= JVM 默认时区）解释的。
 * 两边不对称，东八区下读回来正好少 8 小时。
 *
 * <p>这个坑本仓踩过两次，形态不同、症状一样：MySQL 时代是
 * {@code getTimestamp(...).toLocalDateTime()} 按服务端时区解释再转 JVM 时区；
 * 现在是驱动固定返回 UTC 偏移。<strong>两次都是「读的时区」与「写的时区」不是
 * 同一个</strong>。所以这里用 {@code atZoneSameInstant} 显式对齐到写入时用的
 * 那个时区，而不是依赖任何一端的默认行为。
 *
 * <p>容器上钉了 {@code TZ=Asia/Shanghai}（compose 与 CI 都钉）：存进去的绝对
 * 时刻不受时区影响，但它渲染成几点会受影响，钉住是为了重启或换主机之后
 * 挂钟时间不漂。
 */
final class JdbcTimes {

    private JdbcTimes() {
    }

    /**
     * 按 {@link LocalDateTime} 读一个 {@code TIMESTAMPTZ} 列，null 原样返回。
     *
     * <p>可空列必须保持可空：{@code finished_at} 之类的列「还没结束」就是
     * {@code NULL}，把它读成一个默认时刻会让「未完成」和「在纪元零点完成」
     * 变成同一件事。
     */
    static LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
