// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.domain.port.ReadinessProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时面：存活与就绪。
 *
 * <p>平台的产品健康页探测的是产品自己的 {@code /api/ready}，
 * 没有这个端点的产品在健康页上会一直显示「未实现」——这是首批接入里
 * 好几个产品共同踩过的一格，成本是上线后没有人会在掉线时被叫醒。
 *
 * <p><strong>存活探针按契约零依赖</strong>：它只回答「进程活着吗、是哪个构建」。
 * 在这里放数据库检查，会让一次数据库抖动重启掉一个本来健康的容器。
 * 依赖状态是<strong>就绪</strong>要回答的问题，两者刻意不合并。
 *
 * <p>就绪的每项检查<strong>只回 status 与耗时</strong>，不回原始异常文本——
 * 那里面可能含内网主机名和连接串，属于受守卫的诊断内容，不该出现在一个公开探针上。
 */
@RestController
@RequestMapping("/api")
public class RuntimeController {

    private final ReadinessProbe readinessProbe;
    private final String version;
    private final String deployStage;

    public RuntimeController(ReadinessProbe readinessProbe,
                             @Value("${app.version:dev}") String version,
                             @Value("${app.deploy-stage:local}") String deployStage) {
        this.readinessProbe = readinessProbe;
        this.version = version;
        this.deployStage = deployStage;
    }

    /** 存活：零依赖，永远 200。进程能应答这一条，就说明它还在。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "version", version, "deployStage", deployStage);
    }

    /**
     * 就绪：依赖可用才 200。
     *
     * <p>{@code blocked} 返 503，{@code ready} 与 {@code degraded} 返 200——
     * 降级不是不可用，把它也判成 503 会让编排在一个还能服务的实例上反复重启。
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        List<ReadinessProbe.Check> results = readinessProbe.check();
        boolean blocked = results.stream()
                .anyMatch(check -> check.status() == ReadinessProbe.Status.DOWN);
        List<Map<String, Object>> checks = results.stream().map(RuntimeController::describe).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", blocked ? "blocked" : "ready");
        body.put("version", version);
        body.put("checks", checks);
        return blocked ? ResponseEntity.status(503).body(body) : ResponseEntity.ok(body);
    }

    private static Map<String, Object> describe(ReadinessProbe.Check check) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("name", check.name());
        described.put("status", check.status().name().toLowerCase(java.util.Locale.ROOT));
        described.put("latencyMs", check.latencyMs());
        return described;
    }
}
