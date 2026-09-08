// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "BidInterpretation_")
public interface BidInterpretationActivities {
    void parseDocument(String sourceId, String bidId, String ownerId,
                       String traceId, String ipAddress);

    void generateProjectOverview(String sourceId, String bidId, String ownerId,
                                 String traceId, String ipAddress);

    void generateTechnicalScoring(String sourceId, String bidId, String ownerId,
                                  String traceId, String ipAddress);

    void complete(String sourceId, String bidId, String ownerId,
                  String traceId, String ipAddress);

    void fail(String sourceId, String bidId, String ownerId,
              String traceId, String ipAddress, String errorMessage);
}
