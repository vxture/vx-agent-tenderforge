// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.support;

import com.td.czghagent.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把每一条离开本服务的失败都塞进同一个封套（产品接入通则 X-1）。
 *
 * <p>这里的完整性本身是契约的一部分：只要有一条路径漏网，Spring 就会用它自己的
 * {@code {timestamp, status, error, path}} 作答——那是同一个面上的<strong>第二种信封形状</strong>，
 * 而且它没有 {@code code}，也没有 {@code retryable}。新消费方最先撞上的就是这个。
 *
 * <p>{@code traceId} 不再进封套：它是本服务自产的 request id，
 * 按 X-2 它不能替代 {@code task_id} 做归因；诊断关联走响应头 {@code x-trace-id}，
 * 由 {@code TraceIdFilter} 统一写入，不占契约字段位。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorEnvelope> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getHttpStatus()).body(new ErrorEnvelope(
                exception.getErrorCode(), exception.getMessage(),
                exception.isRetryable(), exception.getField()
        ));
    }

    /**
     * 请求体校验失败。
     *
     * <p>只回第一个字段错误而不是全部，是有意的取舍：这一面的消费方是自家前端，
     * 表单逐字段校验在浏览器侧已经做过一轮，服务端这条路径主要挡非浏览器调用。
     * 注册类接口那种「一次返回全部违规」的形态留给管理面自己定。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> ResponseEntity.badRequest().body(ErrorEnvelope.ofField(
                        "REQUEST_VALIDATION_FAILED",
                        error.getField() + "：" + error.getDefaultMessage(),
                        false, error.getField()
                )))
                .orElseGet(() -> ResponseEntity.badRequest().body(ErrorEnvelope.of(
                        "REQUEST_VALIDATION_FAILED", "请求参数不合法", false
                )));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorEnvelope> handleMissingParam(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(ErrorEnvelope.ofField(
                "REQUEST_PARAM_MISSING", "缺少必填参数：" + exception.getParameterName(),
                false, exception.getParameterName()
        ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorEnvelope> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest().body(ErrorEnvelope.ofField(
                "REQUEST_PART_MISSING", "缺少上传内容：" + exception.getRequestPartName(),
                false, exception.getRequestPartName()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ErrorEnvelope.ofField(
                "REQUEST_PARAM_INVALID", "参数取值不合法：" + exception.getName(),
                false, exception.getName()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ErrorEnvelope.of(
                "REQUEST_BODY_MALFORMED", "请求体不是合法 JSON", false
        ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorEnvelope.of(
                "REQUEST_METHOD_NOT_ALLOWED", "该资源不支持 " + exception.getMethod() + " 方法", false
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ErrorEnvelope.of(
                "REQUEST_MEDIA_TYPE_UNSUPPORTED", "不支持的请求内容类型", false
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNoRoute(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorEnvelope.of(
                "REQUEST_ROUTE_NOT_FOUND", "请求的接口不存在", false
        ));
    }

    /**
     * 超过上传上限。
     *
     * <p>{@code retryable=false}：原样重发同一个文件永远是同一个结果，
     * 调用方要做的是换个小文件，不是等一会儿。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ErrorEnvelope.of(
                "REQUEST_UPLOAD_TOO_LARGE", "上传内容超出大小上限", false
        ));
    }

    /**
     * 兜底。
     *
     * <p>{@code retryable=true}：未预期的异常按定义是本服务自己的故障，
     * 而这一类里能自愈的（瞬时连接失败、线程池打满）占多数；
     * 明确不该重试的失败都有具名分支，走不到这里。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled request error", exception);
        return ResponseEntity.internalServerError().body(ErrorEnvelope.of(
                "INTERNAL_ERROR", "系统处理失败，请稍后重试", true
        ));
    }
}
