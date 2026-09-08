// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidLayoutProcessor;
import com.td.czghagent.domain.port.BidLayoutOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "false", matchIfMissing = true)
public class LocalBidLayoutOrchestrator implements BidLayoutOrchestrator, AutoCloseable {
    private final BidLayoutProcessor processor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LocalBidLayoutOrchestrator(BidLayoutProcessor processor) {
        this.processor = processor;
    }

    @Override
    public String start(String layoutJobId, String bidId, String ownerId) {
        executor.submit(() -> {
            try {
                processor.process(layoutJobId, bidId, ownerId);
            } catch (RuntimeException exception) {
                processor.fail(layoutJobId, bidId, exception.getMessage());
            }
        });
        return "local-layout-" + layoutJobId;
    }

    @Override
    public void close() {
        executor.close();
    }
}
