// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.security;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.PasswordHasher;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本地口令通道已经退役，并且<strong>回不来</strong>。
 *
 * <p>退役前的形态：登录页早已只剩平台登录，而 {@code POST /api/auth/login} 与 Bearer
 * 会话仍然活着。任何知道 {@code admin} 口令的人都能绕过平台身份——C2 权益与 C3 计量
 * 随之失效，而界面上没有任何异样。删掉了入口却留着接口的通道，比明着开着更难被发现。
 *
 * <p>前两条用例用的都是<strong>库里真实存在、按旧逻辑完全有效</strong>的凭据：启用的账号、
 * 正确的口令、未过期的会话。拿假凭据去验「被拒绝」是空转——旧代码同样会拒绝它。
 * 第三条是对照：平台会话照常放行，证明前两条的 401 来自通道被关，而不是测试环境本身坏了。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/local-channel-${random.uuid}"
})
@AutoConfigureMockMvc
class LocalPasswordChannelRetiredIntegrationTest extends PostgresBackedTest {

    private static final String PASSWORD = "Legacy@2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private RpSessionRepository rpSessions;

    private String username;
    private String userId;
    private String liveLegacyToken;

    /** 一个启用的本地管理员，带一张未过期的会话——正是生产库里残留的那种形状。 */
    @BeforeEach
    void seedALegacyAdminWithALiveSession() {
        username = "legacy-admin-" + UUID.randomUUID();
        userId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO app_user(id, username, password_hash, display_name, role_code, enabled)
                VALUES (?, ?, ?, ?, 'ADMIN', TRUE)
                """, userId, username, passwordHasher.hash(PASSWORD), "遗留管理员");
        liveLegacyToken = SessionToken.generate();
        jdbcTemplate.update("""
                INSERT INTO user_session(id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID().toString(), userId, SessionToken.hash(liveLegacyToken),
                LocalDateTime.now().plusHours(12));
    }

    @Test
    void aLiveLocalSessionNoLongerAuthenticates() throws Exception {
        // 先确认这张票按旧逻辑是有效的，否则下面的 401 什么也证明不了。
        assertThat(liveSessionsOf(userId)).isEqualTo(1);

        mockMvc.perform(get("/api/bids").header("Authorization", "Bearer " + liveLegacyToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }

    /**
     * 口令正确也进不来，并且没有签发新会话。
     *
     * <p>期望的是 401 而不是 404：把 {@code /api/auth/login} 加回过滤器放行名单，
     * 这里会变成 404（或者连接口一起加回时的 200）——两种都红。
     */
    @Test
    void passwordLoginIsGoneEvenWithCorrectCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(liveSessionsOf(userId)).isEqualTo(1);
    }

    @Test
    void aPlatformSessionStillAuthenticates() throws Exception {
        String cookieValue = SessionToken.generate();
        LocalDateTime now = LocalDateTime.now();
        String subject = UUID.randomUUID().toString();
        rpSessions.insertSession(new RpSession(
                UUID.randomUUID().toString(), subject, "平台用户", null, null,
                new TenantScope(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                "workspace:member", "access-token", "refresh-token",
                now.plusHours(12), now.plusHours(12)), SessionToken.hash(cookieValue));

        mockMvc.perform(get("/api/bids").cookie(new Cookie(RpSessionCookie.PLAIN_NAME, cookieValue)))
                .andExpect(status().isOk());
    }

    private Integer liveSessionsOf(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE user_id = ? AND expires_at > ?",
                Integer.class, id, LocalDateTime.now());
    }
}
