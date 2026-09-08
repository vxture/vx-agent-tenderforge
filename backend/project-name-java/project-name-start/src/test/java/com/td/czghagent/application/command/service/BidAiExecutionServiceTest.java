// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.exception.AiGatewayException;
import com.td.czghagent.domain.port.TenderAiGateway;
import com.td.czghagent.domain.repository.BidProductionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidAiExecutionServiceTest {
    @Test
    void normalizesConfiguredProviderNamesForAudit() {
        assertThat(BidAiExecutionService.normalizeProvider("deepseek"))
                .isEqualTo("DIRECT_DEEPSEEK");
        assertThat(BidAiExecutionService.normalizeProvider("direct_deepseek"))
                .isEqualTo("DIRECT_DEEPSEEK");
        assertThat(BidAiExecutionService.normalizeProvider("dashscope_deepseek"))
                .isEqualTo("DASHSCOPE_DEEPSEEK");
        assertThat(BidAiExecutionService.normalizeProvider("bailian_deepseek"))
                .isEqualTo("DASHSCOPE_DEEPSEEK");
        assertThat(BidAiExecutionService.normalizeProvider("qwen")).isEqualTo("DIRECT_QWEN");
        assertThat(BidAiExecutionService.normalizeProvider("local")).isEqualTo("UNKNOWN");
        assertThat(BidAiExecutionService.normalizeProvider("auto")).isEqualTo("UNKNOWN");
        assertThat(BidAiExecutionService.normalizeProvider("direct_qwen")).isEqualTo("DIRECT_QWEN");
        assertThat(BidAiExecutionService.normalizeProvider("other")).isEqualTo("UNKNOWN");
    }

    @Test
    void inputFingerprintExcludesTransientRequestIdentifiers() {
        AiInputFingerprint fingerprint = new AiInputFingerprint();
        FingerprintInput first = new FingerprintInput(
                "request-a", "trace-a", "技术标", List.of("项目概述"));
        FingerprintInput second = new FingerprintInput(
                "request-b", "trace-b", "技术标", List.of("项目概述"));

        assertThat(fingerprint.hash(first, "interpretation-v2"))
                .isEqualTo(fingerprint.hash(second, "interpretation-v2"));
        assertThat(fingerprint.hash(first, "interpretation-v2"))
                .isNotEqualTo(fingerprint.hash(
                        new FingerprintInput("request-a", "trace-a", "另一项目", List.of()),
                        "interpretation-v2"));
    }

    @Test
    void persistsUsageReportedWithStructuredOutputFailure() {
        TenderAiGateway gateway = mock(TenderAiGateway.class);
        BidProductionRepository repository = mock(BidProductionRepository.class);
        when(repository.beginAiRun(any())).thenReturn(
                new BidProductionRepository.AiRunHandle("run-1", "attempt-1", "RUNNING", 1));
        AiGatewayException failure = new AiGatewayException(
                "AI_OUTPUT_INVALID", "结构校验失败", 502, "missing field", "length",
                4000, "response-hash", 2, 1200L, 600L, 300L, 500L,
                "project_overview_extraction", 1000L);
        when(gateway.extractProjectOverview(any())).thenThrow(failure);
        BidAiExecutionService service = new BidAiExecutionService(
                gateway, repository, "DASHSCOPE_DEEPSEEK",
                "deepseek-v4-flash", "deepseek-v4-pro");
        TenderAiGateway.InterpretationRequest request = new TenderAiGateway.InterpretationRequest(
                "request-1", "document-1", "测试标书", "BLIND", List.of());

        assertThatThrownBy(() -> service.extractProjectOverview("bid-1", request))
                .isSameAs(failure);

        verify(repository).failAiRun(
                eq("run-1"), eq("attempt-1"), anyLong(),
                eq(1200L), eq(600L), eq(300L), eq(500L),
                eq("AI_OUTPUT_INVALID"), eq("结构校验失败"), any(),
                eq("length"), eq(4000), eq("response-hash"), eq(2));
    }

    private record FingerprintInput(
            String requestId, String traceId, String title, List<String> segments
    ) {
    }
}
