// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class BidLayoutWorkflowImpl implements BidLayoutWorkflow {
    private final BidLayoutActivities activities = Workflow.newActivityStub(
            BidLayoutActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0).setMaximumAttempts(3).build())
                    .build());

    @Override
    public void layout(String layoutJobId, String bidId, String ownerId) {
        try {
            activities.render(layoutJobId, bidId, ownerId);
        } catch (RuntimeException exception) {
            activities.fail(layoutJobId, bidId, rootMessage(exception));
            throw exception;
        }
    }

    private String rootMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "标书排版失败" : current.getMessage();
    }
}
