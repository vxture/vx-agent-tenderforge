package com.td.czghagent.infrastructure.repository;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.repository.BidRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class JdbcBidDraftPersistence {
    private final JdbcTemplate jdbcTemplate;

    JdbcBidDraftPersistence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertBid(BidDocument bid) {
        jdbcTemplate.update("""
                INSERT INTO bid_document(
                    id, owner_id, org_id, workspace_id, code, writing_method, title, target_pages,
                    bidding_mode, workflow_step, status, content_stale
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, bid.id(), bid.ownerId(), bid.tenant().orgId(), bid.tenant().workspaceId(),
                bid.code(), bid.writingMethod(), bid.title(),
                bid.targetPages(), bid.biddingMode(), bid.workflowStep(), bid.status(),
                bid.contentStale());
    }

    boolean updateSetup(String bidId, String title, int targetPages,
                        String biddingMode, long revision) {
        return jdbcTemplate.update("""
                UPDATE bid_document
                SET title = ?, target_pages = ?, bidding_mode = ?, workflow_step = 'INTERPRETATION',
                    content_stale = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_chapter c WHERE c.bid_id = bid_document.id
                          AND (c.generation_status IN ('READY', 'MANUAL')
                            OR CHAR_LENGTH(TRIM(c.content)) > 0)
                    ) THEN TRUE ELSE content_stale END,
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1
                WHERE id = ? AND revision = ?
                """, title, targetPages, biddingMode, bidId, revision) == 1;
    }

    void insertSourceFile(BidRepository.SourceFileRecord source) {
        jdbcTemplate.update("DELETE FROM bid_source_file WHERE bid_id = ?", source.bidId());
        jdbcTemplate.update("DELETE FROM bid_scoring_criterion WHERE bid_id = ?", source.bidId());
        jdbcTemplate.update("""
                INSERT INTO bid_source_file(
                    id, bid_id, original_file_name, object_key, media_type, file_size,
                    content_hash, parse_status, extracted_text, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, source.id(), source.bidId(), source.originalFileName(), source.objectKey(),
                source.mediaType(), source.fileSize(), source.contentHash(), source.parseStatus(),
                source.extractedText(), source.errorMessage());
        jdbcTemplate.update("""
                UPDATE bid_document SET workflow_step = 'INTERPRETATION', status = 'DRAFT',
                    content_stale = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_chapter c WHERE c.bid_id = bid_document.id
                          AND (c.generation_status IN ('READY', 'MANUAL')
                            OR CHAR_LENGTH(TRIM(c.content)) > 0)
                    ) THEN TRUE ELSE content_stale END,
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, source.bidId());
    }

    Optional<BidRepository.SourceFileRecord> findSourceFile(String bidId) {
        return jdbcTemplate.query("SELECT * FROM bid_source_file WHERE bid_id = ?", (rs, row) ->
                new BidRepository.SourceFileRecord(
                        rs.getString("id"), rs.getString("bid_id"),
                        rs.getString("original_file_name"), rs.getString("object_key"),
                        rs.getString("media_type"), rs.getLong("file_size"),
                        rs.getString("content_hash"), rs.getString("parse_status"),
                        rs.getString("extracted_text"), rs.getString("error_message"),
                        rs.getString("overview_status"), rs.getString("overview_content"),
                        rs.getString("overview_error_message"), rs.getString("scoring_status"),
                        rs.getString("scoring_content"), rs.getString("scoring_error_message")), bidId)
                .stream().findFirst();
    }

    boolean beginSourceParse(String sourceId, String bidId) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_source_file
                SET overview_status = CASE WHEN parse_status = 'FAILED'
                        AND overview_status = 'SUCCEEDED'
                        THEN overview_status ELSE 'PENDING' END,
                    overview_error_message = CASE WHEN parse_status = 'FAILED'
                        AND overview_status = 'SUCCEEDED'
                        THEN overview_error_message ELSE NULL END,
                    scoring_status = CASE WHEN parse_status = 'FAILED'
                        AND scoring_status = 'SUCCEEDED'
                        THEN scoring_status ELSE 'PENDING' END,
                    scoring_error_message = CASE WHEN parse_status = 'FAILED'
                        AND scoring_status = 'SUCCEEDED'
                        THEN scoring_error_message ELSE NULL END,
                    parse_status = 'PARSING', parse_stage = 'QUEUED', parse_progress = 0,
                    error_message = NULL, parse_started_at = CURRENT_TIMESTAMP,
                    parse_finished_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND bid_id = ? AND parse_status <> 'PARSING'
                """, sourceId, bidId);
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE bid_document SET status = 'PARSING', error_message = NULL,
                        updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                    """, bidId);
        }
        return updated == 1;
    }

    boolean updateSourceParseProgress(String sourceId, String stage, int progress) {
        return jdbcTemplate.update("""
                UPDATE bid_source_file SET parse_stage = ?, parse_progress = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND parse_status = 'PARSING'
                """, stage, progress, sourceId) == 1;
    }

    boolean saveParsedSource(String sourceId, String bidId, String extractedText,
                             List<BidRepository.SourceSegmentRecord> segments) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_source_file SET extracted_text = ?, parse_stage = 'EXTRACTED',
                    parse_progress = 25, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND bid_id = ? AND parse_status = 'PARSING'
                """, extractedText, sourceId, bidId);
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update("DELETE FROM bid_source_segment WHERE source_id = ?", sourceId);
        for (BidRepository.SourceSegmentRecord segment : segments) {
            jdbcTemplate.update("""
                    INSERT INTO bid_source_segment(
                        source_id, bid_id, sequence_no, locator_type, locator, segment_text
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, sourceId, bidId, segment.sequence(), segment.locatorType(),
                    segment.locator(), segment.text());
        }
        return true;
    }

    List<BidRepository.SourceSegmentRecord> listSourceSegments(String sourceId) {
        return jdbcTemplate.query("""
                SELECT sequence_no, locator_type, locator, segment_text
                FROM bid_source_segment WHERE source_id = ? ORDER BY sequence_no
                """, (rs, row) -> new BidRepository.SourceSegmentRecord(
                rs.getInt("sequence_no"), rs.getString("locator_type"),
                rs.getString("locator"), rs.getString("segment_text")), sourceId);
    }

    boolean beginInterpretationObject(String sourceId, String objectType) {
        String statusColumn = objectColumn(objectType, "status");
        String errorColumn = objectColumn(objectType, "error_message");
        String stage = "PROJECT_OVERVIEW".equals(objectType)
                ? "PROJECT_OVERVIEW" : "TECHNICAL_SCORING";
        return jdbcTemplate.update("UPDATE bid_source_file SET " + statusColumn
                + " = 'RUNNING', " + errorColumn + " = NULL, parse_stage = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND parse_status = 'PARSING' "
                + "AND " + statusColumn + " <> 'SUCCEEDED'", stage, sourceId) == 1;
    }

    boolean completeInterpretationObject(
            String sourceId, String bidId, String objectType, String content,
            BidWorkspace.Criterion criterion
    ) {
        String statusColumn = objectColumn(objectType, "status");
        String contentColumn = objectColumn(objectType, "content");
        String errorColumn = objectColumn(objectType, "error_message");
        String completedColumn = objectColumn(objectType, "completed_at");
        int updated = jdbcTemplate.update("UPDATE bid_source_file SET " + statusColumn
                + " = 'SUCCEEDED', " + contentColumn + " = ?, " + errorColumn + " = NULL, "
                + completedColumn + " = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND bid_id = ? AND parse_status = 'PARSING'",
                content, sourceId, bidId);
        if (updated == 0) {
            return false;
        }
        replaceCriterion(bidId, criterion, false);
        return true;
    }

    boolean failInterpretationObject(String sourceId, String objectType, String errorMessage) {
        String statusColumn = objectColumn(objectType, "status");
        String errorColumn = objectColumn(objectType, "error_message");
        return jdbcTemplate.update("UPDATE bid_source_file SET " + statusColumn
                + " = 'FAILED', " + errorColumn + " = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND parse_status = 'PARSING'", errorMessage, sourceId) == 1;
    }

    boolean completeSourceParse(String sourceId, String bidId) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_source_file
                SET parse_status = 'SUCCEEDED', parse_stage = 'COMPLETE', parse_progress = 100,
                    error_message = NULL,
                    parse_finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND bid_id = ? AND parse_status = 'PARSING'
                  AND overview_status = 'SUCCEEDED' AND scoring_status = 'SUCCEEDED'
                """, sourceId, bidId);
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update("""
                UPDATE bid_document SET workflow_step = 'INTERPRETATION',
                    status = 'INTERPRETATION_READY', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, bidId);
        return true;
    }

    boolean failSourceParse(String sourceId, String bidId, String errorMessage) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_source_file
                SET parse_status = 'FAILED', parse_stage = 'FAILED', error_message = ?,
                    parse_finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND bid_id = ? AND parse_status = 'PARSING'
                """, errorMessage, sourceId, bidId);
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE bid_document SET status = 'FAILED', error_message = ?,
                        updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                    """, errorMessage, bidId);
        }
        return updated == 1;
    }

    void replaceCriteria(String bidId, List<BidWorkspace.Criterion> criteria, boolean manual) {
        jdbcTemplate.update("DELETE FROM bid_scoring_criterion WHERE bid_id = ?", bidId);
        for (BidWorkspace.Criterion item : criteria) {
            insertCriterion(bidId, item, manual);
        }
        jdbcTemplate.update("""
                UPDATE bid_document SET workflow_step = 'INTERPRETATION',
                    status = 'INTERPRETATION_READY', error_message = NULL,
                    content_stale = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_chapter c WHERE c.bid_id = bid_document.id
                          AND (c.generation_status IN ('READY', 'MANUAL')
                            OR CHAR_LENGTH(TRIM(c.content)) > 0)
                    ) THEN TRUE ELSE content_stale END,
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, bidId);
    }

    private void replaceCriterion(String bidId, BidWorkspace.Criterion item, boolean manual) {
        jdbcTemplate.update(
                "DELETE FROM bid_scoring_criterion WHERE bid_id = ? AND item_type = ?",
                bidId, item.type());
        insertCriterion(bidId, item, manual);
    }

    private void insertCriterion(String bidId, BidWorkspace.Criterion item, boolean manual) {
        jdbcTemplate.update("""
                INSERT INTO bid_scoring_criterion(
                    id, bid_id, item_type, title, description, score,
                    source_excerpt, source_locator, scope, confidence, sort_order, manually_edited
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, item.id(), bidId, item.type(), item.title(), item.description(), item.score(),
                item.sourceExcerpt(), item.sourceLocator(), item.scope(), item.confidence(),
                item.sortOrder(), manual || item.manuallyEdited());
    }

    private String objectColumn(String objectType, String suffix) {
        String prefix = switch (objectType) {
            case "PROJECT_OVERVIEW" -> "overview";
            case "TECHNICAL_SCORING" -> "scoring";
            default -> throw new IllegalArgumentException("Unsupported interpretation object");
        };
        return prefix + "_" + suffix;
    }

    void replaceAssetSelections(String bidId, String ownerId, List<String> assetIds) {
        jdbcTemplate.update("DELETE FROM bid_asset_selection WHERE bid_id = ?", bidId);
        for (String assetId : assetIds) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO bid_asset_selection(bid_id, asset_id)
                    SELECT ?, id FROM bid_reference_asset
                    WHERE id = ? AND owner_id = ? AND status = 'ACTIVE'
                    """, bidId, assetId, ownerId);
            if (inserted != 1) {
                throw new IllegalArgumentException("Selected asset is unavailable: " + assetId);
            }
        }
    }

    void replaceOutline(String bidId, List<BidWorkspace.OutlineNode> nodes, boolean confirmed) {
        List<BidWorkspace.OutlineNode> previousOutline = jdbcTemplate.query(
                "SELECT * FROM bid_outline_node WHERE bid_id = ?",
                BidJdbcMappers.OUTLINE, bidId);
        List<BidWorkspace.Chapter> previousChapters = jdbcTemplate.query(
                "SELECT * FROM bid_chapter WHERE bid_id = ?",
                BidJdbcMappers.CHAPTER, bidId);
        Map<String, BidWorkspace.Chapter> previousByNodeId = new HashMap<>();
        Map<String, BidWorkspace.Chapter> previousByTitle = new HashMap<>();
        Set<String> ambiguousTitles = new HashSet<>();
        previousChapters.forEach(chapter -> {
            previousByNodeId.put(chapter.outlineNodeId(), chapter);
            String title = BidJdbcMappers.normalizeTitle(chapter.title());
            if (previousByTitle.putIfAbsent(title, chapter) != null) {
                ambiguousTitles.add(title);
            }
        });
        ambiguousTitles.forEach(previousByTitle::remove);
        boolean hadContent = previousChapters.stream().anyMatch(chapter ->
                !BidJdbcMappers.safe(chapter.content()).isBlank()
                        || Set.of("READY", "MANUAL").contains(chapter.generationStatus()));
        if (sameOutlineShape(previousOutline, nodes)) {
            updateOutlineInPlace(bidId, nodes);
            updateBidAfterOutlineChange(bidId, confirmed, hadContent);
            return;
        }
        if (hasChapterHistory(bidId)) {
            throw new BusinessException(
                    "BID_OUTLINE_HISTORY_LOCKED",
                    "正文已有生成历史，只能调整目录名称、页数和写作要求；不能增删或改变目录层级",
                    409);
        }
        jdbcTemplate.update("DELETE FROM bid_chapter WHERE bid_id = ?", bidId);
        for (int level = 3; level >= 1; level--) {
            jdbcTemplate.update(
                    "DELETE FROM bid_outline_node WHERE bid_id = ? AND level_no = ?",
                    bidId, level);
        }
        insertOutline(bidId, nodes, previousByNodeId, previousByTitle);
        updateBidAfterOutlineChange(bidId, confirmed, hadContent);
    }

    void replaceGeneratedOutline(String bidId, List<BidWorkspace.OutlineNode> nodes) {
        List<BidWorkspace.OutlineNode> previousOutline = jdbcTemplate.query(
                "SELECT * FROM bid_outline_node WHERE bid_id = ?",
                BidJdbcMappers.OUTLINE, bidId);
        if (previousOutline.isEmpty()) {
            replaceOutline(bidId, nodes, false);
            return;
        }
        List<BidWorkspace.Chapter> previousChapters = jdbcTemplate.query(
                "SELECT * FROM bid_chapter WHERE bid_id = ?",
                BidJdbcMappers.CHAPTER, bidId);
        boolean hadContent = previousChapters.stream().anyMatch(chapter ->
                !BidJdbcMappers.safe(chapter.content()).isBlank()
                        || Set.of("READY", "MANUAL").contains(chapter.generationStatus()));
        archiveCurrentOutline(bidId);
        jdbcTemplate.update("""
                UPDATE bid_snapshot_outline SET chapter_id = NULL
                WHERE chapter_id IN (SELECT id FROM bid_chapter WHERE bid_id = ?)
                """, bidId);
        jdbcTemplate.update("DELETE FROM bid_chapter_version WHERE bid_id = ?", bidId);
        jdbcTemplate.update("DELETE FROM bid_generation_unit WHERE bid_id = ?", bidId);
        jdbcTemplate.update("DELETE FROM bid_review_issue WHERE bid_id = ?", bidId);
        jdbcTemplate.update("DELETE FROM bid_chapter WHERE bid_id = ?", bidId);
        for (int level = 3; level >= 1; level--) {
            jdbcTemplate.update(
                    "DELETE FROM bid_outline_node WHERE bid_id = ? AND level_no = ?",
                    bidId, level);
        }
        insertOutline(bidId, nodes, Map.of(), Map.of());
        updateBidAfterOutlineChange(bidId, false, hadContent);
        jdbcTemplate.update("""
                UPDATE bid_document SET content_status = 'DRAFT', content_hash = NULL,
                    stale_reason = '目录已重新生成，旧正文已归档'
                WHERE id = ?
                """, bidId);
    }

    private void archiveCurrentOutline(String bidId) {
        Integer outlineVersion = jdbcTemplate.queryForObject(
                "SELECT outline_version FROM bid_document WHERE id = ?",
                Integer.class, bidId);
        Map<String, Object> archive = Map.of(
                "outline", jdbcTemplate.queryForList(
                        "SELECT * FROM bid_outline_node WHERE bid_id = ? ORDER BY sort_order, level_no, id",
                        bidId),
                "chapters", jdbcTemplate.queryForList(
                        "SELECT * FROM bid_chapter WHERE bid_id = ? ORDER BY updated_at, id", bidId),
                "chapterVersions", jdbcTemplate.queryForList(
                        "SELECT * FROM bid_chapter_version WHERE bid_id = ? ORDER BY created_at, id",
                        bidId),
                "generationUnits", jdbcTemplate.queryForList(
                        "SELECT * FROM bid_generation_unit WHERE bid_id = ? ORDER BY updated_at, id",
                        bidId),
                "reviewIssues", jdbcTemplate.queryForList(
                        "SELECT * FROM bid_review_issue WHERE bid_id = ? ORDER BY created_at, id",
                        bidId));
        jdbcTemplate.update("""
                INSERT INTO bid_outline_regeneration_archive(
                    id, bid_id, source_outline_version, outline_json, chapter_json,
                    chapter_version_json, generation_unit_json, review_issue_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), bidId,
                outlineVersion == null ? 0 : outlineVersion,
                BidJdbcMappers.writeJson(archive.get("outline")),
                BidJdbcMappers.writeJson(archive.get("chapters")),
                BidJdbcMappers.writeJson(archive.get("chapterVersions")),
                BidJdbcMappers.writeJson(archive.get("generationUnits")),
                BidJdbcMappers.writeJson(archive.get("reviewIssues")));
    }

    private void insertOutline(
            String bidId,
            List<BidWorkspace.OutlineNode> nodes,
            Map<String, BidWorkspace.Chapter> previousByNodeId,
            Map<String, BidWorkspace.Chapter> previousByTitle
    ) {
        nodes.stream().sorted(Comparator.comparingInt(BidWorkspace.OutlineNode::level)
                        .thenComparingInt(BidWorkspace.OutlineNode::sortOrder))
                .forEach(node -> jdbcTemplate.update("""
                        INSERT INTO bid_outline_node(
                            id, bid_id, parent_id, level_no, title, planned_pages, sort_order,
                            task_brief, must_keywords_json, scoring_point_ids_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, node.id(), bidId, node.parentId(), node.level(), node.title(),
                        node.plannedPages(), node.sortOrder(), node.taskBrief(),
                        BidJdbcMappers.writeStringList(node.mustKeywords()),
                        BidJdbcMappers.writeStringList(node.scoringPointIds())));
        Set<String> parentIds = new HashSet<>();
        nodes.stream().map(BidWorkspace.OutlineNode::parentId)
                .filter(id -> id != null).forEach(parentIds::add);
        for (BidWorkspace.OutlineNode node : nodes) {
            if (parentIds.contains(node.id())) {
                continue;
            }
            BidWorkspace.Chapter previous = previousByNodeId.get(node.id());
            if (previous == null) {
                previous = previousByTitle.get(BidJdbcMappers.normalizeTitle(node.title()));
            }
            jdbcTemplate.update("""
                    INSERT INTO bid_chapter(
                        id, bid_id, outline_node_id, title, content, generation_status, revision
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, previous == null ? UUID.randomUUID().toString() : previous.id(),
                    bidId, node.id(), BidJdbcMappers.normalizeTitle(node.title()),
                    previous == null ? "" : previous.content(),
                    previous == null ? "PENDING" : previous.generationStatus(),
                    previous == null ? 0 : previous.revision());
        }
    }

    void confirmOutline(String bidId) {
        jdbcTemplate.update("""
                UPDATE bid_document SET workflow_step = 'OUTLINE', status = 'OUTLINE_READY',
                    error_message = NULL, updated_at = CURRENT_TIMESTAMP, revision = revision + 1
                WHERE id = ?
                """, bidId);
    }

    boolean updateChapter(String bidId, String chapterId, String content, long revision) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_chapter SET content = ?, generation_status = 'MANUAL',
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1
                WHERE bid_id = ? AND id = ? AND revision = ?
                """, content, bidId, chapterId, revision);
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE bid_document SET status = 'CONTENT_READY', updated_at = CURRENT_TIMESTAMP,
                        revision = revision + 1 WHERE id = ?
                    """, bidId);
        }
        return updated == 1;
    }

    void markContentStale(String bidId) {
        jdbcTemplate.update("""
                UPDATE bid_document SET content_stale = TRUE, updated_at = CURRENT_TIMESTAMP,
                    revision = revision + 1 WHERE id = ?
                """, bidId);
    }

    private boolean sameOutlineShape(List<BidWorkspace.OutlineNode> current,
                                     List<BidWorkspace.OutlineNode> replacement) {
        if (current.size() != replacement.size()) {
            return false;
        }
        Map<String, BidWorkspace.OutlineNode> currentById = new HashMap<>();
        current.forEach(node -> currentById.put(node.id(), node));
        return replacement.stream().allMatch(node -> {
            BidWorkspace.OutlineNode previous = currentById.get(node.id());
            return previous != null && previous.level() == node.level()
                    && BidJdbcMappers.safe(previous.parentId())
                    .equals(BidJdbcMappers.safe(node.parentId()));
        });
    }

    private void updateOutlineInPlace(String bidId, List<BidWorkspace.OutlineNode> nodes) {
        nodes.forEach(node -> {
            jdbcTemplate.update("""
                    UPDATE bid_outline_node
                    SET title = ?, planned_pages = ?, sort_order = ?, task_brief = ?,
                        must_keywords_json = ?, scoring_point_ids_json = ?, revision = revision + 1
                    WHERE id = ? AND bid_id = ?
                    """, node.title(), node.plannedPages(), node.sortOrder(), node.taskBrief(),
                    BidJdbcMappers.writeStringList(node.mustKeywords()),
                    BidJdbcMappers.writeStringList(node.scoringPointIds()), node.id(), bidId);
            jdbcTemplate.update("""
                    UPDATE bid_chapter SET title = ? WHERE bid_id = ? AND outline_node_id = ?
                    """, BidJdbcMappers.normalizeTitle(node.title()), bidId, node.id());
        });
    }

    private boolean hasChapterHistory(String bidId) {
        Long generationUnits = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_generation_unit u
                JOIN bid_chapter c ON c.id = u.chapter_id WHERE c.bid_id = ?
                """, Long.class, bidId);
        Long chapterVersions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_chapter_version v WHERE v.bid_id = ?
                """, Long.class, bidId);
        return generationUnits != null && generationUnits > 0
                || chapterVersions != null && chapterVersions > 0;
    }

    private void updateBidAfterOutlineChange(
            String bidId, boolean confirmed, boolean hadContent) {
        jdbcTemplate.update("""
                UPDATE bid_document SET workflow_step = 'OUTLINE', status = ?,
                    content_stale = CASE WHEN EXISTS(
                        SELECT 1 FROM bid_export e WHERE e.bid_id = bid_document.id
                    ) OR ? THEN TRUE ELSE content_stale END,
                    updated_at = CURRENT_TIMESTAMP, revision = revision + 1 WHERE id = ?
                """, confirmed ? "OUTLINE_READY" : "INTERPRETATION_READY", hadContent, bidId);
    }
}
