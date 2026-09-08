// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent;

import com.td.czghagent.application.command.service.AuthCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class BootstrapDataInitializer implements CommandLineRunner {

    private final AuthCommandService authCommandService;
    private final boolean enabled;
    private final String plannerPassword;
    private final String adminPassword;

    public BootstrapDataInitializer(
            AuthCommandService authCommandService,
            @Value("${app.bootstrap.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.planner-password:}") String plannerPassword,
            @Value("${app.bootstrap.admin-password:}") String adminPassword
    ) {
        this.authCommandService = authCommandService;
        this.enabled = enabled;
        this.plannerPassword = plannerPassword;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        authCommandService.createSeedUser("planner", plannerPassword, "投标编制员", "PLANNER");
        authCommandService.createSeedUser("admin", adminPassword, "系统管理员", "ADMIN");
    }
}
