// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-03
package com.td.czghagent.infrastructure.export;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.BidDocument;
import com.td.czghagent.domain.model.BidWorkspace;
import com.td.czghagent.domain.port.BidDocumentExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.document-service.enabled", havingValue = "true")
public class PythonBidDocumentExporter implements BidDocumentExporter {
    private final RestClient client;
    private final String internalToken;

    public PythonBidDocumentExporter(
            RestClient.Builder builder,
            @Value("${app.document-service.base-url}") String baseUrl,
            @Value("${app.ai.internal-token}") String internalToken,
            @Value("${app.document-service.timeout-seconds:1800}") long timeoutSeconds
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(30));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public byte[] renderDocx(BidDocument bid,
                             List<BidWorkspace.OutlineNode> outline,
                             List<BidWorkspace.Chapter> chapters) {
        return render(bid, outline, chapters, List.of()).docx();
    }

    @Override
    public RenderedDocument render(BidDocument bid,
                                   List<BidWorkspace.OutlineNode> outline,
                                   List<BidWorkspace.Chapter> chapters,
                                   List<String> forbiddenTerms) {
        DocumentRequest request = new DocumentRequest(
                bid.id(), bid.title(), bid.biddingMode(), bid.targetPages(),
                outline.stream().map(item -> new OutlineNode(
                        item.id(), item.parentId(), item.level(), item.title(), item.sortOrder())).toList(),
                chapters.stream().map(item -> new Chapter(
                        item.id(), item.outlineNodeId(), item.title(), item.content())).toList(),
                forbiddenTerms
        );
        try {
            ResponseEntity<byte[]> response = client.post()
                    .uri("/internal/tender/document/render")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] content = response.getBody();
            if (content == null || content.length == 0) {
                throw new BusinessException("BID_LAYOUT_EMPTY", "文档服务未返回DOCX成果", 502);
            }
            Integer actualPages = parsePages(response.getHeaders().getFirst("X-Actual-Pages"));
            String qaStatus = valueOr(response.getHeaders().getFirst("X-QA-Status"), "FAILED");
            String qaSummary = decodeSummary(response.getHeaders().getFirst("X-QA-Summary-Base64"));
            return new RenderedDocument(content, actualPages, qaStatus, qaSummary);
        } catch (RestClientException exception) {
            throw new BusinessException("BID_DOCUMENT_SERVICE_FAILED", "正式排版服务处理失败", 502);
        }
    }

    private Integer parsePages(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String decodeSummary(String value) {
        try {
            return value == null || value.isBlank() ? "文档服务未返回QA摘要"
                    : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "文档服务QA摘要格式无效";
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record DocumentRequest(
            String bidId, String title, String biddingMode, int targetPages,
            List<OutlineNode> outline, List<Chapter> chapters, List<String> forbiddenTerms
    ) {
    }

    private record OutlineNode(
            String id, String parentId, int level, String title, int sortOrder
    ) {
    }

    private record Chapter(
            String id, String outlineNodeId, String title, String content
    ) {
    }
}
