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
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class), rs.getLong("revision"));

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
                    rs.getObject("updated_at", LocalDateTime.class), rs.getLong("revision"));

    static final RowMapper<BidReferenceAsset> ASSET = (rs, row) ->
            new BidReferenceAsset(
                    rs.getString("id"), rs.getString("owner_id"), rs.getString("category"),
                    rs.getString("display_name"), rs.getString("original_file_name"),
                    rs.getString("media_type"), rs.getLong("file_size"),
                    rs.getObject("created_at", LocalDateTime.class),
                    rs.getObject("updated_at", LocalDateTime.class), rs.getLong("revision"));

    static final RowMapper<BidExport> EXPORT = (rs, row) -> new BidExport(
            rs.getString("id"), rs.getString("bid_id"), rs.getInt("version_no"),
            rs.getString("file_name"), rs.getLong("file_size"),
            rs.getString("layout_job_id"), rs.getString("qa_status"),
            rs.getObject("created_at", LocalDateTime.class));

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
     * 读一个可空的 DATETIME 列。
     *
     * <p>走 {@code getObject(..., LocalDateTime.class)} 而不是
     * {@code getTimestamp(...).toLocalDateTime()}：后者会把库里的墙钟时间按
     * <strong>服务端时区解释、再转成 JVM 默认时区</strong>，而 JVM 在容器里是 UTC、
     * MySQL 是 Asia/Shanghai——于是每一个时间戳读回来都少 8 小时。
     *
     * <p>更要命的是这个偏移<strong>不对称</strong>：写参数时绑定的 LocalDateTime 不做转换，
     * 原样落库。所以「读出来再拿去比较」这件事必然错位，键集游标翻页就是撞在这上面
     * ——第二页永远为空，而且不报任何错。
     */
    static LocalDateTime nullableTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, LocalDateTime.class);
    }

    static Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
