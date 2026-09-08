// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.application.command.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        name = {"app.temporal.enabled", "app.temporal.worker-enabled"},
        havingValue = "true"
)
public class TemporalWorkerConfiguration {
    @Bean(destroyMethod = "shutdown")
    public WorkerFactory temporalWorkerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    public InitializingBean tenderGenerationWorker(
            WorkerFactory factory,
            BidInterpretationActivities interpretationActivities,
            BidOutlineActivities outlineActivities,
            BidGenerationActivities generationActivities,
            BidLayoutActivities layoutActivities,
            @Value("${app.tender.generation.max-concurrency:3}") int generationConcurrency
    ) {
        return () -> {
            Worker interpretationWorker = factory.newWorker(
                    TemporalBidInterpretationOrchestrator.TASK_QUEUE,
                    WorkerOptions.newBuilder()
                            .setMaxConcurrentActivityExecutionSize(2)
                            .setMaxTaskQueueActivitiesPerSecond(1.0)
                            .build());
            interpretationWorker.registerWorkflowImplementationTypes(
                    BidInterpretationWorkflowImpl.class);
            interpretationWorker.registerActivitiesImplementations(interpretationActivities);
            Worker outlineWorker = factory.newWorker(
                    TemporalBidOutlineOrchestrator.TASK_QUEUE,
                    WorkerOptions.newBuilder()
                            .setMaxConcurrentActivityExecutionSize(2)
                            .setMaxTaskQueueActivitiesPerSecond(1.0)
                            .build());
            outlineWorker.registerWorkflowImplementationTypes(BidOutlineWorkflowImpl.class);
            outlineWorker.registerActivitiesImplementations(outlineActivities);
            Worker generationWorker = factory.newWorker(
                    TemporalBidGenerationOrchestrator.TASK_QUEUE,
                    WorkerOptions.newBuilder()
                            .setMaxConcurrentActivityExecutionSize(generationConcurrency)
                            .setMaxTaskQueueActivitiesPerSecond(generationConcurrency)
                            .build());
            generationWorker.registerWorkflowImplementationTypes(BidContentGenerationWorkflowImpl.class);
            generationWorker.registerActivitiesImplementations(generationActivities);
            Worker layoutWorker = factory.newWorker(
                    TemporalBidLayoutOrchestrator.TASK_QUEUE,
                    WorkerOptions.newBuilder()
                            .setMaxConcurrentActivityExecutionSize(1)
                            .setMaxTaskQueueActivitiesPerSecond(0.25)
                            .build());
            layoutWorker.registerWorkflowImplementationTypes(BidLayoutWorkflowImpl.class);
            layoutWorker.registerActivitiesImplementations(layoutActivities);
            factory.start();
        };
    }
}
