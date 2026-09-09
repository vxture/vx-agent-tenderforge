// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.application.command.service.AuthCommandService;
import com.td.czghagent.domain.model.ParsedDocument;
import com.td.czghagent.domain.port.DocumentParser;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.bootstrap.enabled=false",
        // 冲洗任务在后台跑会去认领测试刚写进去的行——关掉它。
        "app.platform.usage-flush-enabled=false",
        "app.storage.root=${java.io.tmpdir}/tender-writing-${random.uuid}"
})
@AutoConfigureMockMvc
@Import(TenderWritingIntegrationTest.ParserConfiguration.class)
class TenderWritingIntegrationTest extends PostgresBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthCommandService authCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createUsers() {
        authCommandService.createSeedUser("criteria-owner", "Criteria@123", "Scoring validation user", "PLANNER");
        authCommandService.createSeedUser("tender-owner", "Owner@123", "投标编制员", "PLANNER");
        authCommandService.createSeedUser("tender-other", "Other@123", "其他编制员", "PLANNER");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ParserConfiguration {
        @Bean
        @Primary
        DocumentParser tenderParser() {
            return (documentId, file) -> new ParsedDocument(
                    "招标文件", "技术方案评分：30分。出现弄虚作假按废标处理。暗标不得出现投标人名称。",
                    "HIGH", List.of(new ParsedDocument.Evidence(
                    "PARAGRAPH", "段落1", "技术方案评分：30分；暗标不得出现投标人名称。"
            )), List.of(), List.of());
        }

        @Bean
        @Primary
        TenderAiGateway tenderAiGateway() {
            return new TenderAiGatewayStub();
        }
    }

    static final class TenderAiGatewayStub implements TenderAiGateway {

        @Override
        public Interpretation interpret(InterpretationRequest request) {
            return new Interpretation(
                    "## 项目概述\n\n建设技术服务平台。",
                    "## 技术部分评分要求\n\n### 技术方案\n\n* 分值：30分"
            );
        }

        @Override
        public AiResponse<ProjectOverview> extractProjectOverview(InterpretationRequest request) {
            return success(new ProjectOverview("## 项目概述\n\n建设技术服务平台。"));
        }

        @Override
        public AiResponse<TechnicalScoring> extractTechnicalScoring(
                InterpretationRequest request) {
            return success(new TechnicalScoring(
                    "## 技术部分评分要求\n\n### 技术方案\n\n* 分值：30分"));
        }

        @Override
        public AiResponse<OutlinePlan> planOutline(OutlineRequest request) {
            String scoringPointId = request.criteria().getFirst().id();
            OutlineNode section = new OutlineNode(
                    "section-1", null, 1, " 技术实施方案 ", request.targetPages(),
                    "说明项目理解、总体架构和实施方法。", List.of("技术方案"), List.of(scoringPointId)
            );
            OutlineNode topic = new OutlineNode(
                    "topic-1", section.nodeKey(), 2, "总体技术方案", 0,
                    "说明总体技术路线和架构设计。", List.of("技术路线"), List.of(scoringPointId)
            );
            OutlineNode chapter = new OutlineNode(
                    "chapter-1", topic.nodeKey(), 3, "技术实施路径", 0,
                    "说明项目理解、总体架构和实施方法。", List.of("技术方案"), List.of(scoringPointId)
            );
            return success(new OutlinePlan(
                    List.of(section, topic, chapter),
                    List.of(new CoverageItem(scoringPointId, List.of(chapter.nodeKey()))),
                    FrozenDictionary.empty(), List.of()
            ));
        }

        @Override
        public AiResponse<BidStrategy> planOutlineStrategy(OutlineRequest request) {
            return success(new BidStrategy(
                    "技术服务平台", "形成可实施、可验证的技术服务平台建设方案。",
                    List.of("需求可追溯", "架构边界清晰", "交付结果可验证"),
                    List.of(new TechnicalTheme(
                            "总体技术架构", "明确平台模块边界和协同关系。",
                            "按接入、服务和运维职责组织技术能力。",
                            List.of("接入层", "服务层"), List.of("接口校验"),
                            List.of("架构评审"))),
                    List.of(new ScoringResponseStrategy(
                            "技术方案完整、合理、可行", "确认方案具备落地能力",
                            List.of("技术架构", "实施方法"), List.of("验收矩阵"), 5)),
                    List.of("冻结参数保持一致"), List.of(), List.of("虚构业绩")));
        }

