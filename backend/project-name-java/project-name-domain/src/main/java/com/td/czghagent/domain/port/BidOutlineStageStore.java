// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.domain.port;

import java.util.Optional;

/** Stores validated outline-stage results so retries can resume without repeating completed model calls. */
public interface BidOutlineStageStore {
    Optional<TenderAiGateway.BidStrategy> findLatestStrategy(String taskId);

    Optional<TenderAiGateway.BidStrategy> findStrategy(String taskId, String inputHash);

    Optional<TenderAiGateway.OutlineSkeletonPlan> findSkeleton(String taskId, String inputHash);

    Optional<TenderAiGateway.OutlineExpansion> findExpansion(
            String taskId, int batchIndex, String inputHash);

    void begin(String taskId, String stage, int batchIndex,
               String inputHash, String modelName);

    void completeStrategy(String taskId, String inputHash,
                          TenderAiGateway.BidStrategy strategy,
                          String outputHash, long durationMillis);

    void completeSkeleton(String taskId, String inputHash,
                          TenderAiGateway.OutlineSkeletonPlan skeleton,
                          String outputHash, long durationMillis);

    void completeExpansion(String taskId, int batchIndex, String inputHash,
                           TenderAiGateway.OutlineExpansion expansion,
                           String outputHash, long durationMillis);

    void fail(String taskId, String stage, int batchIndex, String inputHash,
              String errorCode, String errorMessage, long durationMillis);
}
