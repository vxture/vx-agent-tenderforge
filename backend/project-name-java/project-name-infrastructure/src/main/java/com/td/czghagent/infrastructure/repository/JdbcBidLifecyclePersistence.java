package com.td.czghagent.infrastructure.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

final class JdbcBidLifecyclePersistence {
    private final JdbcTemplate jdbcTemplate;

    JdbcBidLifecyclePersistence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void assignWorkflowRun(String taskId, String workflowRunId, String snapshotHash) {
        jdbcTemplate.update("""
                UPDATE bid_generation_task SET workflow_run_id = ?, snapshot_hash = ?,
                    heartbeat_at = CURRENT_TIMESTAMP WHERE id = ?
                """, workflowRunId, snapshotHash, taskId);
    }

    void startUnit(String taskId, String chapterId) {
        jdbcTemplate.update("""
                UPDATE bid_generation_unit SET status = 'RUNNING', attempt_count = attempt_count + 1,
                    started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND chapter_id = ? AND unit_index = 0
                """, taskId, chapterId);
        jdbcTemplate.update(
                "UPDATE bid_chapter SET generation_status = 'GENERATING' WHERE id = ?", chapterId);
        heartbeat(taskId);
    }

    void completeUnit(String taskId, String chapterId, String summary) {
        jdbcTemplate.update("""
                UPDATE bid_generation_unit SET status = 'SUCCEEDED', summary = ?, error_message = NULL,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND chapter_id = ? AND unit_index = 0
                """, summary, taskId, chapterId);
        heartbeat(taskId);
    }

    void failUnit(String taskId, String chapterId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE bid_generation_unit SET status = 'FAILED', error_message = ?,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND chapter_id = ? AND unit_index = 0
                """, errorMessage, taskId, chapterId);
        jdbcTemplate.update(
                "UPDATE bid_chapter SET generation_status = 'FAILED' WHERE id = ?", chapterId);
        heartbeat(taskId);
    }

    void appendEvent(String taskId, String bidId, String chapterId,
                     String eventType, String message) {
        jdbcTemplate.update("""
                INSERT INTO bid_generation_event(
                    id, task_id, bid_id, chapter_id, event_type, message
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), taskId, bidId, chapterId, eventType, message);
    }

    void saveChapterVersion(String bidId, String chapterId, String sourceType,
                            String content, String contentHash, String summary, String userId) {
        Integer latest = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) FROM bid_chapter_version WHERE chapter_id = ?
                """, Integer.class, chapterId);
        jdbcTemplate.update("""
                INSERT INTO bid_chapter_version(
                    id, bid_id, chapter_id, version_no, source_type, content,
                    content_hash, summary, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), bidId, chapterId,
                (latest == null ? 0 : latest) + 1, sourceType, content,
                contentHash, summary, userId);
    }

    boolean applyReviewedChapter(String bidId, String chapterId, long revision, String content) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_chapter SET content = ?, generation_status = 'READY',
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1
                WHERE bid_id = ? AND id = ? AND revision = ?
                """, content, bidId, chapterId, revision);
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE bid_document SET updated_at = CURRENT_TIMESTAMP,
                        revision = revision + 1 WHERE id = ?
                    """, bidId);
        }
        return updated == 1;
    }

    String createLayoutJob(String bidId, String userId, String inputHash, int targetPages) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO bid_layout_job(
                    id, bid_id, status, input_hash, target_pages, created_by
                ) VALUES (?, ?, 'PENDING', ?, ?, ?)
                """, id, bidId, inputHash, targetPages, userId);
        jdbcTemplate.update("""
                UPDATE bid_document SET status = 'LAYOUT_QUEUED',
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, bidId);
        return id;
    }

    void assignLayoutWorkflow(String layoutJobId, String bidId, String workflowRunId) {
        jdbcTemplate.update("""
                UPDATE bid_layout_job SET workflow_run_id = ? WHERE id = ? AND bid_id = ?
                """, workflowRunId, layoutJobId, bidId);
    }

    void startLayoutJob(String layoutJobId, String bidId) {
        jdbcTemplate.update("""
                UPDATE bid_layout_job SET status = 'RUNNING' WHERE id = ? AND bid_id = ?
                """, layoutJobId, bidId);
        jdbcTemplate.update(
                "UPDATE bid_document SET status = 'LAYOUT_RUNNING' WHERE id = ?", bidId);
    }

    void completeLayoutJob(String layoutJobId, String bidId, Integer actualPages,
                           String qaStatus, String qaSummary) {
        jdbcTemplate.update("""
                UPDATE bid_layout_job SET status = 'SUCCEEDED', actual_pages = ?, qa_status = ?,
                    qa_summary = ?, finished_at = CURRENT_TIMESTAMP WHERE id = ? AND bid_id = ?
                """, actualPages, qaStatus, qaSummary, layoutJobId, bidId);
        jdbcTemplate.update("""
                UPDATE bid_document SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, bidId);
    }

    void failLayoutJob(String layoutJobId, String bidId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE bid_layout_job SET status = 'FAILED', qa_status = 'FAILED', error_message = ?,
                    finished_at = CURRENT_TIMESTAMP WHERE id = ? AND bid_id = ?
                """, errorMessage, layoutJobId, bidId);
        jdbcTemplate.update("""
                UPDATE bid_document SET status = 'FAILED', error_message = ?,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, errorMessage, bidId);
    }

    private void heartbeat(String taskId) {
        jdbcTemplate.update("""
                UPDATE bid_generation_task SET heartbeat_at = CURRENT_TIMESTAMP WHERE id = ?
                """, taskId);
    }
}
