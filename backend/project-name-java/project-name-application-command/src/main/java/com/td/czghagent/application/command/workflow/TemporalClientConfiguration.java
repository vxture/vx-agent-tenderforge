// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
package com.td.czghagent.application.command.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.temporal.enabled", havingValue = "true")
public class TemporalClientConfiguration {
    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs temporalService(
            @Value("${app.temporal.address:127.0.0.1:7233}") String address
    ) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(address).build());
    }

    @Bean
    public WorkflowClient temporalClient(WorkflowServiceStubs service) {
        return WorkflowClient.newInstance(service);
    }
}
