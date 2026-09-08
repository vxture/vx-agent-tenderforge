// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BidOutlineWorkflow {
    @WorkflowMethod
    void generate(String taskId, String bidId, String ownerId,
                  String traceId, String ipAddress);
}
