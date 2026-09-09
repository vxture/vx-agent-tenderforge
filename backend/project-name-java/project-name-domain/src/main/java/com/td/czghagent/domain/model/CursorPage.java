// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.util.List;

/**
 * 无界流水的响应形状（产品接入通则 A-3 / A-4）。
 *
 * <p>判据是「这张表会不会因为系统自己跑而无限增长」：审计日志、调用记录属于无界流水，
 * 必须给游标和服务端钳制的 limit；有界的管理面对象直接返裸数组。
 *
 * <p>承载集合的键一律叫 {@code items}——不是 {@code rows} / {@code data} / {@code content}。
 * 这些面装的是事件而不是表行，{@code rows} 会把存储隐喻带进一份刻意与存储无关的契约。
 *
 * <p>{@code nextCursor} 为 null 表示没有下一页。<strong>绝不静默截断</strong>：
 * 超过上限的 limit 被钳制时，钳制后的值通过游标语义自然体现，
 * 而调用方能从「返回条数 == 上限且有 nextCursor」看出还有数据。
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor
) {

    /** 服务端钳制上限。请求超过这个值不报错，钳制到这里，并靠 nextCursor 交代还有后续。 */
    public static final int MAX_LIMIT = 200;

    /** 未指定 limit 时的默认页宽。 */
    public static final int DEFAULT_LIMIT = 20;

    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(List.copyOf(items), nextCursor);
    }

    /** 把请求的 limit 钳制进 [1, MAX_LIMIT]。 */
    public static int clampLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
