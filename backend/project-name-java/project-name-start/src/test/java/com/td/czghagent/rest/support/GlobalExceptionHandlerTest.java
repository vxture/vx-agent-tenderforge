// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.support;

import com.td.czghagent.domain.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 每一条离开本服务的失败都是同一个封套（通则 X-1）：{@code code}、{@code message}、{@code retryable} 三个必备，
 * {@code field} 只在字段级错误时出现。
 *
 * <p>漏掉任何一条路径，Spring 就用它自己的 {@code {timestamp, status, error, path}} 作答——
 * 同一个面上出现第二种信封，没有 {@code code} 也没有 {@code retryable}，调用方只能退回按状态码猜。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── 直接调用：每个具名分支的状态码、码、可重试性、字段定位 ───────────────

    @Test
    void aBusinessExceptionKeepsItsOwnStatusCodeRetryabilityAndField() {
        ResponseEntity<ErrorEnvelope> response = handler.handleBusiness(
                new BusinessException("BID_TITLE_TOO_LONG", "标题过长", 422, false, "title"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isEqualTo(new ErrorEnvelope("BID_TITLE_TOO_LONG", "标题过长", false, "title"));
    }

    @Test
    void aMissingQueryParameterNamesTheParameter() {
        ResponseEntity<ErrorEnvelope> response = handler.handleMissingParam(
                new MissingServletRequestParameterException("cursor", "String"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(
                new ErrorEnvelope("REQUEST_PARAM_MISSING", "缺少必填参数：cursor", false, "cursor"));
    }

    @Test
    void aMissingUploadPartNamesThePart() {
        ResponseEntity<ErrorEnvelope> response = handler.handleMissingPart(new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("REQUEST_PART_MISSING");
        assertThat(response.getBody().field()).isEqualTo("file");
    }

    @Test
    void anUnsupportedMethodIs405AndSaysWhichMethod() {
        ResponseEntity<ErrorEnvelope> response = handler.handleMethod(new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().code()).isEqualTo("REQUEST_METHOD_NOT_ALLOWED");
        assertThat(response.getBody().message()).contains("PATCH");
    }

    @Test
    void anUnsupportedMediaTypeIs415() {
        ResponseEntity<ErrorEnvelope> response = handler.handleMediaType(
                new HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody().code()).isEqualTo("REQUEST_MEDIA_TYPE_UNSUPPORTED");
    }

    @Test
    void anUnknownRouteIs404InTheEnvelopeNotSpringsDefaultBody() {
        ResponseEntity<ErrorEnvelope> response = handler.handleNoRoute(
                new NoResourceFoundException(HttpMethod.GET, "/api/nope"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("REQUEST_ROUTE_NOT_FOUND");
    }

    /** 原样重发同一个文件永远是同一个结果，所以不可重试。 */
    @Test
    void anOversizedUploadIs413AndNotRetryable() {
        ResponseEntity<ErrorEnvelope> response = handler.handleUploadSize(new MaxUploadSizeExceededException(10));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isEqualTo(
                new ErrorEnvelope("REQUEST_UPLOAD_TOO_LARGE", "上传内容超出大小上限", false, null));
    }

    /** 兜底是本服务自己的故障：可重试，且不把异常原文（可能含连接串、路径）带给调用方。 */
    @Test
    void anUnexpectedErrorIsRetryableAndDoesNotLeakItsMessage() {
        ResponseEntity<ErrorEnvelope> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://db:5432/vx_tenderforge_db?password=secret"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().retryable()).isTrue();
        assertThat(response.getBody().message()).doesNotContain("jdbc").doesNotContain("secret");
    }

    // ── 经 MVC：真实的异常类型由框架抛出，封套形状按 JSON 核对 ────────────────

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void anInvalidBodyNamesTheFirstInvalidField() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("title"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void malformedJsonIsItsOwnCodeAndCarriesNoField() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_MALFORMED"))
                .andExpect(jsonPath("$.field").doesNotExist())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void aParameterOfTheWrongTypeNamesTheParameter() throws Exception {
        mvc.perform(get("/probe/number").param("page", "second"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_PARAM_INVALID"))
                .andExpect(jsonPath("$.field").value("page"));
    }

    @Test
    void aMissingRequiredParameterGoesThroughTheEnvelopeToo() throws Exception {
        mvc.perform(get("/probe/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_PARAM_MISSING"))
                .andExpect(jsonPath("$.field").value("cursor"));
    }

    /** 封套里不能出现 Spring 缺省错误体的字段——出现了就说明有一条路径绕过了这里。 */
    @Test
    void theBodyHasOnlyEnvelopeFields() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        record Body(@NotBlank String title) {
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/number")
        void number(@RequestParam int page) {
        }

        @GetMapping("/required")
        void required(@RequestParam String cursor) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("jdbc:postgresql://db:5432/vx_tenderforge_db?password=secret");
        }
    }
}
