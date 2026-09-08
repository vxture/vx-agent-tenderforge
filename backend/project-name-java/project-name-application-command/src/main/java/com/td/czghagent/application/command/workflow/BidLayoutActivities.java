// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "BidLayout_")
public interface BidLayoutActivities {
    void render(String layoutJobId, String bidId, String ownerId);

    void fail(String layoutJobId, String bidId, String errorMessage);
}
