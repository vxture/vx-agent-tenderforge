// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidExport;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.BidSummary;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.BidWorkspaceViews;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

final class JdbcBidWorkspaceReader {
    private final JdbcTemplate jdbcTemplate;
    private final BidProductionRepository productionRepository;
    private final JdbcBidTaskExportPersistence tasks;

    JdbcBidWorkspaceReader(JdbcTemplate jdbcTemplate,
                           BidProductionRepository productionRepository,
                           JdbcBidTaskExportPersistence tasks) {
        this.jdbcTemplate = jdbcTemplate;
        this.productionRepository = productionRepository;
        this.tasks = tasks;
    }

    List<BidSummary> listBids(String ownerId) {
        return jdbcTemplate.query("""
                SELECT b.*,
                       (SELECT COUNT(*) FROM bid_chapter c WHERE c.bid_id = b.id) AS total_chapters,
                       (SELECT COUNT(*) FROM bid_chapter c WHERE c.bid_id = b.id
                           AND c.generation_status IN ('READY', 'MANUAL')) AS completed_chapters,
                       CASE WHEN EXISTS(SELECT 1 FROM bid_export e WHERE e.bid_id = b.id)
                           THEN TRUE ELSE FALSE END AS has_export,
                       (SELECT j.status FROM bid_layout_job j WHERE j.bid_id = b.id
                           ORDER BY j.created_at DESC, j.id DESC LIMIT 1) AS layout_status,
                       (SELECT j.qa_status FROM bid_layout_job j WHERE j.bid_id = b.id
                           ORDER BY j.created_at DESC, j.id DESC LIMIT 1) AS qa_status,
                       (SELECT j.actual_pages FROM bid_layout_job j WHERE j.bid_id = b.id
                           ORDER BY j.created_at DESC, j.id DESC LIMIT 1) AS actual_pages,
                       (SELECT MAX(e.version_no) FROM bid_export e WHERE e.bid_id = b.id)
                           AS latest_export_version
                FROM bid_document b WHERE b.owner_id = ?
                ORDER BY b.updated_at DESC, b.id DESC
                """, (rs, row) -> new BidSummary(
                rs.getString("id"), rs.getString("code"), rs.getString("title"),
                rs.getInt("target_pages"), rs.getString("bidding_mode"),
                rs.getString("workflow_step"), rs.getString("status"),
                rs.getBoolean("content_stale"), rs.getInt("completed_chapters"),
                rs.getInt("total_chapters"), rs.getBoolean("has_export"),
                rs.getString("layout_status"), rs.getString("qa_status"),
                BidJdbcMappers.nullableInteger(rs.getObject("actual_pages")),
                BidJdbcMappers.nullableInteger(rs.getObject("latest_export_version")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()), ownerId);
    }

    Optional<BidDocument> findBid(String bidId, String ownerId) {
        return jdbcTemplate.query(
                "SELECT * FROM bid_document WHERE id = ? AND owner_id = ?",
                BidJdbcMappers.BID, bidId, ownerId).stream().findFirst();
    }

    boolean existsBid(String bidId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bid_document WHERE id = ?", Long.class, bidId);
        return count != null && count > 0;
    }

    BidWorkspace loadWorkspace(BidDocument bid) {
        BidWorkspace.SourceFile source = sourceFile(bid.id());
        List<BidWorkspace.Criterion> criteria = jdbcTemplate.query("""
                SELECT * FROM bid_scoring_criterion WHERE bid_id = ? ORDER BY sort_order, id
                """, BidJdbcMappers.CRITERION, bid.id());
        List<BidWorkspace.OutlineNode> outline = jdbcTemplate.query("""
                SELECT * FROM bid_outline_node WHERE bid_id = ? ORDER BY sort_order, level_no, id
                """, BidJdbcMappers.OUTLINE, bid.id());
        List<BidWorkspace.Chapter> chapters = jdbcTemplate.query("""
                SELECT c.* FROM bid_chapter c
                JOIN bid_outline_node n ON n.id = c.outline_node_id
                WHERE c.bid_id = ? ORDER BY n.sort_order, n.id
                """, BidJdbcMappers.CHAPTER, bid.id());
        BidWorkspace.GenerationTask task = tasks.latestTask(bid.id());
        BidWorkspace.OutlineTask outlineTask = tasks.latestOutlineTask(bid.id());
        List<String> selected = selectedAssets(bid.id());
        List<BidExport> exports = tasks.listExports(bid.id());
        BidProductionState production = productionRepository.loadState(
                bid.id(), task == null ? null : task.id());
        return new BidWorkspace(
                latestBid(bid), source, criteria, outline, chapters, outlineTask, task, selected,
                exports, production);
    }

    BidWorkspaceViews.Metadata loadMetadata(BidDocument bid) {
        BidWorkspace.GenerationTask task = tasks.latestTask(bid.id());
        BidWorkspace.SourceFile source = sourceFile(bid.id());
        BidWorkspace.OutlineTask outlineTask = tasks.latestOutlineTask(bid.id());
        List<String> selected = selectedAssets(bid.id());
        List<BidExport> exports = tasks.listExports(bid.id());
        BidProductionState production = productionRepository.loadState(
                bid.id(), task == null ? null : task.id());
        return new BidWorkspaceViews.Metadata(
                latestBid(bid), source, outlineTask, task, selected, exports, production);
    }

    BidWorkspaceViews.OutlineView loadOutline(String bidId) {
        List<BidWorkspace.OutlineNode> nodes = jdbcTemplate.query("""
                SELECT * FROM bid_outline_node WHERE bid_id = ? ORDER BY sort_order, level_no, id
                """, BidJdbcMappers.OUTLINE, bidId);
        List<BidWorkspaceViews.ChapterSummary> chapters = jdbcTemplate.query("""
                SELECT summary.id, summary.outline_node_id, summary.title,
                       summary.generation_status,
                       GREATEST(
                           (CHAR_LENGTH(summary.normalized_content)
                               - CHAR_LENGTH(REPLACE(summary.normalized_content, '<table', ''))) / 6,
                           (CHAR_LENGTH(summary.normalized_content)
                               - CHAR_LENGTH(REPLACE(summary.normalized_content, 'data-table-title', ''))) / 16
                       ) AS table_count,
                       summary.updated_at, summary.revision
                FROM (
                    SELECT c.id, c.outline_node_id, c.title, c.generation_status,
                           LOWER(COALESCE(c.content, '')) AS normalized_content,
                           c.updated_at, c.revision, n.sort_order
                    FROM bid_chapter c
                    JOIN bid_outline_node n ON n.id = c.outline_node_id
                    WHERE c.bid_id = ?
                ) summary
                ORDER BY summary.sort_order, summary.id
                """, (rs, row) -> new BidWorkspaceViews.ChapterSummary(
                rs.getString("id"), rs.getString("outline_node_id"), rs.getString("title"),
                rs.getString("generation_status"), rs.getInt("table_count"),
                rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getLong("revision")), bidId);
        return new BidWorkspaceViews.OutlineView(nodes, chapters);
    }

    Optional<BidWorkspaceViews.ChapterDetail> findChapterDetail(
            String bidId, String chapterId) {
        Optional<BidWorkspace.Chapter> chapter = jdbcTemplate.query("""
                SELECT * FROM bid_chapter WHERE bid_id = ? AND id = ?
                """, BidJdbcMappers.CHAPTER, bidId, chapterId).stream().findFirst();
        if (chapter.isEmpty()) {
            return Optional.empty();
        }
        List<BidProductionState.ReviewIssue> issues = jdbcTemplate.query("""
                SELECT id, chapter_id, severity, issue_code, message, suggestion, status
                FROM bid_review_issue WHERE bid_id = ? AND (chapter_id = ? OR chapter_id IS NULL)
                ORDER BY severity, created_at, id
                """, (rs, row) -> new BidProductionState.ReviewIssue(
                rs.getString("id"), rs.getString("chapter_id"), rs.getString("severity"),
                rs.getString("issue_code"), rs.getString("message"), rs.getString("suggestion"),
                rs.getString("status")), bidId, chapterId);
        return Optional.of(new BidWorkspaceViews.ChapterDetail(chapter.get(), issues));
    }

    BidWorkspaceViews.GenerationProgress loadGenerationProgress(String bidId) {
        BidWorkspace.GenerationTask task = tasks.latestTask(bidId);
        if (task == null) {
            return BidWorkspaceViews.GenerationProgress.idle();
        }
        List<BidProductionState.GenerationEvent> events = jdbcTemplate.query("""
                SELECT * FROM bid_generation_event WHERE bid_id = ? AND task_id = ?
                ORDER BY occurred_at DESC, id DESC LIMIT 20
                """, (rs, row) -> new BidProductionState.GenerationEvent(
                rs.getString("id"), rs.getString("task_id"), rs.getString("chapter_id"),
                rs.getString("event_type"), rs.getString("message"),
                rs.getTimestamp("occurred_at").toLocalDateTime()), bidId, task.id());
        int progress = task.totalUnits() == 0 ? 0
                : Math.min(100, task.completedUnits() * 100 / task.totalUnits());
        return new BidWorkspaceViews.GenerationProgress(
                task.id(), task.status(), task.totalUnits(), task.completedUnits(), progress,
                task.errorMessage(), task.createdAt(), task.finishedAt(), events);
    }

    private BidWorkspace.SourceFile sourceFile(String bidId) {
        return jdbcTemplate.query("""
                SELECT id, original_file_name, media_type, file_size, parse_status,
                       parse_stage, parse_progress, error_message, uploaded_at,
                       parse_started_at, parse_finished_at, overview_status,
                       overview_error_message, scoring_status, scoring_error_message
                FROM bid_source_file WHERE bid_id = ?
                """, (rs, row) -> new BidWorkspace.SourceFile(
                rs.getString("id"), rs.getString("original_file_name"), rs.getString("media_type"),
                rs.getLong("file_size"), rs.getString("parse_status"), rs.getString("parse_stage"),
                rs.getInt("parse_progress"), rs.getString("error_message"),
                rs.getString("overview_status"), rs.getString("overview_error_message"),
                rs.getString("scoring_status"), rs.getString("scoring_error_message"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                BidJdbcMappers.nullableTime(rs.getTimestamp("parse_started_at")),
                BidJdbcMappers.nullableTime(rs.getTimestamp("parse_finished_at"))), bidId)
                .stream().findFirst().orElse(null);
    }

    private List<String> selectedAssets(String bidId) {
        return jdbcTemplate.queryForList(
                "SELECT asset_id FROM bid_asset_selection WHERE bid_id = ? ORDER BY selected_at",
                String.class, bidId);
    }

    private BidDocument latestBid(BidDocument bid) {
        return findBid(bid.id(), bid.ownerId()).orElse(bid);
    }
}
