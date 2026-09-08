// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.health;

import com.td.czghagent.domain.port.ReadinessProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 就绪探测的实现：目前只探数据库。
 *
 * <p>失败的原始异常只进日志，不进返回值——响应里留下的是 {@code DOWN} 加耗时，
 * 排障要的细节去日志里拿。这不是吝啬，是因为这个端点没有守卫。
 */
@Component
public class JdbcReadinessProbe implements ReadinessProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcReadinessProbe.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcReadinessProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Check> check() {
        return List.of(probeDatabase());
    }

    private Check probeDatabase() {
        long startedAt = System.nanoTime();
        Status status;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            status = Status.UP;
        } catch (RuntimeException exception) {
            LOGGER.warn("Readiness probe failed for database", exception);
            status = Status.DOWN;
        }
        return new Check("database", status, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
