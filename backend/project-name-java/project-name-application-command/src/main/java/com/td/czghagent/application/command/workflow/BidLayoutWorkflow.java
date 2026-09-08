// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BidLayoutWorkflow {
    @WorkflowMethod
    void layout(String layoutJobId, String bidId, String ownerId);
}
