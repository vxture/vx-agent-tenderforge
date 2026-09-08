// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.exception;

import java.util.Set;

/**
 * 业务异常，携带平台统一错误封套所需的全部字段（产品接入通则 X-1）。
 *
 * <p>{@code retryable} 是<strong>被调方自己的答复</strong>，不是调用方按状态码推断出来的。
 * 判据只有一条：同一个请求原样重发，过一会儿会不会成功。上游超时、依赖不可达算；
 * 任何需要人介入的（改请求、改授权、改业务状态）都不算——等待改变不了任何事。
 *
 * <p>默认值由 {@link #RETRYABLE_CODES} 按错误码派生，而不是在六十多个抛出点各写一个布尔。
 * 集中一处的好处是它可以被评审：读这张表就能回答「本产品认为哪些失败会自愈」，
 * 散在各处的布尔回答不了这个问题。需要覆盖时用带 {@code retryable} 的构造器显式声明。
 */
public class BusinessException extends RuntimeException {

    /**
     * 会自愈的失败。全部是「上游或依赖此刻不可用」，重发有意义。
     *
     * <p>刻意不含的两类：业务状态类（{@code BID_*_NOT_FROZEN}、{@code BID_STEP_NOT_READY}）
     * 要用户先推进流程；并发冲突类（{@code BID_REVISION_CONFLICT}、
     * {@code USER_CONCURRENT_UPDATE}）要调用方带新 revision 重来——
     * 两者原样重发都只会得到同一个拒绝。
     */
    private static final Set<String> RETRYABLE_CODES = Set.of(
            "AI_GATEWAY_TIMEOUT",
            "AI_GATEWAY_UNAVAILABLE",
            "AI_MODEL_TIMEOUT",
            "AI_PROVIDER_UNAVAILABLE",
            "PARSER_UNAVAILABLE",
            "PARSER_EMPTY_RESPONSE",
            "BID_DOCUMENT_SERVICE_FAILED",
            "STORAGE_WRITE_FAILED"
    );

    private final String errorCode;
    private final int httpStatus;
    private final boolean retryable;
    private final String field;

    public BusinessException(String errorCode, String message, int httpStatus) {
        this(errorCode, message, httpStatus, RETRYABLE_CODES.contains(errorCode), null);
    }

    public BusinessException(String errorCode, String message, int httpStatus,
                             boolean retryable, String field) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.field = field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** 见类注释：被调方自己的答复，调用方照读即可。 */
    public boolean isRetryable() {
        return retryable;
    }

    /** 字段级错误的定位，非字段错误为 null（封套里整体省略）。 */
    public String getField() {
        return field;
    }

    public static BusinessException notFound(String type) {
        return new BusinessException(type + "_NOT_FOUND", "请求的" + type + "不存在", 404);
    }

    /**
     * 求值拒绝一律 403，不是 401。
     *
     * <p>401 是「凭证无效，重换 token 再来」，403 是「换多少次票都不行」。
     * 把 403 当 401 处理，调用方会陷入一个永远重试永远失败的循环。
     */
    public static BusinessException forbidden() {
        return new BusinessException("PROJECT_ACCESS_DENIED", "无权访问该项目", 403);
    }

    /** 字段级校验失败，把出错的字段名带进封套。 */
    public static BusinessException invalidField(String errorCode, String message, String field) {
        return new BusinessException(errorCode, message, 400, false, field);
    }
}
