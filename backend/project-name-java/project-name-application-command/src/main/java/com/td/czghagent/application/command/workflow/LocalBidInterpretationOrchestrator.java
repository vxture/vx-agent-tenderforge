// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidInterpretationProcessor;
import com.td.czghagent.domain.port.BidInterpretationOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "false", matchIfMissing = true)
public class LocalBidInterpretationOrchestrator implements BidInterpretationOrchestrator, AutoCloseable {
    private final BidInterpretationProcessor processor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LocalBidInterpretationOrchestrator(BidInterpretationProcessor processor) {
        this.processor = processor;
    }

    @Override
    public String start(String jobId, String sourceId, String bidId, String ownerId,
                        String traceId, String ipAddress) {
        executor.submit(() -> run(sourceId, bidId, ownerId, traceId, ipAddress));
        return "local-interpretation-" + jobId;
    }

    private void run(String sourceId, String bidId, String ownerId,
                     String traceId, String ipAddress) {
        try {
            processor.parseDocument(sourceId, bidId, ownerId, traceId, ipAddress);
            processor.generateProjectOverview(sourceId, bidId, ownerId, traceId, ipAddress);
            processor.generateTechnicalScoring(sourceId, bidId, ownerId, traceId, ipAddress);
            processor.complete(sourceId, bidId, ownerId, traceId, ipAddress);
        } catch (RuntimeException exception) {
            processor.fail(sourceId, bidId, ownerId, traceId, ipAddress, exception.getMessage());
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}
