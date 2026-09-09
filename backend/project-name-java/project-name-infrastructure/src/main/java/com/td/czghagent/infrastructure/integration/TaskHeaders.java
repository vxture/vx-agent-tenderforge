// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.integration;

import com.td.czghagent.domain.model.TaskContext;
import org.springframework.http.HttpHeaders;

/**
 * 给出站请求补上 {@code task_id}（产品接入通则 X-2）。
 *
 * <p>被调方的义务是把这个键<strong>原样落库</strong>；调用方的义务是一路带过去。
 * 链条上任一跳换成自产的 request id，聚合就断在那一跳——而断掉的表现不是报错，
 * 是汇总时那一段消耗归不到任务上，两侧的数看起来都对。
 *
 * <p>没有 task_id 时<strong>不发这个头</strong>，而不是发一个空值或现编一个：
 * 空头会让被调方存下一个空字符串键，编出来的值会让它存下一个查不到的假键。
 */
public final class TaskHeaders {

    /** HTTP 面的承载位置，与 Java 入站过滤器、Python 中间件三处保持同名。 */
    public static final String TASK_ID = "X-Vxture-Task-Id";

    private TaskHeaders() {
    }

    public static void apply(HttpHeaders headers) {
        String taskId = TaskContext.current();
        if (taskId != null) {
            headers.set(TASK_ID, taskId);
        }
    }
}