        @Override
        public AiResponse<OutlineSkeletonPlan> planOutlineSkeleton(
                OutlineSkeletonRequest request) {
            OutlineSkeletonNode root = new OutlineSkeletonNode(
                    "root-1", null, 1, "技术实施方案", request.outline().targetPages(),
                    "", List.of());
            OutlineSkeletonNode branch = new OutlineSkeletonNode(
                    "branch-1", root.nodeKey(), 2, "总体技术方案", 0,
                    "说明总体技术路线和架构设计。", List.of("技术路线"));
            OutlineBranchTarget target = new OutlineBranchTarget(
                    branch.nodeKey(), root.title(), branch.title(), branch.taskBrief(),
                    branch.mustKeywords(), 2);
            return success(new OutlineSkeletonPlan(
                    List.of(root, branch), List.of(), List.of(List.of(target)),
                    List.of("SP-001")));
        }

        @Override
        public AiResponse<OutlineExpansion> expandOutline(OutlineExpansionRequest request) {
            String parentKey = request.branches().getFirst().nodeKey();
            return success(new OutlineExpansion(List.of(
                    new OutlineExpansionNode(
                            parentKey, "技术实施路径", "说明实施方法。",
                            List.of("技术方案"), List.of("SP-001")),
                    new OutlineExpansionNode(
                            parentKey, "交付验收方法", "说明交付与验收方法。",
                            List.of("验收"), List.of("SP-001"))
            ), List.of()));
        }

        @Override
        public OutlinePlan assembleOutline(OutlineAssemblyRequest request) {
            return planOutline(request.outline()).data();
        }

        @Override
        public AiResponse<BranchBlueprint> planBranchBlueprint(BranchBlueprintRequest request) {
            return success(new BranchBlueprint(
                    "形成统一、可实施且可验证的二级技术域方案。",
                    List.of("统一模块边界和接口契约"), List.of("保持冻结事实一致"),
                    request.chapters().stream().map(chapter -> new LeafBlueprint(
                            chapter.id(), "完成当前章节的专业技术响应。",
                            List.of("按职责边界组织技术方案"),
                            List.of("配置技术组件", "校验接口结果"),
                            List.of("技术设计说明"), List.of("执行设计评审"),
                            "连续技术段落")).toList(),
                    List.of(), List.of()));
        }

        @Override
        public AiResponse<ChapterDraft> draftChapter(ChapterDraftRequest request) {
            return success(new ChapterDraft(
                    List.of(), "本章完成技术实施方案响应。", List.of(), List.of(), List.of(),
                    "<p>本方案根据招标文件技术要求，形成完整、可执行的技术实施路径。</p>"
            ));
        }

        @Override
        public AiResponse<RevisionCandidate> revise(RevisionRequest request) {
            return success(new RevisionCandidate(
                    List.of(), "保持事实不变并优化表达。", request.protectedFacts(), List.of(),
                    request.selectedHtml()
            ));
        }

        @Override
        public AiResponse<Review> review(ReviewRequest request) {
            return success(new Review(
                    true, List.of(), new ReviewCoverage(1, 1, List.of()), List.of(), "审查通过"
            ));
        }

        private <T> AiResponse<T> success(T data) {
            return new AiResponse<>(data, new AiDiagnostics(
                    "stop", 128,
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    32L, 64L, null, null, 1));
        }
    }

