// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-14
package com.td.czghagent.application.command.workflow;

import com.td.czghagent.domain.exception.BusinessException;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidOutlineActivitiesImplTest {
    @Test
    void retriesRecoverableAiFailures() {
        assertFalse(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("AI_OUTPUT_INVALID", "truncated", 502)));
        assertFalse(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("AI_PROVIDER_ERROR", "provider error", 502)));
        assertFalse(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("AI_PROVIDER_UNAVAILABLE", "unavailable", 502)));
        assertFalse(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("AI_GATEWAY_TIMEOUT", "gateway timeout", 504)));
        assertFalse(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("AI_MODEL_TIMEOUT", "model timeout", 504)));
    }

    @Test
    void rejectsBusinessAndConfigurationFailuresWithoutRetry() {
        assertTrue(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("BID_CRITERIA_REQUIRED", "missing", 400)));
        assertTrue(BidOutlineActivitiesImpl.isNonRetryable(
                new BusinessException("AI_PROVIDER_NOT_CONFIGURED", "missing key", 503)));
    }

    @Test
    void preservesTheSafeApplicationMessageForTheFailedTask() {
        ApplicationFailure failure = ApplicationFailure.newFailure(
                "目录策略模型执行超时（已等待约540秒），请稍后重试",
                "AI_MODEL_TIMEOUT");

        assertEquals(
                "目录策略模型执行超时（已等待约540秒），请稍后重试",
                BidOutlineWorkflowImpl.failureMessage(failure));
    }
}
