package com.td.czghagent.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidReferenceAsset;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.TenantScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

final class BidJdbcMappers {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final RowMapper<BidDocument> BID = (rs, row) -> new BidDocument(
            rs.getString("id"), rs.getString("owner_id"),
            new TenantScope(rs.getString("org_id"), rs.getString("workspace_id")),
            rs.getString("code"),
            rs.getString("writing_method"), rs.getString("title"), rs.getInt("target_pages"),
            rs.getString("bidding_mode"), rs.getString("workflow_step"), rs.getString("status"),
            rs.getBoolean("content_stale"), rs.getString("error_message"),
            JdbcTimes.localDateTime(rs, "created_at"),
            JdbcTimes.localDateTime(rs, "updated_at"), rs.getLong("revision"));

    static final RowMapper<BidWorkspace.Criterion> CRITERION = (rs, row) ->
            new BidWorkspace.Criterion(
                    rs.getString("id"), rs.getString("item_type"), rs.getString("title"),
                    rs.getString("description"),
                    rs.getObject("score") == null ? null : rs.getDouble("score"),
                    rs.getString("source_excerpt"), rs.getString("source_locator"),
                    rs.getString("scope"), rs.getString("confidence"), rs.getInt("sort_order"),
                    rs.getBoolean("manually_edited"));

    static final RowMapper<BidWorkspace.OutlineNode> OUTLINE = (rs, row) ->
            new BidWorkspace.OutlineNode(
                    rs.getString("id"), rs.getString("parent_id"), rs.getInt("level_no"),
                    rs.getString("title"), rs.getInt("planned_pages"), rs.getInt("sort_order"),
                    rs.getLong("revision"), safe(rs.getString("task_brief")),
                    parseStringList(rs.getString("must_keywords_json")),
                    parseStringList(rs.getString("scoring_point_ids_json")));

    static final RowMapper<BidWorkspace.Chapter> CHAPTER = (rs, row) ->
            new BidWorkspace.Chapter(
                    rs.getString("id"), rs.getString("outline_node_id"), rs.getString("title"),
                    rs.getString("content"), rs.getString("generation_status"),
                    JdbcTimes.localDateTime(rs, "updated_at"), rs.getLong("revision"));

    static final RowMapper<BidReferenceAsset> ASSET = (rs, row) ->
            new BidReferenceAsset(
                    rs.getString("id"), rs.getString("owner_id"), rs.getString("category"),
                    rs.getString("display_name"), rs.getString("original_file_name"),
                    rs.getString("media_type"), rs.getLong("file_size"),
                    JdbcTimes.localDateTime(rs, "created_at"),
                    JdbcTimes.localDateTime(rs, "updated_at"), rs.getLong("revision"));

    static final RowMapper<BidExport> EXPORT = (rs, row) -> new BidExport(
            rs.getString("id"), rs.getString("bid_id"), rs.getInt("version_no"),
            rs.getString("file_name"), rs.getLong("file_size"),
            rs.getString("layout_job_id"), rs.getString("qa_status"),
            JdbcTimes.localDateTime(rs, "created_at"));

    private BidJdbcMappers() {
    }

    static List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored tender AI list is invalid", exception);
        }
    }

    static String writeStringList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Tender AI list cannot be serialized", exception);
        }
    }

    static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Tender archive cannot be serialized", exception);
        }
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }

    static String normalizeTitle(String value) {
        return safe(value).trim();
    }

    /**
     * 读一个可空的时间列。
     *
     * <p>库列是 {@code TIMESTAMPTZ}，领域用 {@link LocalDateTime}，两者之间那次
     * 显式转换在 {@link JdbcTimes} 里——驱动拒绝直接转，理由和做法都记在那。
     *
     * <p>这里保留一层包装而不是让调用方直接用 {@code JdbcTimes}：这个方法名
     * 说的是「可空」，而可空是这些列的要害。{@code finished_at} 之类的列
     * 「还没结束」就是 {@code NULL}，读成一个默认时刻会让「未完成」和
     * 「在纪元零点完成」变成同一件事，而这件事不会有任何报错。
     *
     * <p>历史注记：MySQL 时代这里的坑是 {@code getTimestamp(...).toLocalDateTime()}
     * 会按服务端时区解释再转 JVM 默认时区，而写入侧不转——读写不对称，
     * 键集游标翻页的第二页永远为空且不报错。换到 Postgres 之后这个坑换了形态：
     * 驱动干脆拒绝隐式转换（见 JdbcTimes），把「按哪个时区解释」逼成一个
     * 必须显式回答的问题。<strong>拒绝比猜错好</strong>。
     */
    static LocalDateTime nullableTime(ResultSet resultSet, String column) throws SQLException {
        return JdbcTimes.localDateTime(resultSet, column);
    }

    static Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
