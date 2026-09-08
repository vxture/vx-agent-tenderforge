// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidOutlineGenerationProcessor;
import com.td.czghagent.domain.exception.BusinessException;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

@Component
public class BidOutlineActivitiesImpl implements BidOutlineActivities {
    private final BidOutlineGenerationProcessor processor;

    public BidOutlineActivitiesImpl(BidOutlineGenerationProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void process(String taskId, String bidId, String ownerId,
                        String traceId, String ipAddress) {
        try {
            processor.process(taskId, bidId, ownerId, traceId, ipAddress);
        } catch (BusinessException exception) {
            throw ApplicationFailure.newNonRetryableFailure(
                    exception.getMessage(), exception.getErrorCode());
        }
    }

    @Override
    public void fail(String taskId, String bidId, String errorMessage) {
        processor.fail(taskId, bidId, errorMessage);
    }
}
