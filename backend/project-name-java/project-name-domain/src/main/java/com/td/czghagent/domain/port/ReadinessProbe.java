// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.port;

import java.util.List;

/**
 * 就绪探测端口。
 *
 * <p>Web 层不直接持有数据源：探针要回答的是「依赖可用吗」，
 * 而「依赖是什么」属于基础设施的知识。把 JdbcTemplate 提到 Web 层能少写一个接口，
 * 代价是从此以后每加一个依赖，Web 层都要认识它一次。
 *
 * <p>每项检查<strong>只回状态与耗时</strong>。原始异常文本留在实现侧的日志里——
 * 连接失败的消息里带着内网主机名、端口甚至用户名，那些不该出现在一个公开探针的响应上。
 */
public interface ReadinessProbe {

    List<Check> check();

    /**
     * 三态而不是布尔：{@code degraded} 表示「这一项不健康但产品仍能服务」，
     * 布尔装不下它，而把降级并进 down 会让编排反复重启一个还能干活的实例。
     */
    enum Status {
        UP, DEGRADED, DOWN
    }

    record Check(String name, Status status, long latencyMs) {
    }
}
