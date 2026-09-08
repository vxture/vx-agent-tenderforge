// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-06
package com.td.czghagent.domain.exception;

/** AI 网关失败，携带不包含模型原文的安全诊断。 */
public final class AiGatewayException extends BusinessException {
    private final String failureReason;
    private final String finishReason;
    private final Integer responseLength;
    private final String responseHash;
    private final Integer attempts;
    private final Long inputTokens;
    private final Long outputTokens;
    private final Long reasoningTokens;
    private final Long cachedInputTokens;
    private final String stage;
    private final Long elapsedMillis;

    public AiGatewayException(
            String errorCode, String message, int httpStatus, String failureReason,
            String finishReason, Integer responseLength, String responseHash, Integer attempts,
            Long inputTokens, Long outputTokens, Long reasoningTokens, Long cachedInputTokens,
            String stage, Long elapsedMillis
    ) {
        super(errorCode, message, httpStatus);
        this.failureReason = failureReason;
        this.finishReason = finishReason;
        this.responseLength = responseLength;
        this.responseHash = responseHash;
        this.attempts = attempts;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.reasoningTokens = reasoningTokens;
        this.cachedInputTokens = cachedInputTokens;
        this.stage = stage;
        this.elapsedMillis = elapsedMillis;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public Integer getResponseLength() {
        return responseLength;
    }

    public String getResponseHash() {
        return responseHash;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Long getReasoningTokens() {
        return reasoningTokens;
    }

    public Long getCachedInputTokens() {
        return cachedInputTokens;
    }

    public String getStage() {
        return stage;
    }

    public Long getElapsedMillis() {
        return elapsedMillis;
    }
}
