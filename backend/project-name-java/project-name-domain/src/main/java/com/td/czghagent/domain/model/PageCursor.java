// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 键集游标的编解码：把排序锚点 {@code (createdAt, id)} 编成一个不透明串。
 *
 * <p>不透明是刻意的。游标一旦长得像可读的偏移量或时间戳，调用方就会开始<strong>构造</strong>它
 * ——自己拼一个时间往前跳、把它当"第几页"存进 URL——而那一刻服务端就再也不能改排序键了。
 * base64url 不提供任何保密性，它提供的是「这不是给你解析的」这个信号。
 *
 * <p>解码失败一律返回 {@code null}（即从头开始），而不是抛错：
 * 游标会因为服务端换了排序键而失效，那时用户手里的旧游标不该表现为一个报错页面。
 * 代价是拼错的游标静默从头开始——可以接受，因为游标不是用户输入的东西。
 */
public record PageCursor(LocalDateTime createdAt, String id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 解码；空、畸形或不可解析一律返回 null，表示「从头开始」。 */
    public static PageCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(SEPARATOR);
            if (separator <= 0 || separator == raw.length() - 1) {
                return null;
            }
            return new PageCursor(
                    LocalDateTime.parse(raw.substring(0, separator)),
                    raw.substring(separator + 1)
            );
        } catch (IllegalArgumentException | DateTimeParseException ignored) {
            return null;
        }
    }
}
