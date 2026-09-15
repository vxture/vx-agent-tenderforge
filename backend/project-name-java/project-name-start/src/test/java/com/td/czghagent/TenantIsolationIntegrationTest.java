// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.model.RpSession;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.repository.RpSessionRepository;
import com.td.czghagent.domain.service.SessionToken;
import com.td.czghagent.rest.security.RpSessionCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 标书与素材按「本人 × 当前工作空间」隔离。
 *
 * <p>平台身份下同一个人可以属于多个工作空间。读路径只按 {@code owner_id} 过滤时，
 * 一个人在 A 空间建的标书会出现在他 B 空间的列表里——响应完全正常，没有任何报错，
 * 只是数据在租户之间串了。本地口令时代这件事不可能发生（一个本地用户恰好一个
 * {@code local:} 空间），所以此前没有任何测试会发现它。
 *
 * <p>三个身份：同一个 subject 分别在 A、B 两个空间，外加 A 空间里的另一个人。
 * 「同一个人换了空间」是本类真正要守的那一格；「另一个人」保证旧的归属校验没有被顺手放宽。
 */
@SpringBootTest(properties = {
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/tenant-isolation-${random.uuid}"
})
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest extends PostgresBackedTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RpSessionRepository rpSessions;

    private String inWorkspaceA;
    private String sameUserInWorkspaceB;
    private String otherUserInWorkspaceA;

    @BeforeEach
    void signInThreeWays() {
        String subject = UUID.randomUUID().toString();
        String workspaceA = UUID.randomUUID().toString();
        String workspaceB = UUID.randomUUID().toString();
        inWorkspaceA = signIn(subject, workspaceA);
        sameUserInWorkspaceB = signIn(subject, workspaceB);
        otherUserInWorkspaceA = signIn(UUID.randomUUID().toString(), workspaceA);
    }

    @Test
    void bidsAreVisibleOnlyInTheWorkspaceTheyWereCreatedIn() throws Exception {
        String bidA = createBid(inWorkspaceA, "A 空间的标书");
        String bidB = createBid(sameUserInWorkspaceB, "B 空间的标书");

        assertThat(bidIds(inWorkspaceA)).containsExactly(bidA);
        assertThat(bidIds(sameUserInWorkspaceB)).containsExactly(bidB);
        assertThat(bidIds(otherUserInWorkspaceA)).isEmpty();

        // 按标识直取：换了空间的本人与别人同样被拒，语义与既有的越权一致（403）。
        mockMvc.perform(get("/api/bids/{bidId}", bidA).cookie(session(inWorkspaceA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bids/{bidId}", bidA).cookie(session(sameUserInWorkspaceB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BID_ACCESS_DENIED"));
        mockMvc.perform(get("/api/bids/{bidId}", bidA).cookie(session(otherUserInWorkspaceA)))
                .andExpect(status().isForbidden());

        // 查询侧之外再取一条命令侧的入口：两侧各有一份归属校验，只修一侧另一侧照样漏。
        mockMvc.perform(put("/api/bids/{bidId}/asset-selections", bidA)
                        .cookie(session(sameUserInWorkspaceB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BID_ACCESS_DENIED"));
    }

    @Test
    void assetsAreVisibleOnlyInTheWorkspaceTheyWereUploadedIn() throws Exception {
        String assetA = uploadGalleryAsset(inWorkspaceA);

        assertThat(assetIds(inWorkspaceA)).contains(assetA);
        assertThat(assetIds(sameUserInWorkspaceB)).doesNotContain(assetA);
        assertThat(assetIds(otherUserInWorkspaceA)).doesNotContain(assetA);

        // 在 B 空间里把 A 空间的素材选进标书：素材对 B 空间不存在，而不是「图库不能被引用」。
        // 两个拒绝都是 400，只有消息能分开——后者说明素材被找到了，隔离已经失效。
        String bidB = createBid(sameUserInWorkspaceB, "B 空间的标书");
        mockMvc.perform(put("/api/bids/{bidId}/asset-selections", bidB)
                        .cookie(session(sameUserInWorkspaceB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\":[\"" + assetA + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("所选素材不可用"));

        // 换了空间删不掉；回到原空间能删。先断言失败再断言成功，否则前一步删掉了就无从验起。
        mockMvc.perform(delete("/api/bid-assets/{assetId}", assetA).cookie(session(sameUserInWorkspaceB)))
                .andExpect(status().isNotFound());
        assertThat(assetIds(inWorkspaceA)).contains(assetA);
        mockMvc.perform(delete("/api/bid-assets/{assetId}", assetA).cookie(session(inWorkspaceA)))
                .andExpect(status().isNoContent());
    }

    private String createBid(String cookieValue, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bids")
                        .cookie(session(cookieValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"writingMethod\":\"SCORING_CRITERIA\",\"title\":\"" + title
                                + "\",\"targetPages\":20,\"biddingMode\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("bid").path("id").asText();
    }

    private String uploadGalleryAsset(String cookieValue) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/bid-assets")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .param("category", "GALLERY")
                        .cookie(session(cookieValue)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("id").asText();
    }

    private List<String> bidIds(String cookieValue) throws Exception {
        return ids(mockMvc.perform(get("/api/bids").cookie(session(cookieValue)))
                .andExpect(status().isOk()).andReturn());
    }

    private List<String> assetIds(String cookieValue) throws Exception {
        return ids(mockMvc.perform(get("/api/bid-assets").cookie(session(cookieValue)))
                .andExpect(status().isOk()).andReturn());
    }

    private List<String> ids(MvcResult result) throws Exception {
        JsonNode page = json(result);
        assertThat(page.path("items").isArray())
                .as("列表响应是 {items, nextCursor}（通则 A-3：没有写下来的上限就给游标）")
                .isTrue();
        List<String> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asText()));
        return ids;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /** 直接写一条 RP 会话：要的只是「某人在某个空间里已登录」，IdP 回路另有测试。 */
    private String signIn(String subject, String workspaceId) {
        String cookieValue = SessionToken.generate();
        LocalDateTime now = LocalDateTime.now();
        rpSessions.insertSession(new RpSession(
                UUID.randomUUID().toString(), subject, "测试用户", null, null,
                new TenantScope(UUID.randomUUID().toString(), workspaceId), "workspace:member",
                "access-" + subject, "refresh-" + subject,
                now.plusHours(12), now.plusHours(12)), SessionToken.hash(cookieValue));
        return cookieValue;
    }

    private Cookie session(String cookieValue) {
        return new Cookie(RpSessionCookie.PLAIN_NAME, cookieValue);
    }
}
