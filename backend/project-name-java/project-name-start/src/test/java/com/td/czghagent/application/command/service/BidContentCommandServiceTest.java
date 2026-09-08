// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.BidGenerationOrchestrator;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import com.td.czghagent.domain.repository.AuditRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidContentCommandServiceTest {

    @Test
    void completedBidCanStartAnotherContentGenerationAfterOutlineRemainsFrozen() {
        assertThat(BidContentCommandService.canStartGeneration("COMPLETED")).isTrue();
        assertThat(BidContentCommandService.canStartGeneration("OUTLINE_READY")).isTrue();
        assertThat(BidContentCommandService.canStartGeneration("DRAFT")).isFalse();
        assertThat(BidContentCommandService.canStartGeneration("INTERPRETATION_READY")).isFalse();
    }

    @Test
    void pausePersistsStateBeforeStoppingTheCurrentWorkflow() {
        Fixture fixture = new Fixture();
        BidRepository.GenerationTaskExecution execution = fixture.execution("RUNNING", 0, true);
        when(fixture.bidRepository.pauseGenerationTask("bid-1")).thenReturn(execution);

        assertThat(fixture.service.pauseGeneration("bid-1", fixture.context))
                .isSameAs(fixture.workspace);

        verify(fixture.generationOrchestrator).stop("task-1", "run-1", 0);
        verify(fixture.auditRepository).append(
                "owner-1", "BID_CONTENT_PAUSE", "BID", "bid-1", "SUCCESS",
                "暂停正文生成并保留已完成内容", "trace-1", "127.0.0.1");
    }

    @Test
    void resumeStartsANewWorkflowExecutionFromTheExistingSnapshot() {
        Fixture fixture = new Fixture();
        BidRepository.GenerationTaskExecution execution = fixture.execution("PENDING", 2, true);
        when(fixture.bidRepository.resumeGenerationTask("bid-1")).thenReturn(execution);
        when(fixture.generationOrchestrator.start(
                "task-1", "bid-1", "owner-1", "snapshot-1", "hash-1", 2))
                .thenReturn("run-2");

        assertThat(fixture.service.resumeGeneration("bid-1", fixture.context))
                .isSameAs(fixture.workspace);

        verify(fixture.productionRepository).assignWorkflowRun("task-1", "run-2", "hash-1");
        verify(fixture.auditRepository).append(
                "owner-1", "BID_CONTENT_RESUME", "BID", "bid-1", "SUCCESS",
                "继续正文任务的未完成部分", "trace-1", "127.0.0.1");
    }

    private static final class Fixture {
        private final BidRepository bidRepository = mock(BidRepository.class);
        private final BidProductionRepository productionRepository =
                mock(BidProductionRepository.class);
        private final BidGenerationOrchestrator generationOrchestrator =
                mock(BidGenerationOrchestrator.class);
        private final AuditRepository auditRepository = mock(AuditRepository.class);
        private final BidCommandSupport support = new BidCommandSupport(
                bidRepository, auditRepository);
        private final BidGenerationService generationService = mock(BidGenerationService.class);
        private final BidDocument bid = new BidDocument(
                "bid-1", "owner-1", "TA-1", "SCORING_CRITERIA", "测试标书", 60,
                "OPEN", "CONTENT", "GENERATING", false, null,
                LocalDateTime.now(), LocalDateTime.now(), 1);
        private final BidProductionState production = new BidProductionState(
                "FROZEN", 1, "interpretation-hash", "FROZEN", 1, "outline-hash",
                "DRAFT", 0, null, null, List.of(), List.of(), List.of(), List.of(), null);
        private final BidWorkspace workspace = new BidWorkspace(
                bid, null, List.of(), List.of(), List.of(), null, null,
                List.of(), List.of(), production);
        private final OperationContext context = new OperationContext(
                new CurrentUser("owner-1", "owner", "Owner", "PLANNER", null),
                "trace-1", "127.0.0.1");
        private final BidContentCommandService service = new BidContentCommandService(
                bidRepository, mock(FileStorage.class), mock(BidAiExecutionService.class),
                mock(BidDocumentExporter.class), productionRepository, generationOrchestrator,
                generationService, support);

        private Fixture() {
            when(bidRepository.findBid("bid-1", "owner-1")).thenReturn(Optional.of(bid));
            when(bidRepository.loadWorkspace(bid)).thenReturn(workspace);
        }

        private BidRepository.GenerationTaskExecution execution(
                String status, int retryCount, boolean changed) {
            return new BidRepository.GenerationTaskExecution(
                    "task-1", "bid-1", status, "snapshot-1", "hash-1",
                    "run-1", retryCount, changed);
        }
    }
}
