// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-09
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BidGenerationActivitiesImplTest {

    @Test
    void outputVarianceAndChapterQualityFailuresRemainRetryable() {
        assertThat(BidGenerationActivitiesImpl.isNonRetryable(
                new BusinessException("AI_OUTPUT_INVALID", "AI输出需要修复", 502)))
                .isFalse();
        assertThat(BidGenerationActivitiesImpl.isNonRetryable(
                new BusinessException("BID_CHAPTER_QUALITY_BLOCKED", "即时质检需要修复", 502)))
                .isFalse();
    }

    @Test
    void invalidInputAndMissingProviderRemainNonRetryable() {
        assertThat(BidGenerationActivitiesImpl.isNonRetryable(
                new BusinessException("BID_SNAPSHOT_INVALID", "输入快照无效", 409)))
                .isTrue();
        assertThat(BidGenerationActivitiesImpl.isNonRetryable(
                new BusinessException("AI_PROVIDER_NOT_CONFIGURED", "模型未配置", 502)))
                .isTrue();
    }
}
