// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.application.command.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalBidGenerationOrchestratorTest {
    @Test
    void createsANewWorkflowIdForEveryResumeAttempt() {
        assertThat(TemporalBidGenerationOrchestrator.workflowId("task-1", 0))
                .isEqualTo("bid-generation-task-1");
        assertThat(TemporalBidGenerationOrchestrator.workflowId("task-1", 1))
                .isEqualTo("bid-generation-task-1-resume-1");
        assertThat(TemporalBidGenerationOrchestrator.workflowId("task-1", 3))
                .isEqualTo("bid-generation-task-1-resume-3");
    }
}
