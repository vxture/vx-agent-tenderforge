// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.domain.model;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResult<>(List.copyOf(items), total, page, size, pages);
    }
}
