// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.exception;

public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public BusinessException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static BusinessException notFound(String type) {
        return new BusinessException(type + "_NOT_FOUND", "请求的" + type + "不存在", 404);
    }

    public static BusinessException forbidden() {
        return new BusinessException("PROJECT_ACCESS_DENIED", "无权访问该项目", 403);
    }
}
