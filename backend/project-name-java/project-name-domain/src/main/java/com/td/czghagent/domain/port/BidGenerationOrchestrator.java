// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.domain.port;

public interface BidGenerationOrchestrator {
    String start(String taskId, String bidId, String ownerId,
                 String snapshotId, String snapshotHash, int retryCount);

    void stop(String taskId, String workflowRunId, int retryCount);
}
