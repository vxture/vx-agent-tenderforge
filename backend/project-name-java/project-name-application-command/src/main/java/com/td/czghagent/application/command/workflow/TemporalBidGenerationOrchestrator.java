// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.port.BidGenerationOrchestrator;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true")
public class TemporalBidGenerationOrchestrator implements BidGenerationOrchestrator {
    public static final String TASK_QUEUE = "tenderagent-bid-generation";
    private final WorkflowClient workflowClient;

    public TemporalBidGenerationOrchestrator(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public String start(String taskId, String bidId, String ownerId,
                        String snapshotId, String snapshotHash, int retryCount) {
        BidContentGenerationWorkflow workflow = workflowClient.newWorkflowStub(
                BidContentGenerationWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE)
                        .setWorkflowId(workflowId(taskId, retryCount)).build());
        WorkflowClient.start(
                workflow::generate, taskId, bidId, ownerId, snapshotId, snapshotHash);
        return WorkflowStub.fromTyped(workflow).getExecution().getRunId();
    }

    @Override
    public void stop(String taskId, String workflowRunId, int retryCount) {
        WorkflowStub workflow = workflowClient.newUntypedWorkflowStub(
                workflowId(taskId, retryCount), Optional.ofNullable(workflowRunId), Optional.empty());
        workflow.terminate("正文生成任务已由用户暂停");
    }

    static String workflowId(String taskId, int retryCount) {
        return retryCount == 0
                ? "bid-generation-" + taskId
                : "bid-generation-" + taskId + "-resume-" + retryCount;
    }
}
