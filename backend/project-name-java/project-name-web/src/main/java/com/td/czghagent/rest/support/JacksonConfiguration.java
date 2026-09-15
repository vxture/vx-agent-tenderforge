// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 出参时间一律带时区偏移（《产品接入通则》次要约定：时间一律 ISO-8601 带时区字符串）。
 *
 * <p>领域模型用挂钟时间 {@link LocalDateTime}——库里存的是 {@code TIMESTAMPTZ} 绝对时刻，
 * 由 {@code JdbcTimes} 显式转到 JVM 时区。默认序列化是 {@code 2026-09-15T14:00:00}，
 * <strong>不带偏移</strong>：调用方只能猜这是哪个时区的挂钟，而浏览器会按它自己的时区猜。
 * 这里在出参边界补上 JVM 时区的偏移：{@code 2026-09-15T14:00:00+08:00}。
 *
 * <p>只改序列化，不改入参解析：请求里的时间都走查询参数 {@code @DateTimeFormat}，
 * 不经 Jackson；没有任何请求体字段是 {@code LocalDateTime}。
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer offsetLocalDateTimes() {
        return builder -> builder.serializerByType(
                LocalDateTime.class, new OffsetLocalDateTimeSerializer(ZoneId.systemDefault()));
    }

    /** 按给定时区把挂钟时间补成带偏移的 ISO-8601。时区与 {@code JdbcTimes} 读出时用的是同一个。 */
    static final class OffsetLocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
        private final ZoneId zone;

        OffsetLocalDateTimeSerializer(ZoneId zone) {
            super(LocalDateTime.class);
            this.zone = zone;
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(
                    value.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
