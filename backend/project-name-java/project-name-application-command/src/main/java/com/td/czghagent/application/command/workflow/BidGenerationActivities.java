// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.model.BidGenerationPlan;
import io.temporal.activity.ActivityInterface;

import java.util.List;

@ActivityInterface(namePrefix = "BidGeneration_")
public interface BidGenerationActivities {
    List<String> prepare(String taskId, String bidId, String ownerId,
                         String snapshotId, String snapshotHash);

    BidGenerationPlan preparePlan(String taskId, String bidId, String ownerId,
                                  String snapshotId, String snapshotHash);

    void generateUnit(String taskId, String bidId, String ownerId,
                      String snapshotId, String snapshotHash, String unitId);

    void complete(String taskId, String bidId, String ownerId,
                  String snapshotId, String snapshotHash);

    void fail(String taskId, String bidId, String errorMessage);
}
