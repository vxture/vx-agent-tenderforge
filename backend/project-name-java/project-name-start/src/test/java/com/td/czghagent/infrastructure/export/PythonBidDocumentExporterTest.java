// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.infrastructure.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.model.TaskContext;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.port.BidDocumentExporter;
import com.td.czghagent.infrastructure.integration.TaskHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 正式排版的出站调用：请求体字段、质量结果从响应头读回、失败翻成业务码。
 *
 * <p>这里错的样子都不报错：请求体字段名漂了，Python 侧按缺省值排出一份空目录的文档；
 * 页数头读不出来，页数偏差检查永远不触发；摘要解码失败时抛异常，
 * 一份已经排好的成果就因为一段说明文字丢掉了。
 *
 * <p>用真会说 HTTP 的假服务而不是打桩 {@link RestClient}：导出器自己设了请求工厂与读超时，
 * 打桩或绑定模拟服务器都会把这一层替换掉。
 */
class PythonBidDocumentExporterTest {

    private static final String TOKEN = "internal-token";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 10, 0);

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private final AtomicReference<String> seenToken = new AtomicReference<>();
    private final AtomicReference<String> seenTaskId = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<byte[]> responseBody = new AtomicReference<>("docx-bytes".getBytes(StandardCharsets.UTF_8));
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private final AtomicReference<Long> delayMillis = new AtomicReference<>(0L);

    private HttpServer server;

    private final BidDocument bid = new BidDocument("bid-1", "owner-1", new TenantScope("org-1", "ws-1"), "B-1",
            "SCORING_CRITERIA", "智慧园区投标文件", 120, "OPEN", "CONTENT", "GENERATING",
            false, null, NOW, NOW, 3);
    private final List<BidWorkspace.OutlineNode> outline = List.of(
            new BidWorkspace.OutlineNode("n1", null, 1, "总体设计", 30, 1, 1, "", List.of(), List.of()),
            new BidWorkspace.OutlineNode("n2", "n1", 2, "架构", 10, 2, 1, "", List.of(), List.of()));
    private final List<BidWorkspace.Chapter> chapters = List.of(
            new BidWorkspace.Chapter("c1", "n2", "架构", "<p>分层架构</p>", "READY", NOW, 4));

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/tender/document/render", exchange -> {
            seenToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            seenTaskId.set(exchange.getRequestHeaders().getFirst(TaskHeaders.TASK_ID));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            long delay = delayMillis.get();
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] bytes = responseBody.get();
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        responseHeaders.put("X-Actual-Pages", "118");
        responseHeaders.put("X-QA-Status", "PASSED");
        responseHeaders.put("X-QA-Summary-Base64", base64("目标120页，实测118页，偏差-2页"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private PythonBidDocumentExporter exporter(long timeoutSeconds) {
        return new PythonBidDocumentExporter(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), TOKEN, timeoutSeconds);
    }

    // ── 请求 ─────────────────────────────────────────────────────────────

    /** 请求体字段名是和 Python 侧的契约：驼峰、目录带层级与父节点、章节挂在目录节点上。 */
    @Test
    void sendsTheBidOutlineChaptersAndForbiddenTermsWithTheInternalToken() throws Exception {
        exporter(30).render(bid, outline, chapters, List.of("16GB", "某品牌"));

        assertThat(seenToken.get()).isEqualTo(TOKEN);
        JsonNode body = json.readTree(seenBody.get());
        assertThat(body.get("bidId").asText()).isEqualTo(bid.id());
        assertThat(body.get("title").asText()).isEqualTo(bid.title());
        assertThat(body.get("biddingMode").asText()).isEqualTo(bid.biddingMode());
        assertThat(body.get("targetPages").asInt()).isEqualTo(bid.targetPages());
        assertThat(body.get("outline")).hasSize(2);
        JsonNode child = body.get("outline").get(1);
        assertThat(child.get("id").asText()).isEqualTo("n2");
        assertThat(child.get("parentId").asText()).isEqualTo("n1");
        assertThat(child.get("level").asInt()).isEqualTo(2);
        assertThat(child.get("title").asText()).isEqualTo("架构");
        assertThat(child.get("sortOrder").asInt()).isEqualTo(2);
        JsonNode chapter = body.get("chapters").get(0);
        assertThat(chapter.get("id").asText()).isEqualTo("c1");
        assertThat(chapter.get("outlineNodeId").asText()).isEqualTo("n2");
        assertThat(chapter.get("title").asText()).isEqualTo("架构");
        assertThat(chapter.get("content").asText()).isEqualTo("<p>分层架构</p>");
        assertThat(json.convertValue(body.get("forbiddenTerms"), List.class)).containsExactly("16GB", "某品牌");
    }

    /** 通则 X-2：排版调用归到发起它的那个任务上。 */
    @Test
    void carriesTheCurrentTaskIdWhenThereIsOne() {
        TaskContext.run("task-9", () -> exporter(30).render(bid, outline, chapters, List.of()));

        assertThat(seenTaskId.get()).isEqualTo("task-9");
    }

    @Test
    void sendsNoTaskIdOutsideATask() {
        exporter(30).render(bid, outline, chapters, List.of());

        assertThat(seenTaskId.get()).isNull();
    }

    /** 只要 DOCX 的旧入口不带禁用口径，但仍然是同一次调用。 */
    @Test
    void renderDocxIsTheSameCallWithoutForbiddenTerms() throws Exception {
        byte[] docx = exporter(30).renderDocx(bid, outline, chapters);

        assertThat(docx).isEqualTo("docx-bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(json.readTree(seenBody.get()).get("forbiddenTerms")).isEmpty();
    }

    // ── 响应 ─────────────────────────────────────────────────────────────

    @Test
    void readsTheDocumentAndItsQualityResultFromTheResponse() {
        BidDocumentExporter.RenderedDocument rendered = exporter(30).render(bid, outline, chapters, List.of());

        assertThat(rendered.docx()).isEqualTo("docx-bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(rendered.actualPages()).isEqualTo(118);
        assertThat(rendered.qaStatus()).isEqualTo("PASSED");
        assertThat(rendered.qaSummary()).isEqualTo("目标120页，实测118页，偏差-2页");
    }

    /**
     * 质量头缺失或损坏时降级而不是失败：一份已经排好的成果不该因为一段说明文字丢掉。
     *
     * <p>缺状态按 {@code FAILED} 处理——不知道有没有过检查，就不能当作过了。
     */
    @Test
    void missingQualityHeadersDegradeToUnknownPagesAndAFailedStatus() {
        responseHeaders.clear();

        BidDocumentExporter.RenderedDocument rendered = exporter(30).render(bid, outline, chapters, List.of());

        assertThat(rendered.actualPages()).isNull();
        assertThat(rendered.qaStatus()).isEqualTo("FAILED");
        assertThat(rendered.qaSummary()).isEqualTo("文档服务未返回QA摘要");
    }

    @Test
    void malformedQualityHeadersDegradeInsteadOfThrowing() {
        responseHeaders.put("X-Actual-Pages", "一百二十");
        responseHeaders.put("X-QA-Summary-Base64", "***不是base64***");

        BidDocumentExporter.RenderedDocument rendered = exporter(30).render(bid, outline, chapters, List.of());

        assertThat(rendered.actualPages()).isNull();
        assertThat(rendered.qaStatus()).isEqualTo("PASSED");
        assertThat(rendered.qaSummary()).isEqualTo("文档服务QA摘要格式无效");
    }

    // ── 失败 ─────────────────────────────────────────────────────────────

    @Test
    void anEmptyDocumentIsALayoutFailureNotASuccess() {
        responseBody.set(new byte[0]);

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> exporter(30).render(bid, outline, chapters, List.of()));

        assertThat(failure.getErrorCode()).isEqualTo("BID_LAYOUT_EMPTY");
        assertThat(failure.getHttpStatus()).isEqualTo(502);
    }

    @Test
    void aServiceErrorBecomesADocumentServiceFailure() {
        responseStatus.set(502);
        responseBody.set("{\"code\":\"DOCUMENT_RENDER_FAILED\"}".getBytes(StandardCharsets.UTF_8));

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> exporter(30).render(bid, outline, chapters, List.of()));

        assertThat(failure.getErrorCode()).isEqualTo("BID_DOCUMENT_SERVICE_FAILED");
        assertThat(failure.getHttpStatus()).isEqualTo(502);
    }

    /** 配置的读超时真的生效：排版挂住时调用方拿到失败，而不是跟着挂住。 */
    @Test
    void theConfiguredReadTimeoutIsHonoured() {
        delayMillis.set(2_500L);

        BusinessException failure = catchThrowableOfType(BusinessException.class,
                () -> exporter(1).render(bid, outline, chapters, List.of()));

        assertThat(failure.getErrorCode()).isEqualTo("BID_DOCUMENT_SERVICE_FAILED");
    }

    private static String base64(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
