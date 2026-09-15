// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
package com.td.czghagent.rest;

import com.td.czghagent.application.command.service.OidcLoginService;
import com.td.czghagent.domain.model.CurrentUser;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.rest.security.RequestIdentity;
import com.td.czghagent.rest.support.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/auth/me} 的形状：用户字段平铺，组织名、工作空间名与联系方式在同一层。
 *
 * <p>门禁页身份块读的就是这里。字段名写错、或者没平铺出来，前端拿到 undefined，
 * 界面退回 {@code usr_} 标识与兜底文案——而接口照样 200。
 */
class AuthMeTest {

    @Test
    void flattensTheSignedInUserTogetherWithTheOrganizationAndWorkspaceNames() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(mock(OidcLoginService.class), false, ""))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        CurrentUser user = new CurrentUser("usr_1", "usr_1", "王小明", "PLANNER", null,
                new TenantScope("org-1", "ws-1"), "华东设计院", "投标一部",
                "wang@example.com", "+8613800001234");

        mvc.perform(get("/api/auth/me").requestAttr(RequestIdentity.USER, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("王小明"))
                .andExpect(jsonPath("$.orgName").value("华东设计院"))
                .andExpect(jsonPath("$.workspaceName").value("投标一部"))
                .andExpect(jsonPath("$.email").value("wang@example.com"))
                .andExpect(jsonPath("$.phone").value("+8613800001234"))
                .andExpect(jsonPath("$.user").doesNotExist());
    }
}
