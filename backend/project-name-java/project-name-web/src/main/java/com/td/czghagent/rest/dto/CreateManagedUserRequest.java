// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateManagedUserRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "[A-Za-z0-9._-]{3,64}", message = "仅支持3至64位字母、数字、点、下划线或短横线")
        String username,
        @NotBlank(message = "请输入姓名") @Size(max = 64, message = "姓名不能超过64个字符")
        String displayName,
        @NotBlank(message = "请选择角色") String roleCode,
        @NotBlank(message = "请输入初始密码") @Size(min = 8, max = 72, message = "密码须为8至72位")
        String password
) {
}
