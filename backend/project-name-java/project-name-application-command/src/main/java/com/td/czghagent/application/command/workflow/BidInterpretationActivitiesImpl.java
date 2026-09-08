// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidInterpretationProcessor;
import org.springframework.stereotype.Component;

@Component
public class BidInterpretationActivitiesImpl implements BidInterpretationActivities {
    private final BidInterpretationProcessor processor;

    public BidInterpretationActivitiesImpl(BidInterpretationProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void parseDocument(String sourceId, String bidId, String ownerId,
                              String traceId, String ipAddress) {
        processor.parseDocument(sourceId, bidId, ownerId, traceId, ipAddress);
    }

    @Override
    public void generateProjectOverview(String sourceId, String bidId, String ownerId,
                                        String traceId, String ipAddress) {
        processor.generateProjectOverview(sourceId, bidId, ownerId, traceId, ipAddress);
    }

    @Override
    public void generateTechnicalScoring(String sourceId, String bidId, String ownerId,
                                         String traceId, String ipAddress) {
        processor.generateTechnicalScoring(sourceId, bidId, ownerId, traceId, ipAddress);
    }

    @Override
    public void complete(String sourceId, String bidId, String ownerId,
                         String traceId, String ipAddress) {
        processor.complete(sourceId, bidId, ownerId, traceId, ipAddress);
    }

    @Override
    public void fail(String sourceId, String bidId, String ownerId,
                     String traceId, String ipAddress, String errorMessage) {
        processor.fail(sourceId, bidId, ownerId, traceId, ipAddress, errorMessage);
    }
}
