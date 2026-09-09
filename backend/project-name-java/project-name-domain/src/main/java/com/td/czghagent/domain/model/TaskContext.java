// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

/**
 * 当前执行线程所属的 {@code task_id}（产品接入通则 X-2）。
 *
 * <p>它必须穿过十几层业务代码才能到达出站调用点。写进每层签名，等于让每个纯业务方法
 * 都认识一个传输层概念；所以这里用线程局部量，和 Python 侧的 {@code ContextVar} 同构。
 *
 * <p><strong>为空是合法状态。</strong>不要在读取处兜底成 traceId 或新 UUID——
 * 那会造出一个对方库里查不到的假聚合键，而假键比空值更难排查：
 * 它让「查得到调用、查不到它属于哪个任务」看起来像是对方丢了数据。
 *
 * <p>跨线程不自动传播。异步执行（线程池、Temporal 活动）要在入口显式 {@link #run}，
 * 这是刻意的：自动继承会让一个池化线程带着上一个任务的 id 去执行下一个任务，
 * 而那种串味在审计里表现为两个任务的消耗被合并，且没有任何报错。
 */
public final class TaskContext {

    /** 契约上限；超长视为未提供而不是截断，截断会让两侧的键对不上。 */
    public static final int MAX_LENGTH = 128;

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TaskContext() {
    }

    /** 当前 task_id，没有则返回 null。 */
    public static String current() {
        return CURRENT.get();
    }

    /** 规范化调用方送来的值：空白或超长一律视为未提供。 */
    public static String normalize(String supplied) {
        if (supplied == null) {
            return null;
        }
        String trimmed = supplied.trim();
        return trimmed.isEmpty() || trimmed.length() > MAX_LENGTH ? null : trimmed;
    }

    /** 在给定 task_id 下执行一段逻辑，结束后恢复原值（支持嵌套）。 */
    public static <T> T run(String taskId, java.util.function.Supplier<T> action) {
        String previous = CURRENT.get();
        set(taskId);
        try {
            return action.get();
        } finally {
            set(previous);
        }
    }

    /** 同 {@link #run}，用于无返回值的逻辑。 */
    public static void run(String taskId, Runnable action) {
        run(taskId, () -> {
            action.run();
            return null;
        });
    }

    private static void set(String taskId) {
        if (taskId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(taskId);
        }
    }
}
