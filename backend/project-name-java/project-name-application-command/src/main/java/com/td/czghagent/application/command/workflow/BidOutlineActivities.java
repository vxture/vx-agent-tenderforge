// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.workflow;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "BidOutline_")
public interface BidOutlineActivities {
    void process(String taskId, String bidId, String ownerId,
                 String traceId, String ipAddress);

    void fail(String taskId, String bidId, String errorMessage);
}
