// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.application.command.cmd;

public record UpdateManagedUserCommand(
        String displayName,
        String roleCode,
        boolean enabled,
        String password,
        long revision
) {
}
