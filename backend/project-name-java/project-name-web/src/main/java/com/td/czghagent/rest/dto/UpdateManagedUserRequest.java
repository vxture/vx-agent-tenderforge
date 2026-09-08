// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateManagedUserRequest(
        @NotBlank(message = "请输入姓名") @Size(max = 64, message = "姓名不能超过64个字符")
        String displayName,
        @NotBlank(message = "请选择角色") String roleCode,
        @NotNull(message = "请选择账号状态") Boolean enabled,
        @Size(min = 8, max = 72, message = "新密码须为8至72位") String password,
        @PositiveOrZero(message = "数据版本不合法") long revision
) {
}
