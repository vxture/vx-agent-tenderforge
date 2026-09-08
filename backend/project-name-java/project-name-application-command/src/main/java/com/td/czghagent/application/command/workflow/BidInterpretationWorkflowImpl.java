// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class BidInterpretationWorkflowImpl implements BidInterpretationWorkflow {
    private final BidInterpretationActivities activities = Workflow.newActivityStub(
            BidInterpretationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(20))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofMinutes(1))
                            .setMaximumAttempts(2)
                            .build())
                    .build());

    @Override
    public void interpret(String jobId, String sourceId, String bidId, String ownerId,
                          String traceId, String ipAddress) {
        try {
            activities.parseDocument(sourceId, bidId, ownerId, traceId, ipAddress);
            activities.generateProjectOverview(sourceId, bidId, ownerId, traceId, ipAddress);
            activities.generateTechnicalScoring(sourceId, bidId, ownerId, traceId, ipAddress);
            activities.complete(sourceId, bidId, ownerId, traceId, ipAddress);
        } catch (RuntimeException exception) {
            activities.fail(sourceId, bidId, ownerId, traceId, ipAddress, rootMessage(exception));
            throw exception;
        }
    }

    private String rootMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "招标文件解读失败" : current.getMessage();
    }
}
