// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BidContentGenerationWorkflow {
    @WorkflowMethod
    void generate(String taskId, String bidId, String ownerId,
                  String snapshotId, String snapshotHash);
}
