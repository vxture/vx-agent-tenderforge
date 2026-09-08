package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidWorkspace;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class JdbcBidReviewPersistence {
    private final JdbcTemplate jdbcTemplate;

    JdbcBidReviewPersistence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    BidProductionState loadState(String bidId, String latestTaskId) {
        StageRow stage = jdbcTemplate.query("""
                SELECT interpretation_status, interpretation_version, interpretation_hash,
                       outline_status, outline_version, outline_hash,
                       content_status, content_version, content_hash, stale_reason
                FROM bid_document WHERE id = ?
                """, (rs, row) -> new StageRow(
                rs.getString("interpretation_status"), rs.getInt("interpretation_version"),
                rs.getString("interpretation_hash"), rs.getString("outline_status"),
                rs.getInt("outline_version"), rs.getString("outline_hash"),
                rs.getString("content_status"), rs.getInt("content_version"),
                rs.getString("content_hash"), rs.getString("stale_reason")), bidId)
                .stream().findFirst().orElseThrow();
        return new BidProductionState(
                stage.interpretationStatus(), stage.interpretationVersion(),
                stage.interpretationHash(), stage.outlineStatus(), stage.outlineVersion(),
                stage.outlineHash(), stage.contentStatus(), stage.contentVersion(),
                stage.contentHash(), stage.staleReason(), loadFrozenFacts(bidId),
                loadUnits(latestTaskId), loadEvents(bidId),
                loadReviewIssues(bidId), loadLatestLayoutJob(bidId));
    }

    String freezeInterpretation(String bidId, String userId, String contentHash,
                                List<BidWorkspace.Criterion> criteria,
                                List<BidProductionState.FrozenFact> facts) {
        int version = nextVersion("bid_interpretation_version", bidId);
        String versionId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO bid_interpretation_version(
                    id, bid_id, version_no, status, content_hash, created_by, frozen_at
                ) VALUES (?, ?, ?, 'FROZEN', ?, ?, CURRENT_TIMESTAMP)
                """, versionId, bidId, version, contentHash, userId);
        insertRequirementItems(versionId, criteria);
        jdbcTemplate.update("DELETE FROM bid_frozen_fact WHERE bid_id = ?", bidId);
        insertFrozenFacts(bidId, versionId, facts);
        jdbcTemplate.update("""
                UPDATE bid_document
                SET interpretation_status = 'FROZEN', interpretation_version = ?,
                    interpretation_hash = ?, outline_status = 'DRAFT', outline_hash = NULL,
                    workflow_step = 'INTERPRETATION', status = 'INTERPRETATION_READY',
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, version, contentHash, bidId);
        return versionId;
    }

    void markInterpretationReview(String bidId, String staleReason) {
        jdbcTemplate.update("""
                UPDATE bid_document
                SET interpretation_status = 'REVIEW', interpretation_hash = NULL,
                    outline_status = 'DRAFT', outline_hash = NULL,
                    stale_reason = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_chapter c WHERE c.bid_id = bid_document.id
                        AND CHAR_LENGTH(TRIM(c.content)) > 0
                    ) THEN ? ELSE stale_reason END,
                    content_stale = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_chapter c WHERE c.bid_id = bid_document.id
                        AND CHAR_LENGTH(TRIM(c.content)) > 0
                    ) THEN TRUE ELSE content_stale END WHERE id = ?
                """, staleReason, bidId);
    }

    void freezeOutline(String bidId, String contentHash) {
        jdbcTemplate.update("""
                UPDATE bid_document
                SET outline_status = 'FROZEN', outline_version = outline_version + 1,
                    outline_hash = ?, workflow_step = 'OUTLINE', status = 'OUTLINE_READY',
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, contentHash, bidId);
    }

    void markOutlineReview(String bidId, String staleReason) {
        jdbcTemplate.update("""
                UPDATE bid_document SET outline_status = 'REVIEW', outline_hash = NULL,
                    stale_reason = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_chapter c WHERE c.bid_id = bid_document.id
                        AND CHAR_LENGTH(TRIM(c.content)) > 0
                    ) THEN ? ELSE stale_reason END WHERE id = ?
                """, staleReason, bidId);
    }

    void replaceReviewIssues(String bidId, List<BidProductionState.ReviewIssue> issues) {
        jdbcTemplate.update(
                "DELETE FROM bid_review_issue WHERE bid_id = ? AND status = 'OPEN'", bidId);
        for (BidProductionState.ReviewIssue issue : issues) {
            jdbcTemplate.update("""
                    INSERT INTO bid_review_issue(
                        id, bid_id, chapter_id, severity, issue_code, message, suggestion, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, issue.id(), bidId, issue.chapterId(), issue.severity(), issue.code(),
                    issue.message(), issue.suggestion(), issue.status());
        }
    }

    void markContentReview(String bidId, String staleReason) {
        jdbcTemplate.update("""
                UPDATE bid_document SET content_status = 'REVIEW', content_hash = NULL,
                    stale_reason = CASE WHEN content_stale THEN stale_reason ELSE ? END
                WHERE id = ?
                """, staleReason, bidId);
    }

    void freezeContent(String bidId, String contentHash) {
        jdbcTemplate.update("""
                UPDATE bid_document
                SET content_status = 'FROZEN', content_version = content_version + 1,
                    content_hash = ?, content_stale = FALSE, stale_reason = NULL,
                    status = 'CONTENT_READY', updated_at = CURRENT_TIMESTAMP,
                    revision = revision + 1 WHERE id = ?
                """, contentHash, bidId);
    }

    private List<BidProductionState.FrozenFact> loadFrozenFacts(String bidId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_frozen_fact WHERE bid_id = ? ORDER BY sort_order, id
                """, (rs, row) -> new BidProductionState.FrozenFact(
                rs.getString("id"), rs.getString("fact_type"), rs.getString("fact_name"),
                rs.getString("fact_value"), rs.getString("source_locator"),
                splitLines(rs.getString("forbidden_values")), rs.getInt("sort_order")), bidId);
    }

    private List<BidProductionState.GenerationUnit> loadUnits(String taskId) {
        if (taskId == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT * FROM bid_generation_unit WHERE task_id = ? ORDER BY unit_index, id
                """, (rs, row) -> new BidProductionState.GenerationUnit(
                rs.getString("id"), rs.getString("task_id"), rs.getString("chapter_id"),
                rs.getInt("unit_index"), rs.getString("status"), rs.getInt("attempt_count"),
                rs.getInt("word_budget"), rs.getString("summary"), rs.getString("error_message"),
                rs.getTimestamp("updated_at").toLocalDateTime()), taskId);
    }

    private List<BidProductionState.GenerationEvent> loadEvents(String bidId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_generation_event WHERE bid_id = ?
                ORDER BY occurred_at DESC, id DESC LIMIT 100
                """, (rs, row) -> new BidProductionState.GenerationEvent(
                rs.getString("id"), rs.getString("task_id"), rs.getString("chapter_id"),
                rs.getString("event_type"), rs.getString("message"),
                rs.getTimestamp("occurred_at").toLocalDateTime()), bidId);
    }

    private List<BidProductionState.ReviewIssue> loadReviewIssues(String bidId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_review_issue WHERE bid_id = ? ORDER BY created_at, id
                """, (rs, row) -> new BidProductionState.ReviewIssue(
                rs.getString("id"), rs.getString("chapter_id"), rs.getString("severity"),
                rs.getString("issue_code"), rs.getString("message"), rs.getString("suggestion"),
                rs.getString("status")), bidId);
    }

    private BidProductionState.LayoutJob loadLatestLayoutJob(String bidId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_layout_job WHERE bid_id = ?
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, (rs, row) -> new BidProductionState.LayoutJob(
                rs.getString("id"), rs.getString("status"), rs.getString("workflow_run_id"),
                rs.getString("input_hash"),
                rs.getInt("target_pages"),
                BidJdbcMappers.nullableInteger(rs.getObject("actual_pages")),
                rs.getString("qa_status"), rs.getString("qa_summary"),
                rs.getString("error_message"), rs.getTimestamp("created_at").toLocalDateTime(),
                BidJdbcMappers.nullableTime(rs.getTimestamp("finished_at"))), bidId)
                .stream().findFirst().orElse(null);
    }

    private void insertRequirementItems(
            String versionId, List<BidWorkspace.Criterion> criteria) {
        for (BidWorkspace.Criterion item : criteria) {
            jdbcTemplate.update("""
                    INSERT INTO bid_requirement_item(
                        id, interpretation_version_id, source_criterion_id, item_type,
                        title, description, score, source_locator, source_excerpt,
                        scope, confidence, sort_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), versionId, item.id(), item.type(),
                    item.title(), item.description(), item.score(), item.sourceLocator(),
                    item.sourceExcerpt(), item.scope(), item.confidence(), item.sortOrder());
        }
    }

    private void insertFrozenFacts(String bidId, String versionId,
                                   List<BidProductionState.FrozenFact> facts) {
        for (BidProductionState.FrozenFact fact : facts) {
            jdbcTemplate.update("""
                    INSERT INTO bid_frozen_fact(
                        id, bid_id, interpretation_version_id, fact_type, fact_name,
                        fact_value, source_locator, forbidden_values, sort_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, fact.id(), bidId, versionId, fact.type(), fact.name(), fact.value(),
                    fact.sourceLocator(), String.join("\n", fact.forbiddenValues()), fact.sortOrder());
        }
    }

    private int nextVersion(String table, String bidId) {
        String sql = "SELECT COALESCE(MAX(version_no), 0) FROM " + table + " WHERE bid_id = ?";
        Integer latest = jdbcTemplate.queryForObject(sql, Integer.class, bidId);
        return (latest == null ? 0 : latest) + 1;
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        value.lines().map(String::trim).filter(item -> !item.isBlank()).forEach(result::add);
        return result;
    }

    private record StageRow(
            String interpretationStatus, int interpretationVersion, String interpretationHash,
            String outlineStatus, int outlineVersion, String outlineHash,
            String contentStatus, int contentVersion, String contentHash, String staleReason) {
    }
}
