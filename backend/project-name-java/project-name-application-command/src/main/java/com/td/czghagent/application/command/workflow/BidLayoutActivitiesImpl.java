// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidLayoutProcessor;
import org.springframework.stereotype.Component;

@Component
public class BidLayoutActivitiesImpl implements BidLayoutActivities {
    private final BidLayoutProcessor processor;

    public BidLayoutActivitiesImpl(BidLayoutProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void render(String layoutJobId, String bidId, String ownerId) {
        processor.process(layoutJobId, bidId, ownerId);
    }

    @Override
    public void fail(String layoutJobId, String bidId, String errorMessage) {
        processor.fail(layoutJobId, bidId, errorMessage);
    }
}
