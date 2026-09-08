// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.model.BidGenerationPlan;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BidContentGenerationWorkflowImpl implements BidContentGenerationWorkflow {
    private static final Set<String> RECOVERABLE_UNIT_FAILURE_TYPES = Set.of(
            "AI_OUTPUT_INVALID", "AI_PROVIDER_ERROR", "BID_CHAPTER_QUALITY_BLOCKED"
    );
    private final BidGenerationActivities activities = Workflow.newActivityStub(
            BidGenerationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(20))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(5))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofMinutes(2))
                            .setMaximumAttempts(3)
                            .build())
                    .build()
    );
    private final BidGenerationActivities finalizationActivities = Workflow.newActivityStub(
            BidGenerationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(2))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(15))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofMinutes(5))
                            .setMaximumAttempts(2)
                            .build())
                    .build()
    );
    private final BidGenerationActivities repairActivities = Workflow.newActivityStub(
            BidGenerationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(20))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                    .build()
    );

    @Override
    public void generate(String taskId, String bidId, String ownerId,
                         String snapshotId, String snapshotHash) {
        try {
            int version = Workflow.getVersion(
                    "bid-content-generation-lanes", Workflow.DEFAULT_VERSION, 1);
            if (version == Workflow.DEFAULT_VERSION) {
                generateSerially(taskId, bidId, ownerId, snapshotId, snapshotHash);
            } else {
                int resilienceVersion = Workflow.getVersion(
                        "bid-content-generation-resilient-units", Workflow.DEFAULT_VERSION, 1);
                if (resilienceVersion == Workflow.DEFAULT_VERSION) {
                    generateByLanes(taskId, bidId, ownerId, snapshotId, snapshotHash);
                } else {
                    generateByLanesResilient(taskId, bidId, ownerId, snapshotId, snapshotHash);
                }
            }
            finalizationActivities.complete(taskId, bidId, ownerId, snapshotId, snapshotHash);
        } catch (RuntimeException exception) {
            activities.fail(taskId, bidId, rootMessage(exception));
            throw exception;
        }
    }

    private void generateSerially(String taskId, String bidId, String ownerId,
                                  String snapshotId, String snapshotHash) {
        List<String> unitIds = activities.prepare(
                taskId, bidId, ownerId, snapshotId, snapshotHash);
        unitIds.forEach(unitId -> generateUnit(
                taskId, bidId, ownerId, snapshotId, snapshotHash, unitId));
    }

    private void generateByLanes(String taskId, String bidId, String ownerId,
                                 String snapshotId, String snapshotHash) {
        BidGenerationPlan plan = activities.preparePlan(
                taskId, bidId, ownerId, snapshotId, snapshotHash);
        List<Promise<Void>> runners = BidGenerationLaneScheduler.schedule(plan).stream()
                .map(queue -> Async.procedure(() -> queue.forEach(lane ->
                        lane.unitIds().forEach(unitId -> generateUnit(
                                taskId, bidId, ownerId, snapshotId, snapshotHash, unitId)))))
                .toList();
        Promise.allOf(runners).get();
    }

    private void generateByLanesResilient(
            String taskId, String bidId, String ownerId, String snapshotId, String snapshotHash
    ) {
        BidGenerationPlan plan = activities.preparePlan(
                taskId, bidId, ownerId, snapshotId, snapshotHash);
        List<List<BidGenerationPlan.Lane>> queues = BidGenerationLaneScheduler.schedule(plan);
        List<UnitFailure> failures = executeQueues(
                queues, Set.of(), activities, taskId, bidId, ownerId, snapshotId, snapshotHash);
        Set<String> repairable = failures.stream()
                .filter(failure -> RECOVERABLE_UNIT_FAILURE_TYPES.contains(failure.type()))
                .map(UnitFailure::unitId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<UnitFailure> unresolved = new ArrayList<>(failures.stream()
                .filter(failure -> !repairable.contains(failure.unitId())).toList());
        if (!repairable.isEmpty()) {
            unresolved.addAll(executeQueues(
                    queues, repairable, repairActivities,
                    taskId, bidId, ownerId, snapshotId, snapshotHash));
        }
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException(failureSummary(unresolved));
        }
    }

    private List<UnitFailure> executeQueues(
            List<List<BidGenerationPlan.Lane>> queues, Set<String> selectedUnitIds,
            BidGenerationActivities activityStub, String taskId, String bidId,
            String ownerId, String snapshotId, String snapshotHash
    ) {
        List<Promise<List<UnitFailure>>> runners = queues.stream()
                .map(queue -> Async.function(() -> executeQueue(
                        queue, selectedUnitIds, activityStub,
                        taskId, bidId, ownerId, snapshotId, snapshotHash)))
                .toList();
        Promise.allOf(runners).get();
        return runners.stream().flatMap(runner -> runner.get().stream()).toList();
    }

    private List<UnitFailure> executeQueue(
            List<BidGenerationPlan.Lane> queue, Set<String> selectedUnitIds,
            BidGenerationActivities activityStub, String taskId, String bidId,
            String ownerId, String snapshotId, String snapshotHash
    ) {
        List<UnitFailure> failures = new ArrayList<>();
        queue.forEach(lane -> lane.unitIds().forEach(unitId -> {
            if (!selectedUnitIds.isEmpty() && !selectedUnitIds.contains(unitId)) {
                return;
            }
            try {
                activityStub.generateUnit(taskId, bidId, ownerId, snapshotId, snapshotHash, unitId);
            } catch (RuntimeException exception) {
                String type = failureType(exception);
                if (!RECOVERABLE_UNIT_FAILURE_TYPES.contains(type)) {
                    throw exception;
                }
                failures.add(new UnitFailure(unitId, type, rootMessage(exception)));
            }
        }));
        return List.copyOf(failures);
    }

    private String failureType(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ApplicationFailure failure) {
                return failure.getType();
            }
            current = current.getCause();
        }
        return exception.getClass().getSimpleName();
    }

    private String failureSummary(List<UnitFailure> failures) {
        String details = failures.stream().limit(3)
                .map(failure -> failure.type() + "：" + failure.message())
                .collect(java.util.stream.Collectors.joining("；"));
        return "正文生成仍有" + failures.size() + "个单元未能自动修复：" + details;
    }

    private void generateUnit(String taskId, String bidId, String ownerId,
                              String snapshotId, String snapshotHash, String unitId) {
        activities.generateUnit(taskId, bidId, ownerId, snapshotId, snapshotHash, unitId);
    }

    private String rootMessage(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "正文生成失败" : current.getMessage();
    }

    private record UnitFailure(String unitId, String type, String message) {
    }
}
