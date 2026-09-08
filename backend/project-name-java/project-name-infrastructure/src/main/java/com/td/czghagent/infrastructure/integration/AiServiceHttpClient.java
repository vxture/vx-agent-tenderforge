// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
package com.td.czghagent.infrastructure.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.czghagent.domain.exception.AiGatewayException;
import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.ParsedDocument;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.DocumentParser;
import com.td.czghagent.domain.port.TenderAiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Component
public class AiServiceHttpClient implements DocumentParser, TenderAiGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestClient client;
    private final String internalToken;

    public AiServiceHttpClient(
            RestClient.Builder builder,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.internal-token}") String internalToken,
            @Value("${app.ai.timeout-seconds:120}") long timeoutSeconds
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public ParsedDocument parse(String documentId, StoredFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("documentId", documentId);
        body.add("file", filePart(file));
        try {
            ParsedDocument result = client.post()
                    .uri("/internal/parse")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ParsedDocument.class);
            if (result == null) {
                throw new BusinessException("PARSER_EMPTY_RESPONSE", "文件解析服务未返回结果", 502);
            }
            return result;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    "PARSER_REJECTED",
                    exception.getStatusCode().is4xxClientError()
                            ? "文件内容无法解析，请检查格式" : "文件解析服务处理失败",
                    exception.getStatusCode().is4xxClientError() ? 422 : 502
            );
        } catch (RestClientException exception) {
            throw new BusinessException("PARSER_UNAVAILABLE", "文件解析服务暂不可用", 502);
        }
    }

    @Override
    public Interpretation interpret(InterpretationRequest request) {
        ProjectOverview overview = extractProjectOverview(request).data();
        TechnicalScoring scoring = extractTechnicalScoring(request).data();
        return new Interpretation(
                overview.projectOverview(), scoring.technicalScoringRequirements());
    }

    @Override
    public AiResponse<ProjectOverview> extractProjectOverview(InterpretationRequest request) {
        return postAi("/internal/tender/interpretation/project-overview",
                request, ProjectOverview.class);
    }

    @Override
    public AiResponse<TechnicalScoring> extractTechnicalScoring(InterpretationRequest request) {
        return postAi("/internal/tender/interpretation/technical-scoring",
                request, TechnicalScoring.class);
    }

    @Override
    public AiResponse<OutlinePlan> planOutline(OutlineRequest request) {
        return postAi("/internal/tender/outline", request, OutlinePlan.class);
    }

    @Override
    public AiResponse<BidStrategy> planOutlineStrategy(OutlineRequest request) {
        return postAi("/internal/tender/outline/strategy", request, BidStrategy.class);
    }

    @Override
    public AiResponse<OutlineSkeletonPlan> planOutlineSkeleton(OutlineSkeletonRequest request) {
        return postAi("/internal/tender/outline/skeleton", request, OutlineSkeletonPlan.class);
    }

    @Override
    public AiResponse<OutlineExpansion> expandOutline(OutlineExpansionRequest request) {
        return postAi("/internal/tender/outline/expansion", request, OutlineExpansion.class);
    }

    @Override
    public OutlinePlan assembleOutline(OutlineAssemblyRequest request) {
        return postObject("/internal/tender/outline/assemble", request, OutlinePlan.class);
    }

    @Override
    public AiResponse<BranchBlueprint> planBranchBlueprint(BranchBlueprintRequest request) {
        return postAi("/internal/tender/chapter/blueprint", request, BranchBlueprint.class);
    }

    @Override
    public AiResponse<ChapterDraft> draftChapter(ChapterDraftRequest request) {
        return postAi("/internal/tender/chapter", request, ChapterDraft.class);
    }

    @Override
    public AiResponse<RevisionCandidate> revise(RevisionRequest request) {
        return postAi("/internal/tender/revision", request, RevisionCandidate.class);
    }

    @Override
    public AiResponse<Review> review(ReviewRequest request) {
        return postAi("/internal/tender/review", request, Review.class);
    }

    private <T> AiResponse<T> postAi(String path, Object body, Class<T> responseType) {
        long started = System.nanoTime();
        try {
            JsonNode result = client.post()
                    .uri(path)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (result == null || !result.path("data").isObject()) {
                throw new BusinessException("AI_OUTPUT_INVALID", "AI 服务未返回有效结果", 502);
            }
            T data = OBJECT_MAPPER.treeToValue(result.path("data"), responseType);
            AiDiagnostics diagnostics = OBJECT_MAPPER.treeToValue(
                    result.path("diagnostics"), AiDiagnostics.class);
            return new AiResponse<>(data, diagnostics);
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception, path, started);
        } catch (ResourceAccessException exception) {
            throw transportFailure(exception, path, started);
        } catch (RestClientException exception) {
            throw new BusinessException("AI_GATEWAY_UNAVAILABLE", "AI 网关暂不可用", 502);
        } catch (Exception exception) {
            throw new BusinessException("AI_OUTPUT_INVALID", "AI 服务响应无法解析", 502);
        }
    }

    private <T> T postObject(String path, Object body, Class<T> responseType) {
        long started = System.nanoTime();
        try {
            T result = client.post().uri(path)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(responseType);
            if (result == null) {
                throw new BusinessException("AI_OUTPUT_INVALID", "AI 服务未返回有效结果", 502);
            }
            return result;
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception, path, started);
        } catch (ResourceAccessException exception) {
            throw transportFailure(exception, path, started);
        } catch (RestClientException exception) {
            throw new BusinessException("AI_GATEWAY_UNAVAILABLE", "AI 网关暂不可用", 502);
        }
    }

    private AiGatewayException responseFailure(
            RestClientResponseException exception, String path, long started
    ) {
        AiError error = parseAiError(exception.getResponseBodyAsString());
        int upstreamStatus = exception.getStatusCode().value();
        int status = upstreamStatus == 503 || upstreamStatus == 504 ? upstreamStatus : 502;
        String stage = error.stage() == null ? stage(path) : error.stage();
        Long elapsed = error.elapsedMillis() == null ? elapsedMillis(started) : error.elapsedMillis();
        return new AiGatewayException(
                error.code(), userMessage(error, stage, elapsed), status, error.failureReason(),
                error.finishReason(), error.responseLength(), error.responseHash(),
                error.attempts(), error.inputTokens(), error.outputTokens(),
                error.reasoningTokens(), error.cachedInputTokens(), stage, elapsed);
    }

    private BusinessException transportFailure(
            ResourceAccessException exception, String path, long started
    ) {
        if (isTimeout(exception)) {
            String stage = stage(path);
            long elapsed = elapsedMillis(started);
            return new AiGatewayException(
                    "AI_GATEWAY_TIMEOUT",
                    timeoutMessage("AI_GATEWAY_TIMEOUT", stage, elapsed), 504, "",
                    null, null, null, 1, null, null, null, null, stage, elapsed);
        }
        return new BusinessException("AI_GATEWAY_UNAVAILABLE", "AI 网关暂不可用", 502);
    }

    private String userMessage(AiError error, String stage, long elapsedMillis) {
        if ("AI_MODEL_TIMEOUT".equals(error.code())
                || "AI_GATEWAY_TIMEOUT".equals(error.code())) {
            return timeoutMessage(error.code(), stage, elapsedMillis);
        }
        return error.message();
    }

    private String timeoutMessage(String code, String stage, long elapsedMillis) {
        String failure = "AI_MODEL_TIMEOUT".equals(code) ? "模型执行超时" : "AI 网关响应超时";
        long elapsedSeconds = Math.max(1, (elapsedMillis + 999) / 1000);
        return stageLabel(stage) + failure + "（已等待约" + elapsedSeconds + "秒），请稍后重试";
    }

    private String stageLabel(String stage) {
        if (stage == null || stage.isBlank()) {
            return "AI 阶段";
        }
        if (stage.contains("bid_strategy") || stage.equals("outline_strategy")
                || stage.equals("strategy")) {
            return "目录策略";
        }
        if (stage.contains("outline_skeleton") || stage.equals("skeleton")) {
            return "目录骨架";
        }
        if (stage.contains("outline_expansion") || stage.contains("outline_branch_expansion")
                || stage.equals("expansion")) {
            return "三级目录扩展";
        }
        if (stage.contains("project_overview")) {
            return "项目概要解析";
        }
        if (stage.contains("technical_scoring")) {
            return "评分点解析";
        }
        if (stage.contains("chapter")) {
            return "章节生成";
        }
        if (stage.contains("review")) {
            return "标书审查";
        }
        if (stage.contains("revision")) {
            return "局部修订";
        }
        if (stage.equals("assemble")) {
            return "目录合并";
        }
        return "AI 阶段";
    }

    private String aiErrorMessage(String code) {
        return switch (code) {
            case "AI_MODEL_AUTH_FAILED" -> "AI 模型凭据校验失败";
            case "AI_PROVIDER_NOT_CONFIGURED" -> "AI 模型尚未完成配置";
            case "AI_OUTPUT_INVALID" -> "AI 返回内容未通过结构校验";
            case "AI_MODEL_TIMEOUT" -> "AI 模型阶段执行超时";
            case "AI_GATEWAY_TIMEOUT" -> "AI 网关响应超时";
            default -> "AI 工作流执行失败";
        };
    }

    private AiError parseAiError(String response) {
        try {
            JsonNode detail = OBJECT_MAPPER.readTree(response).path("detail");
            String code = detail.path("code").asText("AI_PROVIDER_ERROR");
            String message = detail.path("message").asText(aiErrorMessage(code));
            String objectName = detail.path("objectName").asText("");
            JsonNode validationErrors = detail.path("validationErrors");
            String validation = validationErrors.isArray() && !validationErrors.isEmpty()
                    ? validationErrors.get(0).asText("") : "";
            String failureReason = validationErrors.isArray()
                    ? limit(OBJECT_MAPPER.writeValueAsString(validationErrors)) : "";
            String context = objectName.isBlank() ? "" : "（" + objectName + "）";
            if (!validation.isBlank()) {
                context += "：" + validation;
            }
            return new AiError(
                    code, limit(message + context), failureReason,
                    nullableText(detail, "finishReason"), nullableInt(detail, "responseLength"),
                    nullableText(detail, "responseHash"), nullableInt(detail, "attempts"),
                    nullableLong(detail, "inputTokens"), nullableLong(detail, "outputTokens"),
                    nullableLong(detail, "reasoningTokens"),
                    nullableLong(detail, "cachedInputTokens"),
                    nullableText(detail, "stage"), nullableLong(detail, "elapsedMillis"));
        } catch (Exception ignored) {
            String code = response.contains("AI_OUTPUT_INVALID")
                    ? "AI_OUTPUT_INVALID" : "AI_PROVIDER_ERROR";
            return new AiError(code, aiErrorMessage(code), "", null, null, null, null,
                    null, null, null, null, null, null);
        }
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asLong() : null;
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String stage(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String limit(String value) {
        return value.substring(0, Math.min(1000, value.length()));
    }

    private record AiError(
            String code, String message, String failureReason, String finishReason,
            Integer responseLength, String responseHash, Integer attempts,
            Long inputTokens, Long outputTokens, Long reasoningTokens, Long cachedInputTokens,
            String stage, Long elapsedMillis
    ) {
    }

    private HttpEntity<ByteArrayResource> filePart(StoredFile file) {
        ByteArrayResource resource = new ByteArrayResource(file.content()) {
            @Override
            public String getFilename() {
                return file.fileName();
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType(file.mediaType()));
        headers.setContentDispositionFormData("file", file.fileName());
        return new HttpEntity<>(resource, headers);
    }

    private MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
