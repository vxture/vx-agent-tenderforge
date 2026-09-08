// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class BidOutlineWorkflowImpl implements BidOutlineWorkflow {
    private final BidOutlineActivities activities = Workflow.newActivityStub(
            BidOutlineActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(20))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0).setMaximumAttempts(2).build())
                    .build());

    @Override
    public void generate(String taskId, String bidId, String ownerId,
                         String traceId, String ipAddress) {
        try {
            activities.process(taskId, bidId, ownerId, traceId, ipAddress);
        } catch (RuntimeException exception) {
            activities.fail(taskId, bidId, rootMessage(exception));
            throw exception;
        }
    }

    private String rootMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "目录生成失败" : current.getMessage();
    }
}
