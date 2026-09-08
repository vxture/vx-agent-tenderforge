// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.TenderAiGateway;

import java.util.List;

/** Converts the validated outline strategy into a compact immutable writing contract. */
final class BidSolutionContractRenderer {
    private static final int MAX_CHARACTERS = 16_000;

    private BidSolutionContractRenderer() {
    }

    static String render(TenderAiGateway.BidStrategy strategy) {
        StringBuilder value = new StringBuilder();
        section(value, "项目类型", List.of(strategy.projectArchetype()));
        section(value, "方案定位", List.of(strategy.solutionPositioning()));
        section(value, "设计原则", strategy.designPrinciples());
        themes(value, strategy.technicalThemes());
        scoring(value, strategy.scoringResponses());
        section(value, "跨章节约束", strategy.crossCuttingConstraints());
        section(value, "保守假设", strategy.assumptions());
        section(value, "禁止声明", strategy.prohibitedClaims());
        return limit(value.toString());
    }

    static String fallback(BidWorkspace workspace) {
        StringBuilder value = new StringBuilder("# 解决方案架构契约\n");
        value.append("方案范围：").append(workspace.bid().title()).append("的技术投标响应。\n");
        value.append("目录主线：");
        workspace.outline().stream().filter(node -> node.level() <= 2)
                .sorted(java.util.Comparator.comparingInt(BidWorkspace.OutlineNode::sortOrder))
                .forEach(node -> value.append(node.title()).append('；'));
        value.append("\n全局约束：保持冻结事实、评分要求、术语和承诺一致，不编造投标人事实。\n");
        return limit(value.toString());
    }

    private static void themes(StringBuilder target, List<TenderAiGateway.TechnicalTheme> themes) {
        target.append("# 技术主题\n");
        for (TenderAiGateway.TechnicalTheme item : safe(themes)) {
            target.append("- ").append(item.title()).append("：")
                    .append(item.objective()).append("；路线=").append(item.approach())
                    .append("；组件=").append(String.join("、", safe(item.components())))
                    .append("；控制=").append(String.join("、", safe(item.controls())))
                    .append("；验证=").append(String.join("、", safe(item.verification()))).append('\n');
        }
    }

    private static void scoring(
            StringBuilder target, List<TenderAiGateway.ScoringResponseStrategy> responses) {
        target.append("# 评分响应策略\n");
        int index = 1;
        for (TenderAiGateway.ScoringResponseStrategy item : safe(responses)) {
            target.append(String.format("- SP-%03d：", index++)).append(item.requirement())
                    .append("；评审意图=").append(item.evaluatorIntent())
                    .append("；响应要素=").append(String.join("、", safe(item.responseElements())))
                    .append("；证据计划=").append(String.join("、", safe(item.evidencePlan())))
                    .append("；优先级=").append(item.priority()).append('\n');
        }
    }

    private static void section(StringBuilder target, String title, List<String> values) {
        target.append("# ").append(title).append('\n');
        safe(values).stream().filter(item -> item != null && !item.isBlank())
                .forEach(item -> target.append("- ").append(item).append('\n'));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String limit(String value) {
        return value.substring(0, Math.min(MAX_CHARACTERS, value.length()));
    }
}
