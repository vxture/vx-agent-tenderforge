package com.td.czghagent.domain.model;

import java.util.List;

/**
 * 不可变的正文执行计划。一个 lane 对应一个一级目录章节。
 */
public record BidGenerationPlan(List<Lane> lanes, int maxConcurrency) {
    public BidGenerationPlan {
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        if (maxConcurrency < 1 || maxConcurrency > 5) {
            throw new IllegalArgumentException("正文生成并发数必须在1至5之间");
        }
    }

    public record Lane(
            String rootOutlineId,
            String title,
            int estimatedCharacters,
            List<String> unitIds
    ) {
        public Lane {
            unitIds = unitIds == null ? List.of() : List.copyOf(unitIds);
            if (rootOutlineId == null || rootOutlineId.isBlank() || unitIds.isEmpty()) {
                throw new IllegalArgumentException("正文生成 lane 不完整");
            }
        }
    }
}
