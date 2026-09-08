// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.port.TenderAiGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidSolutionContractRendererTest {
    @Test
    void rendersStrategyAsABoundedSharedContract() {
        String contract = BidSolutionContractRenderer.render(new TenderAiGateway.BidStrategy(
                "信息化系统", "以可验证的工程闭环响应评分要求",
                List.of("分层解耦", "全过程可追溯"),
                List.of(new TenderAiGateway.TechnicalTheme(
                        "数据治理", "建立统一数据口径", "采集、校验、治理、服务",
                        List.of("数据接入", "质量规则"), List.of("权限控制"), List.of("质量报告"))),
                List.of(new TenderAiGateway.ScoringResponseStrategy(
                        "数据治理能力", "确认方案可实施且可验收",
                        List.of("架构", "流程"), List.of("设计说明", "验收记录"), 1)),
                List.of("接口口径一致"), List.of("未提供部署规模时采用可扩展设计"),
                List.of("不得虚构案例", "x".repeat(20_000))));

        assertThat(contract)
                .contains("# 技术主题", "数据治理", "# 评分响应策略", "SP-001")
                .hasSizeLessThanOrEqualTo(16_000);
    }
}