    @Test
    void manualInterpretationRequiresExactlyTwoBusinessObjects() throws Exception {
        String owner = login("criteria-owner", "Criteria@123");
        JsonNode created = performJson(post("/api/bids"), owner, """
                {"writingMethod":"SCORING_CRITERIA","title":"评分点校验标书",
                 "targetPages":20,"biddingMode":"BLIND"}
                """);
        String bidId = created.path("bid").path("id").asText();
        long revision = created.path("bid").path("revision").asLong();

        mockMvc.perform(put("/api/bids/{bidId}/criteria", bidId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"revision":%d,"items":[{
                                  "type":"PROJECT_OVERVIEW","title":"项目概述",
                                  "description":"建设统一平台","score":null,
                                  "sourceExcerpt":"建设统一平台","sourceLocator":"段落1",
                                  "scope":"TECHNICAL","confidence":"HIGH"}]}
                                """.formatted(revision)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BID_CRITERIA_REQUIRED"));

        mockMvc.perform(put("/api/bids/{bidId}/criteria", bidId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"revision":%d,"items":[
                                  {"type":"PROJECT_OVERVIEW","title":"项目概述","description":"项目内容",
                                   "score":null,"scope":"TECHNICAL","confidence":"HIGH"},
                                  {"type":"FORMAT","title":"装订要求","description":"双面打印",
                                   "score":null,"scope":"FORMAT","confidence":"HIGH"}]}
                                """.formatted(revision)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BID_CRITERION_INVALID"));
    }

    @Test
    void ownerCompletesFourStepsAndOtherUserCannotReadBid() throws Exception {
        String owner = login("tender-owner", "Owner@123");
        String other = login("tender-other", "Other@123");
        mockMvc.perform(get("/api/bids").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        JsonNode created = performJson(post("/api/bids"), owner, """
                {"writingMethod":"SCORING_CRITERIA","title":"技术标投标文件",
                 "targetPages":60,"biddingMode":"BLIND"}
                """);
        String bidId = created.path("bid").path("id").asText();
        assertThat(created.path("bid").path("workflowStep").asText()).isEqualTo("INTERPRETATION");
        mockMvc.perform(get("/api/bids").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/bids/{bidId}", bidId).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BID_ACCESS_DENIED"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "招标文件.txt", MediaType.TEXT_PLAIN_VALUE,
                "技术方案评分：30分。暗标不得出现投标人名称。".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/bids/{bidId}/source-file", bidId).file(file)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        JsonNode accepted = responseData(mockMvc.perform(
                        post("/api/bids/{bidId}/interpretation/parse", bidId)
                                .header("Authorization", bearer(owner)))
                .andExpect(status().isAccepted())
                .andReturn());
        assertThat(accepted.path("sourceFile").path("parseStatus").asText())
                .isIn("PARSING", "SUCCEEDED");
        JsonNode parsed = awaitInterpretation(bidId, owner);
        assertThat(parsed.path("sourceFile").path("overviewStatus").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(parsed.path("sourceFile").path("scoringStatus").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(parsed.path("criteria").size()).isEqualTo(2);
        assertThat(parsed.path("criteria").get(0).path("type").asText())
                .isEqualTo("PROJECT_OVERVIEW");
        assertThat(parsed.path("criteria").get(0).path("title").asText()).isEqualTo("项目概述");
        assertThat(parsed.path("criteria").get(1).path("type").asText())
                .isEqualTo("TECHNICAL_SCORING");

        JsonNode criteriaBody = objectMapper.createObjectNode()
                .put("revision", parsed.path("bid").path("revision").asLong())
                .set("items", objectMapper.createArrayNode());
        for (JsonNode criterion : parsed.path("criteria")) {
            var item = objectMapper.createObjectNode()
                    .put("id", criterion.path("id").asText())
                    .put("type", criterion.path("type").asText())
                    .put("title", criterion.path("title").asText())
                    .put("description", criterion.path("description").asText())
                    .put("sourceExcerpt", criterion.path("sourceExcerpt").asText())
                    .put("sourceLocator", criterion.path("sourceLocator").asText())
                    .put("scope", criterion.path("scope").asText())
                    .put("confidence", criterion.path("confidence").asText());
            if (criterion.path("score").isNumber()) {
                item.put("score", criterion.path("score").asDouble());
            } else {
                item.putNull("score");
            }
            ((com.fasterxml.jackson.databind.node.ArrayNode) criteriaBody.path("items")).add(item);
        }
        mockMvc.perform(put("/api/bids/{bidId}/criteria", bidId)
                        .header("Authorization", bearer(owner))
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5174")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(criteriaBody)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5174"));

        JsonNode freezeBody = objectMapper.createObjectNode()
                .put("revision", parsed.path("bid").path("revision").asLong());
        mockMvc.perform(post("/api/bids/{bidId}/interpretation/freeze", bidId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(freezeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.interpretationStatus").value("FROZEN"));

        JsonNode acceptedOutline = responseData(mockMvc.perform(
                        post("/api/bids/{bidId}/outline/generate", bidId)
                                .header("Authorization", bearer(owner)))
                .andExpect(status().isAccepted())
                .andReturn());
        assertThat(acceptedOutline.path("outlineTask").path("status").asText())
                .isIn("PENDING", "RUNNING", "SUCCEEDED");
        JsonNode outline = awaitOutline(bidId, owner);
        assertThat(outline.path("outline").get(0).path("title").asText())
                .isEqualTo("技术实施方案");
        JsonNode confirmBody = objectMapper.createObjectNode()
                .put("confirm", true)
                .put("revision", outline.path("bid").path("revision").asLong())
                .set("nodes", objectMapper.createArrayNode());
        for (JsonNode node : outline.path("outline")) {
            var outlineItem = objectMapper.createObjectNode()
                    .put("clientId", node.path("id").asText())
                    .put("parentClientId", node.path("parentId").isNull()
                            ? null : node.path("parentId").asText())
                    .put("level", node.path("level").asInt())
                    .put("title", node.path("title").asText())
                    .put("plannedPages", node.path("plannedPages").asInt())
                    .put("taskBrief", node.path("taskBrief").asText());
            outlineItem.set("mustKeywords", node.path("mustKeywords").deepCopy());
            outlineItem.set("scoringPointIds", node.path("scoringPointIds").deepCopy());
            ((com.fasterxml.jackson.databind.node.ArrayNode) confirmBody.path("nodes")).add(outlineItem);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) criteriaBody)
                .put("revision", outline.path("bid").path("revision").asLong());
        ((com.fasterxml.jackson.databind.node.ObjectNode) criteriaBody.path("items").get(0))
                .put("description", "完整响应技术方案评分要求并补充实施细则");
        JsonNode criteriaChangedBeforeContent = responseData(mockMvc.perform(
                        put("/api/bids/{bidId}/criteria", bidId)
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(criteriaBody)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(criteriaChangedBeforeContent.path("bid").path("contentStale").asBoolean())
                .isFalse();
        ((com.fasterxml.jackson.databind.node.ObjectNode) freezeBody)
                .put("revision", criteriaChangedBeforeContent.path("bid").path("revision").asLong());
        JsonNode refrozenInterpretation = responseData(mockMvc.perform(
                        post("/api/bids/{bidId}/interpretation/freeze", bidId)
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(freezeBody)))
                .andExpect(status().isOk())
                .andReturn());
        ((com.fasterxml.jackson.databind.node.ObjectNode) confirmBody).put(
                "revision", refrozenInterpretation.path("bid").path("revision").asLong()
        );
        JsonNode confirmed = responseData(mockMvc.perform(put("/api/bids/{bidId}/outline", bidId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(confirmBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bid.status").value("OUTLINE_READY"))
                .andReturn());
        assertThat(confirmed.path("bid").path("contentStale").asBoolean()).isFalse();

        mockMvc.perform(post("/api/bids/{bidId}/content/generate", bidId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isAccepted());
        Long allocatedCharacters = jdbcTemplate.queryForObject("""
                SELECT SUM(word_budget) FROM bid_generation_unit WHERE bid_id = ?
                """, Long.class, bidId);
        assertThat(allocatedCharacters).isEqualTo(39_900L);
        JsonNode workspace = awaitGeneration(bidId, owner);
        assertThat(workspace.path("chapters")).isNotEmpty();
        assertThat(workspace.path("generationTask").path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(workspace.path("bid").path("contentStale").asBoolean()).isFalse();
        assertThat(workspace.path("production").path("contentStatus").asText()).isEqualTo("FROZEN");
        assertThat(workspace.path("production").path("layoutJob").path("status").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(workspace.path("exports").get(0).path("version").asInt()).isEqualTo(1);
        mockMvc.perform(get("/api/bids/{bidId}/outline", bidId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapters[0].tableCount").isNumber());

        String generationTaskId = workspace.path("generationTask").path("id").asText();
        String activeAiRunId = jdbcTemplate.queryForObject("""
                SELECT id FROM bid_ai_run WHERE task_id = ? ORDER BY started_at DESC LIMIT 1
                """, String.class, generationTaskId);
        String activeAttemptId = jdbcTemplate.queryForObject("""
                SELECT current_attempt_id FROM bid_ai_run WHERE id = ?
                """, String.class, activeAiRunId);
        jdbcTemplate.update(
                "UPDATE bid_ai_run SET status = 'RUNNING' WHERE id = ?", activeAiRunId);
        jdbcTemplate.update(
                "UPDATE bid_ai_run_attempt SET status = 'RUNNING', finished_at = NULL WHERE id = ?",
                activeAttemptId);
        jdbcTemplate.update(
                "UPDATE bid_generation_task SET status = 'RUNNING' WHERE id = ?",
                generationTaskId);
        jdbcTemplate.update(
                "UPDATE bid_document SET status = 'GENERATING' WHERE id = ?", bidId);
        mockMvc.perform(post("/api/bids/{bidId}/content/generation/pause", bidId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationTask.status").value("PAUSED"))
                .andExpect(jsonPath("$.bid.status").value("GENERATION_PAUSED"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM bid_ai_run_attempt WHERE id = ?
                """, String.class, activeAttemptId)).isEqualTo("INTERRUPTED");
        Integer succeededBeforeResume = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_generation_unit
                WHERE task_id = ? AND status = 'SUCCEEDED'
                """, Integer.class, generationTaskId);
        mockMvc.perform(post("/api/bids/{bidId}/content/generation/resume", bidId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isAccepted());
        JsonNode resumedWorkspace = awaitGeneration(bidId, owner);
        assertThat(resumedWorkspace.path("generationTask").path("status").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_generation_unit
                WHERE task_id = ? AND status = 'SUCCEEDED'
                """, Integer.class, generationTaskId)).isEqualTo(succeededBeforeResume);

        // 夹具走 owner 连接：往标题里塞前后空白，验应用会不会规范化。
        // 生产改评分条款走的是删+插，从不 UPDATE 标题——所以这几列落在列锁
        // 白名单之外是对的，用受限角色去做只会得到与被测行为无关的
        // permission denied（见 PostgresBackedTest#fixtureJdbc）。
        JdbcTemplate fixture = fixtureJdbc();
        fixture.update(
                "UPDATE bid_scoring_criterion SET title = CONCAT(' ', title, ' ') WHERE bid_id = ?",
                bidId
        );
        fixture.update(
                "UPDATE bid_outline_node SET title = CONCAT(' ', title, ' ') WHERE bid_id = ?",
                bidId
        );
        fixture.update(
                "UPDATE bid_chapter SET title = CONCAT(' ', title, ' ') WHERE bid_id = ?",
                bidId
        );

        JsonNode latestWorkspace = responseData(mockMvc.perform(
                        get("/api/bids/{bidId}", bidId)
                                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andReturn());
        long stableRevision = latestWorkspace.path("bid").path("revision").asLong();
        ((com.fasterxml.jackson.databind.node.ObjectNode) criteriaBody)
                .put("revision", stableRevision);
        JsonNode unchangedCriteria = responseData(mockMvc.perform(
                        put("/api/bids/{bidId}/criteria", bidId)
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(criteriaBody)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unchangedCriteria.path("bid").path("revision").asLong()).isEqualTo(stableRevision);
        assertThat(unchangedCriteria.path("bid").path("contentStale").asBoolean()).isFalse();

        long unchangedCriteriaRevision = unchangedCriteria.path("bid").path("revision").asLong();
        ((com.fasterxml.jackson.databind.node.ObjectNode) confirmBody)
                .put("revision", unchangedCriteriaRevision);
        JsonNode unchangedOutline = responseData(mockMvc.perform(
                        put("/api/bids/{bidId}/outline", bidId)
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(confirmBody)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unchangedOutline.path("bid").path("revision").asLong())
                .isEqualTo(unchangedCriteriaRevision);
        assertThat(unchangedOutline.path("bid").path("contentStale").asBoolean()).isFalse();

        JsonNode reviewed = responseData(mockMvc.perform(
                        post("/api/bids/{bidId}/content/review", bidId)
                                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.contentStatus").value("FROZEN"))
                .andReturn());
        assertThat(reviewed.path("production").path("reviewIssues").toString())
                .doesNotContain("\"severity\":\"ERROR\"");
        JsonNode contentFreezeBody = objectMapper.createObjectNode()
                .put("revision", reviewed.path("bid").path("revision").asLong());
        JsonNode frozen = responseData(mockMvc.perform(post("/api/bids/{bidId}/content/freeze", bidId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(contentFreezeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.contentStatus").value("FROZEN"))
                .andReturn());

        JsonNode frozenChapter = frozen.path("chapters").get(0);
        JsonNode editBody = objectMapper.createObjectNode()
                .put("content", frozenChapter.path("content").asText() + "<p>人工复核通过。</p>")
                .put("revision", frozenChapter.path("revision").asLong());
        mockMvc.perform(patch("/api/bids/{bidId}/chapters/{chapterId}", bidId,
                        frozenChapter.path("id").asText())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(editBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapter.generationStatus").value("MANUAL"))
                .andExpect(jsonPath("$.chapter.content").value(
                        org.hamcrest.Matchers.containsString("人工复核通过")));
        mockMvc.perform(post("/api/bids/{bidId}/layout-jobs", bidId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        JsonNode rereviewed = responseData(mockMvc.perform(
                        post("/api/bids/{bidId}/content/review", bidId)
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(rereviewed.path("production").path("reviewIssues").toString())
                .doesNotContain("\"severity\":\"ERROR\"");
        JsonNode refreezeBody = objectMapper.createObjectNode()
                .put("revision", rereviewed.path("bid").path("revision").asLong());
        mockMvc.perform(post("/api/bids/{bidId}/content/freeze", bidId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(refreezeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.contentStatus").value("FROZEN"));
        mockMvc.perform(post("/api/bids/{bidId}/layout-jobs", bidId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.layoutJob.id").isNotEmpty());
        JsonNode laidOut = awaitLayout(bidId, owner);
        assertThat(laidOut.path("production").path("layoutJob").path("qaStatus").asText())
                .isEqualTo("PASSED");
        assertThat(laidOut.path("exports").get(0).path("version").asInt()).isEqualTo(2);
        JsonNode exports = responseData(mockMvc.perform(
                        get("/api/bids/{bidId}/exports", bidId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(exports).isNotEmpty();
        String exportId = exports.get(exports.size() - 1).path("id").asText();
        byte[] docx = mockMvc.perform(
                get("/api/bids/{bidId}/exports/{exportId}/download", bidId, exportId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(docx).startsWith("PK".getBytes(StandardCharsets.US_ASCII));

        JsonNode exportedWorkspace = responseData(mockMvc.perform(
                        get("/api/bids/{bidId}", bidId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn());
        String existingChapterId = exportedWorkspace.path("chapters").get(0).path("id").asText();
        String existingContent = exportedWorkspace.path("chapters").get(0).path("content").asText();
        ((com.fasterxml.jackson.databind.node.ObjectNode) confirmBody)
                .put("revision", exportedWorkspace.path("bid").path("revision").asLong());
        ((com.fasterxml.jackson.databind.node.ObjectNode) confirmBody.path("nodes").get(0))
                .put("plannedPages", 61);
        JsonNode editedOutline = responseData(mockMvc.perform(
                        put("/api/bids/{bidId}/outline", bidId)
                                .header("Authorization", bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(confirmBody)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(editedOutline.path("bid").path("contentStale").asBoolean()).isTrue();
        assertThat(editedOutline.path("chapters").get(0).path("id").asText())
                .isEqualTo(existingChapterId);
        assertThat(editedOutline.path("chapters").get(0).path("content").asText())
                .isEqualTo(existingContent);
        assertThat(editedOutline.path("chapters").get(0).path("generationStatus").asText())
                .isEqualTo("MANUAL");

        JsonNode regenerationAccepted = responseData(mockMvc.perform(
                        post("/api/bids/{bidId}/outline/generate", bidId)
                                .header("Authorization", bearer(owner)))
                .andExpect(status().isAccepted()).andReturn());
        assertThat(regenerationAccepted.path("outlineTask").path("status").asText())
                .isIn("PENDING", "RUNNING");
        JsonNode regeneratedOutline = awaitOutline(bidId, owner);
        assertThat(regeneratedOutline.path("outlineTask").path("status").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(regeneratedOutline.path("chapters").get(0).path("id").asText())
                .isNotEqualTo(existingChapterId);
        assertThat(regeneratedOutline.path("chapters").get(0).path("content").asText()).isEmpty();
        assertThat(regeneratedOutline.path("outline").get(0).path("plannedPages").asInt())
                .isEqualTo(60);
        assertThat(regeneratedOutline.path("outline").get(1).path("plannedPages").asInt()).isZero();
        assertThat(regeneratedOutline.path("outline").get(2).path("plannedPages").asInt()).isZero();
        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bid_outline_regeneration_archive WHERE bid_id = ?",
                Integer.class, bidId);
        assertThat(archiveCount).isOne();
        String archivedChapters = jdbcTemplate.queryForObject("""
                SELECT chapter_json FROM bid_outline_regeneration_archive
                WHERE bid_id = ? ORDER BY created_at DESC LIMIT 1
                """, String.class, bidId);
        assertThat(archivedChapters).contains(existingChapterId, existingContent);

        mockMvc.perform(patch("/api/account/profile")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"投标负责人\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("投标负责人"));
        List<String> auditedOperations = jdbcTemplate.queryForList(
                "SELECT DISTINCT operation_type FROM bid_ai_run WHERE bid_id = ? AND status = 'SUCCEEDED'",
                String.class, bidId);
        // 目录现在按阶段走：策略、骨架、逐批展开各自成为一次可恢复的 AI 运行，
        // 所以主流程不再有一条笼统的 OUTLINE 记录。这不只是命名变化——
        // 每个阶段单独入账，才可能回答「这个任务卡在第几批、试了几次」。
        //
        assertThat(auditedOperations).contains(
                "INTERPRETATION_PROJECT_OVERVIEW", "INTERPRETATION_TECHNICAL_SCORING",
                "OUTLINE_STRATEGY", "OUTLINE_SKELETON", "OUTLINE_EXPANSION",
                "BRANCH_BLUEPRINT", "CHAPTER_DRAFT", "REVIEW");
        assertThat(auditedOperations)
                .as("单次调用那条路径已经不再被主流程使用")
                .doesNotContain("OUTLINE");
        Integer missingDiagnostics = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_ai_run
                WHERE bid_id = ? AND status = 'SUCCEEDED'
                  AND operation_type IN (
                    'INTERPRETATION_PROJECT_OVERVIEW',
                    'INTERPRETATION_TECHNICAL_SCORING',
                    'OUTLINE_STRATEGY', 'OUTLINE_SKELETON', 'OUTLINE_EXPANSION',
                    'BRANCH_BLUEPRINT', 'CHAPTER_DRAFT', 'REVIEW')
                  AND (finish_reason IS NULL OR response_length IS NULL OR response_hash IS NULL)
                """, Integer.class, bidId);
        assertThat(missingDiagnostics).isZero();
        Integer completedOutlineStages = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_outline_stage_result s
                JOIN bid_outline_task t ON t.id = s.task_id
                WHERE t.bid_id = ? AND s.status = 'SUCCEEDED'
                """, Integer.class, bidId);
        // 分阶段目录（V23「resumable outline stages」）现在接通了。
        //
        // 这条断言此前被改成「确实一行都没有」，因为那时领域端口、JDBC 实现、
        // bid_outline_stage_result 表和专门的迁移都在，却没有任何生产调用方。
        // 当时留了一句话：谁把这条链接回主流程，这个用例就会红，而那正是需要有人
        // 来决定它该断言什么的时刻。它按预期红了，现在断言真实路径。
        //
        // 至少三个阶段：策略、骨架，以及至少一批展开。落库不是为了好看——
        // 没有它，一次十几批的展开在中途失败时会从策略开始整个重跑。
        assertThat(completedOutlineStages).isGreaterThanOrEqualTo(3);
        List<String> expansionModels = jdbcTemplate.queryForList("""
                SELECT DISTINCT s.model_name FROM bid_outline_stage_result s
                JOIN bid_outline_task t ON t.id = s.task_id
                WHERE t.bid_id = ? AND s.stage_type = 'EXPANSION'
                """, String.class, bidId);
        // 展开走 fast 档：它是量最大的一段（每批一次调用），
        // 用 quality 档跑完一份大标书的价钱是另一个量级。
        assertThat(expansionModels).containsExactly("deepseek-v4-flash");
        // 技术域蓝图：<b>一个二级分支一份</b>，不是一章一份。
        //
        // 一章一份既贵（每章多一次 quality 档调用），又恰好毁掉它自己的作用——
        // 蓝图存在的意义是让同一分支下的各章互相知道对方在写什么，
        // 而各章各自生成的蓝图之间没有任何一致性。
        Integer blueprintCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bid_snapshot_branch_blueprint b
                JOIN bid_generation_snapshot s ON s.id = b.snapshot_id
                WHERE s.bid_id = ?
                """, Integer.class, bidId);
        Integer branchCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT o.parent_source_outline_id)
                FROM bid_snapshot_outline o
                JOIN bid_generation_snapshot s ON s.id = o.snapshot_id
                WHERE s.bid_id = ? AND o.level_no = 3
                """, Integer.class, bidId);
        assertThat(blueprintCount)
                .as("每个有章节的二级分支恰好一份蓝图")
                .isEqualTo(branchCount);
        // 这个夹具里每个二级分支只有一章，所以上面那条<b>分不出</b>「一分支一份」
        // 和「一章一份」——实测确认过。它证明的是链路接通和落库，不是复用。
        // 复用由 BidBranchBlueprintServiceTest 覆盖，那里同一分支下有两章。
        assertThat(parsed.path("criteria")).isNotEmpty();
    }

    private JsonNode awaitGeneration(String bidId, String token) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < 300; attempt++) {
            latest = responseData(mockMvc.perform(get("/api/bids/{bidId}/generation-progress", bidId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            if (List.of("SUCCEEDED", "FAILED").contains(latest.path("status").asText())) {
                return responseData(mockMvc.perform(get("/api/bids/{bidId}", bidId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk()).andReturn());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("正文生成任务未完成，当前状态：" + latest.path("status").asText());
    }

    private JsonNode awaitInterpretation(String bidId, String token) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < 300; attempt++) {
            latest = responseData(mockMvc.perform(get("/api/bids/{bidId}", bidId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            String parseStatus = latest.path("sourceFile").path("parseStatus").asText();
            if ("SUCCEEDED".equals(parseStatus)) {
                return latest;
            }
            if ("FAILED".equals(parseStatus)) {
                throw new AssertionError("招标文件解读失败："
                        + latest.path("sourceFile").path("errorMessage").asText());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("招标文件解读未完成，当前阶段："
                + latest.path("sourceFile").path("parseStage").asText());
    }

    private JsonNode awaitOutline(String bidId, String token) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < 300; attempt++) {
            latest = responseData(mockMvc.perform(get("/api/bids/{bidId}", bidId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            String taskStatus = latest.path("outlineTask").path("status").asText();
            if ("SUCCEEDED".equals(taskStatus) && !latest.path("outline").isEmpty()) {
                return latest;
            }
            if ("FAILED".equals(taskStatus)) {
                throw new AssertionError("目录生成失败："
                        + latest.path("outlineTask").path("errorMessage").asText());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("目录生成未完成，当前阶段："
                + latest.path("outlineTask").path("stage").asText());
    }

    private JsonNode awaitLayout(String bidId, String token) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < 300; attempt++) {
            latest = responseData(mockMvc.perform(get("/api/bids/{bidId}/metadata", bidId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn());
            if (List.of("SUCCEEDED", "FAILED").contains(
                    latest.path("production").path("layoutJob").path("status").asText())) {
                return responseData(mockMvc.perform(get("/api/bids/{bidId}", bidId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk()).andReturn());
            }
            Thread.sleep(100);
        }
        JsonNode job = latest.path("production").path("layoutJob");
        throw new AssertionError("标书排版任务未完成，当前状态：" + job.path("status").asText()
                + "，错误：" + job.path("errorMessage").asText());
    }

    private JsonNode performJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 String token, String body) throws Exception {
        return responseData(mockMvc.perform(request.header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());
    }

    private String login(String username, String password) throws Exception {
        JsonNode data = responseData(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(username, password))))
                .andExpect(status().isOk()).andReturn());
        return data.path("token").asText();
    }

    /**
     * 读响应载荷。
     *
     * <p>不再剥 {@code data} 一层：成功响应<strong>直接就是载荷</strong>（通则 A-4）。
     * 这个方法留着而不是内联，是因为它同时是一道断言——如果哪天有人给成功响应
     * 重新套回一层信封，这里返回的就会是一个空节点，全部用例一起红。
     */
    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Credentials(String username, String password) {
    }
}
