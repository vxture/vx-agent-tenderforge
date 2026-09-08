// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidReferenceChunk;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class JdbcGenerationSnapshotPersistence {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcGenerationSnapshotPersistence(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    String create(String taskId, String ownerId, BidWorkspace workspace, String snapshotHash,
                  String solutionContract, String writingBible, String termRegistry, String commitmentRegistry,
                  String promptVersion, List<BidReferenceChunk> referenceChunks,
                  List<BidProductionRepository.GenerationUnitPlan> units) {
        String snapshotId = UUID.randomUUID().toString();
        insertRoot(snapshotId, taskId, ownerId, workspace, snapshotHash, writingBible,
                solutionContract, termRegistry, commitmentRegistry, promptVersion);
        insertRequirements(snapshotId, workspace.criteria());
        insertOutline(snapshotId, workspace);
        insertFacts(snapshotId, workspace.production().frozenFacts());
        insertReferenceChunks(snapshotId, referenceChunks);
        insertUnits(taskId, workspace.bid().id(), units);
        jdbcTemplate.update("UPDATE bid_generation_task SET snapshot_id = ?, snapshot_hash = ? WHERE id = ?",
                snapshotId, snapshotHash, taskId);
        jdbcTemplate.update("""
                UPDATE bid_document SET content_status = 'GENERATING', stale_reason = NULL WHERE id = ?
                """, workspace.bid().id());
        return snapshotId;
    }

    BidGenerationSnapshot load(String snapshotId, String ownerId) {
        SnapshotRoot root = jdbcTemplate.query("""
                SELECT * FROM bid_generation_snapshot WHERE id = ? AND owner_id = ?
                """, (rs, row) -> root(rs), snapshotId, ownerId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("生成快照不存在或无权访问"));
        return new BidGenerationSnapshot(
                root.id(), root.taskId(), root.bidId(), root.ownerId(), root.snapshotHash(),
                root.bidTitle(), root.biddingMode(), root.targetPages(),
                root.interpretationVersion(), root.outlineVersion(), root.solutionContract(), root.writingBible(),
                root.termRegistry(), root.commitmentRegistry(), root.promptVersion(), root.createdAt(),
                loadRequirements(snapshotId), loadOutline(snapshotId), loadFacts(snapshotId),
                loadReferenceChunks(snapshotId));
    }

    Optional<TenderAiGateway.BranchBlueprint> findBranchBlueprint(
            String snapshotId, String branchOutlineId, String inputHash) {
        return jdbcTemplate.query("""
                SELECT blueprint_payload FROM bid_snapshot_branch_blueprint
                WHERE snapshot_id = ? AND branch_outline_id = ? AND input_hash = ?
                """, (rs, row) -> decodeBlueprint(rs.getString("blueprint_payload")),
                snapshotId, branchOutlineId, inputHash).stream().findFirst();
    }

    void saveBranchBlueprint(
            String snapshotId, String branchOutlineId, String inputHash,
            TenderAiGateway.BranchBlueprint blueprint, String outputHash, String promptVersion) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO bid_snapshot_branch_blueprint(
                        id, snapshot_id, branch_outline_id, input_hash,
                        blueprint_payload, output_hash, prompt_version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), snapshotId, branchOutlineId, inputHash,
                    encodeBlueprint(blueprint), outputHash, promptVersion);
        } catch (DuplicateKeyException ignored) {
            // A concurrent retry committed the same immutable blueprint first.
        }
    }

    private void insertRoot(String id, String taskId, String ownerId, BidWorkspace workspace,
                            String hash, String bible, String solutionContract, String terms,
                            String commitments, String promptVersion) {
        jdbcTemplate.update("""
                INSERT INTO bid_generation_snapshot(
                    id, task_id, bid_id, owner_id, snapshot_hash, bid_title, bidding_mode,
                    target_pages, interpretation_version, outline_version, solution_contract, writing_bible,
                    term_registry, commitment_registry, prompt_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, taskId, workspace.bid().id(), ownerId, hash, workspace.bid().title(),
                workspace.bid().biddingMode(), workspace.bid().targetPages(),
                workspace.production().interpretationVersion(), workspace.production().outlineVersion(),
                solutionContract, bible, terms, commitments, promptVersion);
    }

    private void insertRequirements(String snapshotId, List<BidWorkspace.Criterion> criteria) {
        for (BidWorkspace.Criterion item : criteria) {
            jdbcTemplate.update("""
                    INSERT INTO bid_snapshot_requirement(
                        id, snapshot_id, source_criterion_id, item_type, title, description,
                        score, source_excerpt, source_locator, sort_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), snapshotId, item.id(), item.type(), item.title(),
                    item.description(), item.score(), item.sourceExcerpt(), item.sourceLocator(), item.sortOrder());
        }
    }

    private void insertOutline(String snapshotId, BidWorkspace workspace) {
        Map<String, BidWorkspace.Chapter> chapters = new HashMap<>();
        workspace.chapters().forEach(chapter -> chapters.put(chapter.outlineNodeId(), chapter));
        for (BidWorkspace.OutlineNode node : workspace.outline()) {
            BidWorkspace.Chapter chapter = chapters.get(node.id());
            jdbcTemplate.update("""
                    INSERT INTO bid_snapshot_outline(
                        id, snapshot_id, source_outline_id, chapter_id, chapter_generation_status,
                        parent_source_outline_id, level_no, title, planned_pages, sort_order,
                        task_brief, must_keywords, scoring_point_ids
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), snapshotId, node.id(),
                    chapter == null ? null : chapter.id(),
                    chapter == null ? null : chapter.generationStatus(), node.parentId(), node.level(),
                    node.title(), node.plannedPages(), node.sortOrder(), node.taskBrief(),
                    join(node.mustKeywords()), join(node.scoringPointIds()));
        }
    }

    private void insertFacts(
            String snapshotId,
            List<com.td.czghagent.domain.model.BidProductionState.FrozenFact> facts
    ) {
        for (var fact : facts) {
            jdbcTemplate.update("""
                    INSERT INTO bid_snapshot_fact(
                        id, snapshot_id, fact_type, fact_name, fact_value,
                        source_locator, forbidden_values, sort_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), snapshotId, fact.type(), fact.name(), fact.value(),
                    fact.sourceLocator(), join(fact.forbiddenValues()), fact.sortOrder());
        }
    }

    private void insertReferenceChunks(String snapshotId, List<BidReferenceChunk> chunks) {
        for (BidReferenceChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO bid_snapshot_asset_chunk(
                        id, snapshot_id, source_chunk_id, asset_id, asset_category, asset_name,
                        heading, source_locator, content, content_hash, chunk_index
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), snapshotId, chunk.id(), chunk.assetId(),
                    chunk.category(), chunk.assetName(), chunk.heading(), chunk.sourceLocator(),
                    chunk.content(), chunk.contentHash(), chunk.chunkIndex());
        }
    }

    private void insertUnits(String taskId, String bidId,
                             List<BidProductionRepository.GenerationUnitPlan> units) {
        for (var unit : units) {
            jdbcTemplate.update("""
                    INSERT INTO bid_generation_unit(
                        id, task_id, bid_id, chapter_id, unit_index, unit_title,
                        idempotency_key, status, word_budget
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                    """, unit.id(), taskId, bidId, unit.chapterId(), unit.unitIndex(),
                    unit.unitTitle(), unit.idempotencyKey(), unit.wordBudget());
        }
    }

    private List<BidGenerationSnapshot.Requirement> loadRequirements(String snapshotId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_snapshot_requirement WHERE snapshot_id = ? ORDER BY sort_order, id
                """, (rs, row) -> new BidGenerationSnapshot.Requirement(
                rs.getString("source_criterion_id"), rs.getString("item_type"), rs.getString("title"),
                rs.getString("description"), rs.getObject("score", Double.class),
                rs.getString("source_excerpt"), rs.getString("source_locator"), rs.getInt("sort_order")
        ), snapshotId);
    }

    private List<BidGenerationSnapshot.Outline> loadOutline(String snapshotId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_snapshot_outline WHERE snapshot_id = ? ORDER BY sort_order, id
                """, (rs, row) -> new BidGenerationSnapshot.Outline(
                rs.getString("source_outline_id"), rs.getString("chapter_id"),
                rs.getString("chapter_generation_status"), rs.getString("parent_source_outline_id"),
                rs.getInt("level_no"), rs.getString("title"), rs.getInt("planned_pages"),
                rs.getInt("sort_order"), rs.getString("task_brief"), split(rs.getString("must_keywords")),
                split(rs.getString("scoring_point_ids"))
        ), snapshotId);
    }

    private List<BidGenerationSnapshot.Fact> loadFacts(String snapshotId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_snapshot_fact WHERE snapshot_id = ? ORDER BY sort_order, id
                """, (rs, row) -> new BidGenerationSnapshot.Fact(
                rs.getString("fact_type"), rs.getString("fact_name"), rs.getString("fact_value"),
                rs.getString("source_locator"), split(rs.getString("forbidden_values")),
                rs.getInt("sort_order")
        ), snapshotId);
    }

    private List<BidGenerationSnapshot.ReferenceChunk> loadReferenceChunks(String snapshotId) {
        return jdbcTemplate.query("""
                SELECT * FROM bid_snapshot_asset_chunk
                WHERE snapshot_id = ? ORDER BY asset_id, chunk_index, id
                """, (rs, row) -> new BidGenerationSnapshot.ReferenceChunk(
                rs.getString("source_chunk_id"), rs.getString("asset_id"),
                rs.getString("asset_category"), rs.getString("asset_name"), rs.getString("heading"),
                rs.getString("source_locator"), rs.getString("content"), rs.getString("content_hash"),
                rs.getInt("chunk_index")
        ), snapshotId);
    }

    private SnapshotRoot root(ResultSet rs) throws SQLException {
        return new SnapshotRoot(
                rs.getString("id"), rs.getString("task_id"), rs.getString("bid_id"),
                rs.getString("owner_id"), rs.getString("snapshot_hash"), rs.getString("bid_title"),
                rs.getString("bidding_mode"), rs.getInt("target_pages"),
                rs.getInt("interpretation_version"), rs.getInt("outline_version"),
                rs.getString("solution_contract"), rs.getString("writing_bible"), rs.getString("term_registry"),
                rs.getString("commitment_registry"), rs.getString("prompt_version"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join("\n", values);
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : value.lines().filter(line -> !line.isBlank()).toList();
    }

    private String encodeBlueprint(TenderAiGateway.BranchBlueprint value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("二级技术域蓝图无法序列化", exception);
        }
    }

    private TenderAiGateway.BranchBlueprint decodeBlueprint(String value) {
        try {
            return objectMapper.readValue(value, TenderAiGateway.BranchBlueprint.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("已保存的二级技术域蓝图无效", exception);
        }
    }

    private record SnapshotRoot(
            String id, String taskId, String bidId, String ownerId, String snapshotHash,
            String bidTitle, String biddingMode, int targetPages, int interpretationVersion,
            int outlineVersion, String solutionContract, String writingBible, String termRegistry,
            String commitmentRegistry, String promptVersion, java.time.LocalDateTime createdAt
    ) {
    }
}
