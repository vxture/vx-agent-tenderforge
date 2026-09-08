// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.port.BidOutlineOrchestrator;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true")
public class TemporalBidOutlineOrchestrator implements BidOutlineOrchestrator {
    public static final String TASK_QUEUE = "tenderagent-bid-outline";
    private final WorkflowClient workflowClient;

    public TemporalBidOutlineOrchestrator(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public String start(String taskId, String bidId, String ownerId,
                        String traceId, String ipAddress) {
        BidOutlineWorkflow workflow = workflowClient.newWorkflowStub(
                BidOutlineWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("bid-outline-" + taskId).build());
        WorkflowClient.start(
                workflow::generate, taskId, bidId, ownerId, traceId, ipAddress);
        return WorkflowStub.fromTyped(workflow).getExecution().getRunId();
    }
}
