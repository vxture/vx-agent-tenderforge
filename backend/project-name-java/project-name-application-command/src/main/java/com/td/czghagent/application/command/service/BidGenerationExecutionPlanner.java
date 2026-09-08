package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidGenerationPlan;
import com.td.czghagent.domain.model.BidGenerationSnapshot;
import com.td.czghagent.domain.model.BidGenerationUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class BidGenerationExecutionPlanner {
    private final int maxConcurrency;

    BidGenerationExecutionPlanner(
            @Value("${app.tender.generation.max-concurrency:3}") int maxConcurrency
    ) {
        if (maxConcurrency < 1 || maxConcurrency > 5) {
            throw new IllegalArgumentException("正文生成并发数必须在1至5之间");
        }
        this.maxConcurrency = maxConcurrency;
    }

    BidGenerationPlan plan(
            BidGenerationSnapshot snapshot,
            List<BidGenerationUnit> units
    ) {
        Map<String, BidGenerationSnapshot.Outline> bySourceId = new HashMap<>();
        Map<String, BidGenerationSnapshot.Outline> byChapterId = new HashMap<>();
        snapshot.outline().forEach(node -> {
            bySourceId.put(node.sourceOutlineId(), node);
            if (node.chapterId() != null && !node.chapterId().isBlank()) {
                byChapterId.put(node.chapterId(), node);
            }
        });
        List<BidGenerationUnit> ordered = units.stream()
                .sorted(Comparator
                        .comparingInt((BidGenerationUnit unit) -> outlineOrder(unit, byChapterId))
                        .thenComparingInt(BidGenerationUnit::unitIndex)
                        .thenComparing(BidGenerationUnit::id))
                .toList();
        Map<String, LaneBuilder> lanes = new LinkedHashMap<>();
        for (BidGenerationUnit unit : ordered) {
            BidGenerationSnapshot.Outline node = byChapterId.get(unit.chapterId());
            if (node == null) {
                throw new IllegalStateException("生成单元对应的快照章节不存在");
            }
            BidGenerationSnapshot.Outline root = root(node, bySourceId);
            lanes.computeIfAbsent(root.sourceOutlineId(), ignored -> new LaneBuilder(root))
                    .add(unit);
        }
        return new BidGenerationPlan(
                lanes.values().stream().map(LaneBuilder::build).toList(),
                maxConcurrency);
    }

    private int outlineOrder(
            BidGenerationUnit unit,
            Map<String, BidGenerationSnapshot.Outline> byChapterId
    ) {
        BidGenerationSnapshot.Outline node = byChapterId.get(unit.chapterId());
        return node == null ? Integer.MAX_VALUE : node.sortOrder();
    }

    private BidGenerationSnapshot.Outline root(
            BidGenerationSnapshot.Outline node,
            Map<String, BidGenerationSnapshot.Outline> bySourceId
    ) {
        BidGenerationSnapshot.Outline current = node;
        Set<String> visited = new HashSet<>();
        while (current.level() > 1 && visited.add(current.sourceOutlineId())) {
            current = bySourceId.get(current.parentSourceOutlineId());
            if (current == null) {
                throw new IllegalStateException("快照目录父子关系不完整");
            }
        }
        if (current.level() != 1) {
            throw new IllegalStateException("生成章节无法归属到一级目录");
        }
        return current;
    }

    private static final class LaneBuilder {
        private final BidGenerationSnapshot.Outline root;
        private final List<String> unitIds = new ArrayList<>();
        private int estimatedCharacters;

        private LaneBuilder(BidGenerationSnapshot.Outline root) {
            this.root = root;
        }

        private LaneBuilder add(BidGenerationUnit unit) {
            unitIds.add(unit.id());
            estimatedCharacters = Math.addExact(estimatedCharacters, unit.wordBudget());
            return this;
        }

        private BidGenerationPlan.Lane build() {
            return new BidGenerationPlan.Lane(
                    root.sourceOutlineId(), root.title(), estimatedCharacters, unitIds);
        }
    }
}
