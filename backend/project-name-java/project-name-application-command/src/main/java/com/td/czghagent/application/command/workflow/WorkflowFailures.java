// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.application.command.workflow;

import io.temporal.failure.ApplicationFailure;

/**
 * 工作流失败时写回任务的原因。四个工作流共用一份，免得各抄一遍再各自走样。
 */
final class WorkflowFailures {

    private WorkflowFailures() {
    }

    /**
     * 取失败的根因原文。
     *
     * <p>活动失败到了工作流里是 {@code ActivityFailure → ApplicationFailure}，而
     * {@link ApplicationFailure#getMessage()} 是 Temporal 格式化过的
     * {@code message='上游超时', type='AI_PROVIDER_ERROR', nonRetryable=true}——原样写回任务，
     * 用户在界面上看到的是一串协议文本而不是原因。这里取最深一层 {@link ApplicationFailure}
     * 的 {@link ApplicationFailure#getOriginalMessage() 原文}；没有时退回最深一层异常的消息。
     */
    static String rootMessage(Throwable exception, String fallback) {
        String original = null;
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ApplicationFailure failure
                    && failure.getOriginalMessage() != null && !failure.getOriginalMessage().isBlank()) {
                original = failure.getOriginalMessage();
            }
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        if (original != null) {
            return original;
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
