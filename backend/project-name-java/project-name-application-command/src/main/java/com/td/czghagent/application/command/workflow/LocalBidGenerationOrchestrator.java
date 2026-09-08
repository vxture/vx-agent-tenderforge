// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.application.command.service.BidGenerationService;
import com.td.czghagent.domain.port.BidGenerationOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "false", matchIfMissing = true)
public class LocalBidGenerationOrchestrator implements BidGenerationOrchestrator, AutoCloseable {
    private final BidGenerationService generationService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Future<?>> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, String> activeRunIds = new ConcurrentHashMap<>();

    public LocalBidGenerationOrchestrator(BidGenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public String start(String taskId, String bidId, String ownerId,
                        String snapshotId, String snapshotHash, int retryCount) {
        String runId = "local-" + taskId + "-" + retryCount;
        activeRunIds.put(taskId, runId);
        Future<?> future = executor.submit(() -> {
            try {
                run(taskId, bidId, ownerId, snapshotId, snapshotHash);
            } finally {
                if (activeRunIds.remove(taskId, runId)) {
                    activeRuns.remove(taskId);
                }
            }
        });
        activeRuns.put(taskId, future);
        if (!runId.equals(activeRunIds.get(taskId))) {
            activeRuns.remove(taskId, future);
        }
        return runId;
    }

    @Override
    public void stop(String taskId, String workflowRunId, int retryCount) {
        Future<?> future = activeRuns.remove(taskId);
        activeRunIds.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void run(String taskId, String bidId, String ownerId,
                     String snapshotId, String snapshotHash) {
        try {
            List<String> units = generationService.prepare(
                    taskId, bidId, ownerId, snapshotId, snapshotHash);
            units.forEach(unitId -> generationService.generateUnit(
                    taskId, bidId, ownerId, snapshotId, snapshotHash, unitId));
            generationService.complete(
                    taskId, bidId, ownerId, snapshotId, snapshotHash);
        } catch (RuntimeException exception) {
            generationService.fail(taskId, bidId, exception.getMessage());
        }
    }

    @Override
    public void close() {
        activeRuns.values().forEach(future -> future.cancel(true));
        activeRuns.clear();
        activeRunIds.clear();
        executor.close();
    }
}
