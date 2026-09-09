// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest.support;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 平台统一错误封套（产品接入通则 X-1）。
 *
 * <p>三个必备字段一个都不能可选：调用方读到 {@code undefined} 的 {@code retryable}，
 * 就会退回按状态码猜重试——而这个字段存在的全部意义就是终结那种猜测。
 * {@code field} 是字段级错误的可选定位，为空时整体省略而不是置 null。
 *
 * <p>本封套只承载失败。成功响应直接返回载荷本体（A-4）：无回显内容返裸数组，
 * 游标流水返 {@link CursorPage}。历史上的 {@code {success, data, errorCode}} 空壳信封已退役——
 * 它给每个消费方多加了一层没有第二个键可填的解包。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
        String code,
        String message,
        boolean retryable,
        String field
) {

    public static ErrorEnvelope of(String code, String message, boolean retryable) {
        return new ErrorEnvelope(code, message, retryable, null);
    }

    public static ErrorEnvelope ofField(String code, String message, boolean retryable, String field) {
        return new ErrorEnvelope(code, message, retryable, field);
    }
}
