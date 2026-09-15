// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest;

import com.td.czghagent.PostgresBackedTest;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import com.td.czghagent.infrastructure.platform.MockEntitlementResolver;
import com.td.czghagent.rest.security.RpSessionCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C2 权益的强制点在 HTTP 层真的成立：每个用户命令端点都先判定权益。
 *
 * <p>判定逻辑本身由 {@code EntitlementGuardTest} 验；这里验的是<strong>接缝</strong>——
 * 每个端点是不是经过了那道判定、判定是不是先于取数与状态校验。所以用一个不存在的标书 id：
 * 未订阅时必须拿到 403 {@code NOT_ENTITLED}，有订阅时必须<strong>越过</strong>判定、
 * 得到别的答案（404 之类）。前者证明被拦，后者证明拦的原因确实是权益，而不是 id 无效。
 *
 * <p>权益由替身解析器按 MOCK_TIER / MOCK_STATUS 的真实语义产生，逐个用例切换。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/entitlement-gate-${random.uuid}"
})
@AutoConfigureMockMvc
@Import(EntitlementEnforcementIntegrationTest.SwitchableEntitlements.class)
class EntitlementEnforcementIntegrationTest extends PostgresBackedTest {

    private static final String BID = UUID.randomUUID().toString();
    private static final String CHAPTER = UUID.randomUUID().toString();
    private static final String ASSET = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RpSessionRepository rpSessions;

    @Autowired
    private SwitchableEntitlementResolver entitlements;

    private Cookie session;

    @TestConfiguration(proxyBeanMethods = false)
    static class SwitchableEntitlements {
        @Bean
        @Primary
        SwitchableEntitlementResolver switchableEntitlementResolver() {
            return new SwitchableEntitlementResolver();
        }
    }

    /** 委托给真实的替身解析器，只是允许用例之间换一组 MOCK_TIER / MOCK_STATUS。 */
    static final class SwitchableEntitlementResolver implements EntitlementResolver {
        private volatile EntitlementResolver delegate = activePro();

        void use(String tier, String status) {
            delegate = new MockEntitlementResolver(tier, status, false);
        }

        void reset() {
            delegate = activePro();
        }

        private static EntitlementResolver activePro() {
            return new MockEntitlementResolver("pro", "active", false);
        }

        @Override
        public Entitlement resolve(String workspaceId) {
            return delegate.resolve(workspaceId);
        }

        @Override
        public void invalidate(String workspaceId) {
        }

        @Override
        public boolean isMock() {
            return true;
        }
    }

