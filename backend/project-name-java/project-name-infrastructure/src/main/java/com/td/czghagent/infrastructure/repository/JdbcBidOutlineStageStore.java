// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.port.BidOutlineStageStore;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcBidOutlineStageStore implements BidOutlineStageStore {
    private static final String STRATEGY = "STRATEGY";
    private static final String SKELETON = "SKELETON";
    private static final String EXPANSION = "EXPANSION";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcBidOutlineStageStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TenderAiGateway.BidStrategy> findLatestStrategy(String taskId) {
        return jdbcTemplate.query("""
                SELECT output_payload FROM bid_outline_stage_result
                WHERE task_id = ? AND stage_type = 'STRATEGY' AND batch_index = 0
                  AND status = 'SUCCEEDED'
                ORDER BY finished_at DESC LIMIT 1
                """, (resultSet, row) -> decode(
                resultSet.getString("output_payload"), TenderAiGateway.BidStrategy.class),
                taskId).stream().findFirst();
    }

    @Override
    public Optional<TenderAiGateway.BidStrategy> findStrategy(
            String taskId, String inputHash) {
        return find(taskId, STRATEGY, 0, inputHash, TenderAiGateway.BidStrategy.class);
    }

    @Override
    public Optional<TenderAiGateway.OutlineSkeletonPlan> findSkeleton(
            String taskId, String inputHash) {
        return find(taskId, SKELETON, 0, inputHash, TenderAiGateway.OutlineSkeletonPlan.class);
    }

    @Override
    public Optional<TenderAiGateway.OutlineExpansion> findExpansion(
            String taskId, int batchIndex, String inputHash) {
        return find(taskId, EXPANSION, batchIndex, inputHash,
                TenderAiGateway.OutlineExpansion.class);
    }

    @Override
    public void begin(String taskId, String stage, int batchIndex,
                      String inputHash, String modelName) {
        int updated = jdbcTemplate.update("""
                UPDATE bid_outline_stage_result
                SET status = 'RUNNING', input_hash = ?, output_payload = NULL,
                    output_hash = NULL, model_name = ?, duration_ms = NULL,
                    attempt_count = attempt_count + 1, error_code = NULL,
                    error_message = NULL, started_at = CURRENT_TIMESTAMP, finished_at = NULL
                WHERE task_id = ? AND stage_type = ? AND batch_index = ?
                """, inputHash, modelName, taskId, stage, batchIndex);
        if (updated == 0) {
            insert(taskId, stage, batchIndex, inputHash, modelName);
        }
    }

    @Override
    public void completeStrategy(
            String taskId, String inputHash, TenderAiGateway.BidStrategy strategy,
            String outputHash, long durationMillis) {
        complete(taskId, STRATEGY, 0, inputHash, strategy, outputHash, durationMillis);
    }

    @Override
    public void completeSkeleton(
            String taskId, String inputHash, TenderAiGateway.OutlineSkeletonPlan skeleton,
            String outputHash, long durationMillis) {
        complete(taskId, SKELETON, 0, inputHash, skeleton, outputHash, durationMillis);
    }

    @Override
    public void completeExpansion(
            String taskId, int batchIndex, String inputHash,
            TenderAiGateway.OutlineExpansion expansion,
            String outputHash, long durationMillis) {
        complete(taskId, EXPANSION, batchIndex, inputHash,
                expansion, outputHash, durationMillis);
    }

    @Override
    public void fail(
            String taskId, String stage, int batchIndex, String inputHash,
            String errorCode, String errorMessage, long durationMillis) {
        jdbcTemplate.update("""
                UPDATE bid_outline_stage_result
                SET status = 'FAILED', duration_ms = ?, error_code = ?, error_message = ?,
                    finished_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND stage_type = ? AND batch_index = ? AND input_hash = ?
                """, durationMillis, errorCode, limit(errorMessage),
                taskId, stage, batchIndex, inputHash);
    }

    private <T> Optional<T> find(
            String taskId, String stage, int batchIndex, String inputHash, Class<T> type) {
        return jdbcTemplate.query("""
                SELECT output_payload FROM bid_outline_stage_result
                WHERE task_id = ? AND stage_type = ? AND batch_index = ?
                  AND status = 'SUCCEEDED' AND input_hash = ?
                """, (resultSet, row) -> decode(resultSet.getString("output_payload"), type),
                taskId, stage, batchIndex, inputHash).stream().findFirst();
    }

    private void insert(
            String taskId, String stage, int batchIndex,
            String inputHash, String modelName) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO bid_outline_stage_result(
                        id, task_id, stage_type, batch_index, status, input_hash, model_name
                    ) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
                    """, UUID.randomUUID().toString(), taskId, stage, batchIndex,
                    inputHash, modelName);
        } catch (DuplicateKeyException exception) {
            begin(taskId, stage, batchIndex, inputHash, modelName);
        }
    }

    private void complete(
            String taskId, String stage, int batchIndex, String inputHash,
            Object output, String outputHash, long durationMillis) {
        int changed = jdbcTemplate.update("""
                UPDATE bid_outline_stage_result
                SET status = 'SUCCEEDED', output_payload = ?, output_hash = ?, duration_ms = ?,
                    error_code = NULL, error_message = NULL, finished_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND stage_type = ? AND batch_index = ? AND input_hash = ?
                """, encode(output), outputHash, durationMillis,
                taskId, stage, batchIndex, inputHash);
        if (changed != 1) {
            throw new IllegalStateException("Outline stage stopped before its result was committed");
        }
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outline stage result cannot be serialized", exception);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored outline stage result is invalid", exception);
        }
    }

    private String limit(String value) {
        String safe = value == null || value.isBlank() ? "目录阶段执行失败" : value;
        return safe.substring(0, Math.min(1000, safe.length()));
    }
}
