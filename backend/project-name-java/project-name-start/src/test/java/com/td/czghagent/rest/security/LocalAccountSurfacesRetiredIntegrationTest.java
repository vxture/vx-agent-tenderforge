// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.security;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本地账号的管理面与资料编辑已经退役，平台身份只读地展示。
 *
 * <p>退役前的形态：{@code /api/account/profile|avatar} 按 {@code app_user.id} 找人，平台用户
 * 一律 404 {@code USER_NOT_FOUND}——账户页对每一个真实用户都是坏的；
 * {@code /api/admin/users*} 管理的是本地口令通道退役后再也登录不了的账号，
 * 在那里停用、改角色都不改变任何人能做什么，只制造「管过了」的错觉。
 *
 * <p>全部用例以<strong>工作空间 owner（即 ADMIN）</strong>的平台会话发起：
 * 不是管理员的话 {@code /api/admin/**} 会先被过滤器 403 挡下，
 * 「路由已不存在」这件事就测不到了。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        // 故意带尾斜杠：资料页地址必须规范化，不能拼出 //profile。
        "app.platform.console-url=https://console.example.test/",
        "app.storage.root=${java.io.tmpdir}/local-surfaces-${random.uuid}"
})
@AutoConfigureMockMvc
class LocalAccountSurfacesRetiredIntegrationTest extends PostgresBackedTest {

    private static final String PICTURE = "https://idp.example.test/avatars/owner.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RpSessionRepository rpSessions;

    @Test
    void localAccountManagementAndProfileEditingRoutesNoLongerExist() throws Exception {
        Session owner = signInAsWorkspaceOwner();
        String userId = UUID.randomUUID().toString();
        Map<String, MockHttpServletRequestBuilder> retired = new LinkedHashMap<>();
        retired.put("GET /api/admin/users", get("/api/admin/users"));
        retired.put("POST /api/admin/users", post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        retired.put("GET /api/admin/users/{id}", get("/api/admin/users/{id}", userId));
        retired.put("PATCH /api/admin/users/{id}", patch("/api/admin/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        retired.put("POST /api/admin/users/{id}/deactivate",
                post("/api/admin/users/{id}/deactivate", userId));
        retired.put("POST /api/admin/users/{id}/activate",
                post("/api/admin/users/{id}/activate", userId));
        retired.put("PATCH /api/account/profile", patch("/api/account/profile")
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"新名字\"}"));
        retired.put("GET /api/account/avatar", get("/api/account/avatar"));
        retired.put("POST /api/account/avatar", multipart("/api/account/avatar")
                .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3})));

        for (Map.Entry<String, MockHttpServletRequestBuilder> route : retired.entrySet()) {
            int status = mockMvc.perform(route.getValue().cookie(owner.cookie()))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(route.getKey() + " 应当已不存在").isEqualTo(404);
        }
    }

    /** 审计日志保留：删掉的是账号管理，不是管理面本身。 */
    @Test
    void auditLogsRemainAvailableToAdministrators() throws Exception {
        Session owner = signInAsWorkspaceOwner();

        mockMvc.perform(get("/api/admin/audit-logs").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    /**
     * {@code /api/auth/me} 给出平台身份，外加控制台资料页的地址。
     *
     * <p>原有字段必须原样保留（前端的会话恢复与角色判断都读它们），
     * 资料页地址只是在同一层多出一个键。
     */
    @Test
    void meExposesThePlatformIdentityAndTheConsoleProfileLink() throws Exception {
        Session owner = signInAsWorkspaceOwner();

        mockMvc.perform(get("/api/auth/me").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.subject()))
                .andExpect(jsonPath("$.username").value(owner.subject()))
                .andExpect(jsonPath("$.displayName").value("平台管理员"))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.avatarUrl").value(PICTURE))
                .andExpect(jsonPath("$.tenant.workspaceId").value(owner.workspaceId()))
                .andExpect(jsonPath("$.consoleProfileUrl").value("https://console.example.test/profile"));
    }

    private Session signInAsWorkspaceOwner() {
        String subject = UUID.randomUUID().toString();
        String workspaceId = UUID.randomUUID().toString();
        String cookieValue = SessionToken.generate();
        LocalDateTime now = LocalDateTime.now();
        rpSessions.insertSession(new RpSession(
                UUID.randomUUID().toString(), subject, "平台管理员", "owner@example.test", PICTURE,
                new TenantScope(UUID.randomUUID().toString(), workspaceId),
                "workspace:owner", "access-token", "refresh-token",
                now.plusHours(12), now.plusHours(12)), SessionToken.hash(cookieValue));
        return new Session(new Cookie(RpSessionCookie.PLAIN_NAME, cookieValue), subject, workspaceId);
    }

    private record Session(Cookie cookie, String subject, String workspaceId) {
    }
}
