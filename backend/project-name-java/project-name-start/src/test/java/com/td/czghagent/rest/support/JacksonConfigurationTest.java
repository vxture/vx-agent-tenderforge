// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.rest.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出参时间带偏移，且偏移对应的是同一个绝对时刻。
 *
 * <p>只断言「有偏移」不够：把挂钟时间原样拼上 {@code +00:00} 也有偏移，
 * 东八区下却差了 8 小时。所以同时断言解析回来的时刻等于按 JVM 时区解释的那个时刻。
 * 用例不依赖跑测试的机器在哪个时区。
 */
class JacksonConfigurationTest {

    private final ObjectMapper mapper = mapperWithCustomizer();

    @Test
    void writesAWallClockTimeWithTheJvmZoneOffsetForTheSameInstant() throws Exception {
        LocalDateTime wallClock = LocalDateTime.of(2026, 9, 15, 14, 30, 5);

        String written = mapper.readTree(mapper.writeValueAsString(new Stamp(wallClock)))
                .path("at").asText();

        assertThat(written).as("出参时间必须带时区偏移").matches(".*([+-]\\d{2}:\\d{2}|Z)$");
        assertThat(OffsetDateTime.parse(written).toInstant())
                .as("偏移要对应同一个绝对时刻，而不是给挂钟时间随手拼一个偏移")
                .isEqualTo(wallClock.atZone(ZoneId.systemDefault()).toInstant());
    }

    record Stamp(LocalDateTime at) {
    }

    private static ObjectMapper mapperWithCustomizer() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfiguration().offsetLocalDateTimes().customize(builder);
        return builder.build();
    }
}