    @BeforeEach
    void signIn() {
        String cookieValue = SessionToken.generate();
        String subject = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        rpSessions.insertSession(new RpSession(
                UUID.randomUUID().toString(), subject, "权益门控用户", null, null,
                new TenantScope(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                "workspace:member", "access-token", "refresh-token",
                now.plusHours(12), now.plusHours(12)), SessionToken.hash(cookieValue));
        session = new Cookie(RpSessionCookie.PLAIN_NAME, cookieValue);
    }

    @AfterEach
    void resetEntitlements() {
        entitlements.reset();
    }

    /** 每个用户命令端点，带着能通过请求体校验的最小合法请求（校验先于命令方法执行）。 */
    static Stream<Arguments> gatedCommands() {
        return Stream.of(
                command("新建标书", () -> post("/api/bids").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"writingMethod\":\"SCORING_CRITERIA\",\"title\":\"门控标书\","
                                + "\"targetPages\":20,\"biddingMode\":\"BLIND\"}")),
                command("保存设置", () -> patch("/api/bids/{id}/setup", BID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"门控标书\",\"targetPages\":20,\"biddingMode\":\"BLIND\",\"revision\":0}")),
                command("上传招标文件", () -> multipart("/api/bids/{id}/source-file", BID)
                        .file(new MockMultipartFile("file", "tender.txt", "text/plain",
                                "招标文件".getBytes(StandardCharsets.UTF_8)))),
                command("解读", () -> post("/api/bids/{id}/interpretation/parse", BID)),
                command("保存解读", () -> put("/api/bids/{id}/criteria", BID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"PROJECT_OVERVIEW\",\"title\":\"t\",\"description\":\"d\"}],"
                                + "\"revision\":0}")),
                command("冻结解读", () -> post("/api/bids/{id}/interpretation/freeze", BID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")),
                command("选择素材", () -> put("/api/bids/{id}/asset-selections", BID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assetIds\":[]}")),
                command("生成目录", () -> post("/api/bids/{id}/outline/generate", BID)),
                command("保存目录", () -> put("/api/bids/{id}/outline", BID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodes\":[{\"clientId\":\"n1\",\"level\":1,\"title\":\"t\",\"plannedPages\":1}],"
                                + "\"confirm\":false,\"revision\":0}")),
                command("冻结目录", () -> post("/api/bids/{id}/outline/freeze", BID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")),
                command("生成正文", () -> post("/api/bids/{id}/content/generate", BID)),
                command("继续生成", () -> post("/api/bids/{id}/content/generation/resume", BID)),
                command("保存章节", () -> patch("/api/bids/{id}/chapters/{c}", BID, CHAPTER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\",\"revision\":0}")),
                command("AI 局部修订", () -> post("/api/bids/{id}/chapters/{c}/ai-revisions", BID, CHAPTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"REWRITE\",\"selectedHtml\":\"<p>x</p>\",\"revision\":0}")),
                command("成稿审查", () -> post("/api/bids/{id}/content/review", BID)),
                command("冻结正文", () -> post("/api/bids/{id}/content/freeze", BID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")),
                command("排版", () -> post("/api/bids/{id}/layout-jobs", BID)),
                command("导出", () -> post("/api/bids/{id}/exports", BID)),
                command("上传素材", () -> multipart("/api/bid-assets")
                        .file(new MockMultipartFile("file", "asset.txt", "text/plain",
                                "素材".getBytes(StandardCharsets.UTF_8)))
                        .param("category", "TEMPLATE")));
    }

    /** 刻意放行的：读、停止消耗、清理自己的数据。与 check_entitlement_gates.py 的名单一致。 */
    static Stream<Arguments> openEndpoints() {
        return Stream.of(
                command("标书列表", () -> get("/api/bids")),
                command("权益视图", () -> get("/api/entitlement")),
                command("暂停生成", () -> post("/api/bids/{id}/content/generation/pause", BID)),
                command("删除素材", () -> delete("/api/bid-assets/{id}", ASSET)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatedCommands")
    void aWorkspaceThatNeverSubscribedIsRefusedBeforeAnythingElse(
            String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        entitlements.use("none", "");

        mockMvc.perform(request.get().cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ENTITLED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    /**
     * 对照：同一批请求在有效订阅下必须越过判定。不做这一条，上面的 403 也可能来自
     * 别的原因（比如一个不存在的 id 恰好被答成 403），而测试照样是绿的。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("gatedCommands")
    void anActiveSubscriptionGetsPastTheGate(
            String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        MvcResult result = mockMvc.perform(request.get().cookie(session)).andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("NOT_ENTITLED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("openEndpoints")
    void readsPauseAndCleanupStayOpenWithoutASubscription(
            String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        entitlements.use("none", "");

        MvcResult result = mockMvc.perform(request.get().cookie(session)).andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("NOT_ENTITLED");
    }

    /**
     * 订阅状态轴：失效的三种被拒；未知档位按 BidCapability 的既有语义（空能力集）被拒。
     * overdue 是欠费宽限、权益保留（product_220 §3），平台照发 tier，所以放行。
     */
    @ParameterizedTest(name = "tier={0} status={1} → refused={2}")
    @CsvSource({
            "pro,expired,true",
            "pro,cancelled,true",
            "pro,suspended,true",
            "platinum,active,true",
            "starter,trialing,false",
            "business,overdue,false",
            "enterprise,active,false",
            "free,active,false"
    })
    void subscriptionStatesDecideTheGate(String tier, String status, boolean refused) throws Exception {
        entitlements.use(tier, status);

        MvcResult result = mockMvc.perform(post("/api/bids/{id}/interpretation/parse", BID).cookie(session))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (refused) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(body).contains("NOT_ENTITLED");
        } else {
            assertThat(body).doesNotContain("NOT_ENTITLED");
        }
    }

    @Test
    void anActiveSubscriptionCreatesABid() throws Exception {
        mockMvc.perform(post("/api/bids").cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"writingMethod\":\"SCORING_CRITERIA\",\"title\":\"有订阅的标书\","
                                + "\"targetPages\":20,\"biddingMode\":\"BLIND\"}"))
                .andExpect(status().isOk());
    }

    private static Arguments command(String name, Supplier<MockHttpServletRequestBuilder> request) {
        return Arguments.of(name, request);
    }
}
