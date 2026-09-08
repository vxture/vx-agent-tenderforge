// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BidInterpretationWorkflow {
    @WorkflowMethod
    void interpret(String jobId, String sourceId, String bidId, String ownerId,
                   String traceId, String ipAddress);
}
