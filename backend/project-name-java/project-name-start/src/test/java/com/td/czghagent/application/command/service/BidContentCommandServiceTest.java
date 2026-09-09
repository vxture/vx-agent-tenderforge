// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
package com.td.czghagent.application.command.service;

import com.td.czghagent.domain.model.AuditEvent;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.ProductIdentity;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidProductionState;
import com.td.czghagent.domain.model.OperationContext;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.UsageEvent;
import com.td.czghagent.domain.model.UsageMetric;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.domain.port.BidGenerationOrchestrator;
import com.td.czghagent.domain.port.FileStorage;
import com.td.czghagent.domain.port.UsageRecorder;
import com.td.czghagent.domain.repository.BidProductionRepository;
import com.td.czghagent.domain.repository.BidRepository;
import com.td.czghagent.domain.repository.AuditRepository;
import com.td.czghagent.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(fixture.capturedAudit())
                .isEqualTo(new AuditEvent(
                        "owner-1", ProductIdentity.PRODUCT_CODE, "BID_CONTENT_PAUSE",
                        "BID", "bid-1", AuditEvent.SUCCESS,
                        "暂停正文生成并保留已完成内容", null,
                        TENANT.orgId(), TENANT.workspaceId(), "trace-1", "127.0.0.1"));
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
        assertThat(fixture.capturedAudit())
                .isEqualTo(new AuditEvent(
                        "owner-1", ProductIdentity.PRODUCT_CODE, "BID_CONTENT_RESUME",
                        "BID", "bid-1", AuditEvent.SUCCESS,
                        "继续正文任务的未完成部分", null,
                        TENANT.orgId(), TENANT.workspaceId(), "trace-1", "127.0.0.1"));
    }

    /** 归属人的过渡租户轴；接通平台身份后这里换成真实 workspace，断言不用动。 */
    private static final TenantScope TENANT = TenantScope.local("owner-1");

    // ── C3 计量：一个业务单元一笔账 ──────────────────────────────────────────

    /**
     * 一次生成<strong>任务</strong>计一笔，不是一次请求计一笔。
     *
     * <p>幂等键取任务 id，所以用户连点、前端重试、网关重放，账上都只有一笔。
     * 用随机 UUID 做键的话这几种情况会各记一次——而它们在日志里长得
     * 和三次真实生成一模一样。
     */
    @Test
    void metersOneChargePerGenerationTaskNotPerClick() {
        MeteringFixture fixture = new MeteringFixture(null);
        when(fixture.generationService.createTask(fixture.workspace))
                .thenReturn(new BidGenerationService.GenerationLaunch(
                        "task-42", "snapshot-1", "hash-1"));
        when(fixture.generationOrchestrator.start(
                "task-42", "bid-1", "owner-1", "snapshot-1", "hash-1", 0))
                .thenReturn("run-1");

        fixture.service.startGeneration("bid-1", fixture.context);

        assertThat(fixture.metered).singleElement().satisfies(event -> {
            assertThat(event.metric()).isEqualTo(UsageMetric.BID_GENERATIONS);
            assertThat(event.idempotencyKey())
                    .as("键取任务 id——同一个任务不管被报几次都是一笔")
                    .isEqualTo("tenderforge.bid.generations:task-42");
            assertThat(event.workspaceId()).isEqualTo(TENANT.workspaceId());
            assertThat(event.endUserId()).isEqualTo("owner-1");
        });
    }

    /**
     * 已经有任务在跑时再点一次<strong>不计费</strong>。
     *
     * <p>这条路径上 startGeneration 提前返回，什么也没创建。在方法入口计量
     * 就会在这里多收一笔——而用户看到的只是同一个进度条。
     */
    @Test
    void chargesNothingWhenAGenerationIsAlreadyRunning() {
        MeteringFixture fixture = new MeteringFixture(new BidWorkspace.GenerationTask(
                "task-existing", "RUNNING", 10, 3, null, LocalDateTime.now(), null));

        fixture.service.startGeneration("bid-1", fixture.context);

        assertThat(fixture.metered).isEmpty();
    }

    /**
     * 任务启动失败<strong>不计费</strong>。
     *
     * <p>编排器起不来时任务会被标记失败并抛出 502。这里记一笔的表现是：
     * 用户看到「启动失败」，账单上多了一次生成。
     */
    @Test
    void chargesNothingWhenTheOrchestratorRefusesToStart() {
        MeteringFixture fixture = new MeteringFixture(null);
        when(fixture.generationService.createTask(fixture.workspace))
                .thenReturn(new BidGenerationService.GenerationLaunch(
                        "task-42", "snapshot-1", "hash-1"));
        when(fixture.generationOrchestrator.start(
                "task-42", "bid-1", "owner-1", "snapshot-1", "hash-1", 0))
                .thenThrow(new IllegalStateException("temporal is down"));

        assertThatThrownBy(() -> fixture.service.startGeneration("bid-1", fixture.context))
                .isInstanceOf(BusinessException.class);
        assertThat(fixture.metered).isEmpty();
    }

    /** 能走到生成的那一档状态：目录已冻结、章节已就位、没有在跑的任务。 */
    private static final class MeteringFixture {
        private final BidRepository bidRepository = mock(BidRepository.class);
        private final BidProductionRepository productionRepository =
                mock(BidProductionRepository.class);
        private final BidGenerationOrchestrator generationOrchestrator =
                mock(BidGenerationOrchestrator.class);
        private final AuditRepository auditRepository = mock(AuditRepository.class);
        private final BidCommandSupport support =
                new BidCommandSupport(bidRepository, auditRepository);
        private final BidGenerationService generationService = mock(BidGenerationService.class);
        private final List<UsageEvent> metered = new ArrayList<>();
        private final BidDocument bid = new BidDocument(
                "bid-1", "owner-1", TENANT, "TA-1", "SCORING_CRITERIA", "测试标书", 60,
                "OPEN", "CONTENT", "OUTLINE_READY", false, null,
                LocalDateTime.now(), LocalDateTime.now(), 1);
        private final BidProductionState production = new BidProductionState(
                "FROZEN", 1, "interpretation-hash", "FROZEN", 1, "outline-hash",
                "DRAFT", 0, null, null, List.of(), List.of(), List.of(), List.of(), null);
        private final BidWorkspace workspace;
        private final OperationContext context = new OperationContext(
                new CurrentUser("owner-1", "owner", "Owner", "PLANNER", null, TENANT),
                "trace-1", "127.0.0.1");
        private final BidContentCommandService service;

        private MeteringFixture(BidWorkspace.GenerationTask runningTask) {
            this.workspace = new BidWorkspace(
                    bid, null, List.of(), List.of(),
                    List.of(new BidWorkspace.Chapter(
                            "chapter-1", "node-1", "第一章", "", "PENDING",
                            LocalDateTime.now(), 1)),
                    null, runningTask, List.of(), List.of(), production);
            this.service = new BidContentCommandService(
                    bidRepository, mock(FileStorage.class), mock(BidAiExecutionService.class),
                    mock(BidDocumentExporter.class), productionRepository, generationOrchestrator,
                    generationService, support, metered::add);
            when(bidRepository.findBid("bid-1", "owner-1")).thenReturn(Optional.of(bid));
            when(bidRepository.loadWorkspace(bid)).thenReturn(workspace);
        }
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
                "bid-1", "owner-1", TENANT, "TA-1", "SCORING_CRITERIA", "测试标书", 60,
                "OPEN", "CONTENT", "GENERATING", false, null,
                LocalDateTime.now(), LocalDateTime.now(), 1);
        private final BidProductionState production = new BidProductionState(
                "FROZEN", 1, "interpretation-hash", "FROZEN", 1, "outline-hash",
                "DRAFT", 0, null, null, List.of(), List.of(), List.of(), List.of(), null);
        private final BidWorkspace workspace = new BidWorkspace(
                bid, null, List.of(), List.of(), List.of(), null, null,
                List.of(), List.of(), production);
        private final OperationContext context = new OperationContext(
                new CurrentUser("owner-1", "owner", "Owner", "PLANNER", null, TENANT),
                "trace-1", "127.0.0.1");
        private final List<UsageEvent> metered = new ArrayList<>();
        private final UsageRecorder usageRecorder = metered::add;
        private final BidContentCommandService service = new BidContentCommandService(
                bidRepository, mock(FileStorage.class), mock(BidAiExecutionService.class),
                mock(BidDocumentExporter.class), productionRepository, generationOrchestrator,
                generationService, support, usageRecorder);

        /** 取出唯一一条写出去的审计事件；没写或写了多条都让断言失败而不是静默取第一条。 */
        private AuditEvent capturedAudit() {
            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).append(captor.capture());
            return captor.getValue();
        }

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
