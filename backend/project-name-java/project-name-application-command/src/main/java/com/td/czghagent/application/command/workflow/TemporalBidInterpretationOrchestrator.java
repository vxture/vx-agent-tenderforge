// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.port.BidInterpretationOrchestrator;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true")
public class TemporalBidInterpretationOrchestrator implements BidInterpretationOrchestrator {
    public static final String TASK_QUEUE = "tenderagent-bid-interpretation";
    private final WorkflowClient workflowClient;

    public TemporalBidInterpretationOrchestrator(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public String start(String jobId, String sourceId, String bidId, String ownerId,
                        String traceId, String ipAddress) {
        BidInterpretationWorkflow workflow = workflowClient.newWorkflowStub(
                BidInterpretationWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("bid-interpretation-" + jobId).build());
        WorkflowClient.start(
                workflow::interpret, jobId, sourceId, bidId, ownerId, traceId, ipAddress);
        return WorkflowStub.fromTyped(workflow).getExecution().getRunId();
    }
}
