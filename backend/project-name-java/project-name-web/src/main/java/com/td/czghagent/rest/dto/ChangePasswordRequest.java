// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
package com.td.czghagent.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 8, max = 64) String newPassword
) {
}

