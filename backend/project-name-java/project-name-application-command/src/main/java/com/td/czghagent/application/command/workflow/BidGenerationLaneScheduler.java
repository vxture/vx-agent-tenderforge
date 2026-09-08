package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.model.BidGenerationPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 确定性地将一级章节 lane 分配到有限数量的 Temporal workflow runner。
 */
final class BidGenerationLaneScheduler {
    private BidGenerationLaneScheduler() {
    }

    static List<List<BidGenerationPlan.Lane>> schedule(BidGenerationPlan plan) {
        if (plan.lanes().isEmpty()) {
            return List.of();
        }
        int runnerCount = Math.min(plan.maxConcurrency(), plan.lanes().size());
        List<List<BidGenerationPlan.Lane>> queues = new ArrayList<>();
        List<Integer> loads = new ArrayList<>();
        for (int index = 0; index < runnerCount; index++) {
            queues.add(new ArrayList<>());
            loads.add(0);
        }
        List<BidGenerationPlan.Lane> largestFirst = plan.lanes().stream()
                .sorted(Comparator.comparingInt(BidGenerationPlan.Lane::estimatedCharacters)
                        .reversed()
                        .thenComparing(BidGenerationPlan.Lane::rootOutlineId))
                .toList();
        for (BidGenerationPlan.Lane lane : largestFirst) {
            int target = 0;
            for (int index = 1; index < runnerCount; index++) {
                if (loads.get(index) < loads.get(target)) {
                    target = index;
                }
            }
            queues.get(target).add(lane);
            loads.set(target, Math.addExact(loads.get(target), lane.estimatedCharacters()));
        }
        return queues.stream().map(List::copyOf).toList();
    }
}
