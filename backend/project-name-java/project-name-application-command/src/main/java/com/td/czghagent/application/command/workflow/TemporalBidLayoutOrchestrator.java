// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.port.BidLayoutOrchestrator;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true")
public class TemporalBidLayoutOrchestrator implements BidLayoutOrchestrator {
    public static final String TASK_QUEUE = "tenderagent-bid-layout";
    private final WorkflowClient workflowClient;

    public TemporalBidLayoutOrchestrator(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public String start(String layoutJobId, String bidId, String ownerId) {
        BidLayoutWorkflow workflow = workflowClient.newWorkflowStub(
                BidLayoutWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("bid-layout-" + layoutJobId).build());
        WorkflowClient.start(workflow::layout, layoutJobId, bidId, ownerId);
        return WorkflowStub.fromTyped(workflow).getExecution().getRunId();
    }
}
