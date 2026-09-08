// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-11
package com.td.czghagent.application.command.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 将三级章节字符预算转换为具有明确写作职责的语义单元。
 */
final class BidSemanticUnitPlanner {
    private static final int COMFORTABLE_SINGLE_UNIT_CHARACTERS = 3_600;
    private static final int LONG_UNIT_CHARACTERS = 4_200;

    private BidSemanticUnitPlanner() {
    }

    static List<SemanticUnit> plan(int totalCharacters) {
        if (totalCharacters < 1) {
            throw new IllegalArgumentException("章节正文字符预算必须大于0");
        }
        List<String> roles = roles(unitCount(totalCharacters));
        List<Integer> weights = weights(roles.size());
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        List<SemanticUnit> result = new ArrayList<>();
        int allocated = 0;
        for (int index = 0; index < roles.size(); index++) {
            int budget = index == roles.size() - 1
                    ? totalCharacters - allocated
                    : Math.max(1, totalCharacters * weights.get(index) / totalWeight);
            allocated += budget;
            result.add(new SemanticUnit(index, roles.get(index), budget));
        }
        return List.copyOf(result);
    }

    private static int unitCount(int totalCharacters) {
        if (totalCharacters <= COMFORTABLE_SINGLE_UNIT_CHARACTERS) {
            return 1;
        }
        if (totalCharacters <= 7_600) {
            return 2;
        }
        if (totalCharacters <= 12_000) {
            return 3;
        }
        if (totalCharacters <= 17_000) {
            return 4;
        }
        return Math.max(5, (int) Math.ceil((double) totalCharacters / LONG_UNIT_CHARACTERS));
    }

    private static List<String> roles(int count) {
        return switch (count) {
            case 1 -> List.of("完整技术响应");
            case 2 -> List.of("技术设计与建设内容", "实施控制与交付验收");
            case 3 -> List.of("需求判断与响应边界", "技术设计与实施方法", "管控交付与验收");
            case 4 -> List.of("需求判断与总体设计", "技术架构与功能设计",
                    "实施方法与资源组织", "质量控制与交付验收");
            default -> extendedRoles(count);
        };
    }

    private static List<String> extendedRoles(int count) {
        List<String> result = new ArrayList<>();
        result.add("需求判断与响应边界");
        result.add("总体技术设计");
        for (int index = 0; index < count - 4; index++) {
            result.add("核心功能与实施细节（专题" + (index + 1) + "）");
        }
        result.add("运行控制与质量保障");
        result.add("交付成果与验收方法");
        return List.copyOf(result);
    }

    private static List<Integer> weights(int count) {
        return switch (count) {
            case 1 -> List.of(100);
            case 2 -> List.of(56, 44);
            case 3 -> List.of(22, 48, 30);
            case 4 -> List.of(18, 31, 29, 22);
            default -> extendedWeights(count);
        };
    }

    private static List<Integer> extendedWeights(int count) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(1);
        }
        return List.copyOf(result);
    }

    record SemanticUnit(int index, String role, int characterBudget) {
    }
}
