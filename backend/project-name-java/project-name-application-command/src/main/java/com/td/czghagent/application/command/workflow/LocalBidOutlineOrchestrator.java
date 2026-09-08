// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidOutlineGenerationProcessor;
import com.td.czghagent.domain.port.BidOutlineOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "false", matchIfMissing = true)
public class LocalBidOutlineOrchestrator implements BidOutlineOrchestrator, AutoCloseable {
    private final BidOutlineGenerationProcessor processor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LocalBidOutlineOrchestrator(BidOutlineGenerationProcessor processor) {
        this.processor = processor;
    }

    @Override
    public String start(String taskId, String bidId, String ownerId,
                        String traceId, String ipAddress) {
        executor.submit(() -> {
            try {
                processor.process(taskId, bidId, ownerId, traceId, ipAddress);
            } catch (RuntimeException exception) {
                processor.fail(taskId, bidId, exception.getMessage());
            }
        });
        return "local-outline-" + taskId;
    }

    @Override
    public void close() {
        executor.close();
    }
}
