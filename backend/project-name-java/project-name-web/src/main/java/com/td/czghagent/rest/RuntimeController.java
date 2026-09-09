// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-10
package com.td.czghagent.rest;

import com.td.czghagent.domain.port.ReadinessProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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
 * <p>身份块的字段名与形状由组织规范
 * <em>025-service-health-endpoint-contract</em> §3 固定：
 * {@code status/service/version/gitSha/stage/buildTime/time}，可选 {@code product}
 * 与 {@code uptimeSec}。字段名不是本仓能自选的——跨产品聚合按名字取值，
 * 各写各的名字（{@code sha} vs {@code gitSha}、{@code deployStage} vs {@code stage}）
 * 会让聚合端读到空值，而空值看起来跟「没部署」一模一样。
 *
 * <p>三个溯源值全部来自<strong>镜像 ENV</strong>（Dockerfile 的
 * {@code ARG APP_VERSION/GIT_SHA/BUILD_TIME} → {@code ENV}），不经过宿主机
 * {@code .env}。它们回答的是「这是哪一次构建」，一旦能在运行时被改写，
 * 就不再是那个问题的答案。缺失时诚实兜底 {@code dev}/{@code unknown}，
 * <strong>绝不编造</strong>——规范 §6 把硬编 {@code version:"1.0.0"} 列为禁止项。
 *
 * <p>就绪的每项检查<strong>只回 status 与耗时</strong>，不回原始异常文本——
 * 那里面可能含内网主机名和连接串，属于受守卫的诊断内容，不该出现在一个公开探针上。
 */
@RestController
@RequestMapping("/api")
public class RuntimeController {

    /** 最细粒度部署单元的标识；本产品的三个镜像各是一个 service。 */
    private static final String SERVICE = "tenderforge-api";
    /** 产品线标识，多服务产品用（规范 §3 可选字段）。 */
    private static final String PRODUCT = "tenderforge";

    private final ReadinessProbe readinessProbe;
    private final String version;
    private final String gitSha;
    private final String buildTime;
    private final String stage;
    private final Instant startedAt = Instant.now();

    public RuntimeController(ReadinessProbe readinessProbe,
                             @Value("${app.version:dev}") String version,
                             @Value("${app.git-sha:unknown}") String gitSha,
                             @Value("${app.build-time:unknown}") String buildTime,
                             @Value("${app.deploy-stage:local}") String stage) {
        this.readinessProbe = readinessProbe;
        this.version = version;
        // gitSha 不带 `sha-` 前缀：那个前缀是<b>镜像 tag 的形态</b>，不是数据本身。
        // 带着它，聚合端拿到的就不是一个能直接 `git show` 的值。
        this.gitSha = gitSha.startsWith("sha-") ? gitSha.substring(4) : gitSha;
        this.buildTime = buildTime;
        this.stage = stage;
    }

    /** 存活：零依赖，永远 200。进程能应答这一条，就说明它还在。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return identity("ok");
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
        Map<String, Object> body = identity(blocked ? "fail" : "ready");
        body.put("checks", results.stream().map(RuntimeController::describe).toList());
        return blocked ? ResponseEntity.status(503).body(body) : ResponseEntity.ok(body);
    }

    /** 规范 §3 的身份块。就绪端点在此基础上加 {@code checks}。 */
    private Map<String, Object> identity(String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("service", SERVICE);
        body.put("product", PRODUCT);
        body.put("version", version);
        body.put("gitSha", gitSha);
        body.put("stage", stage);
        body.put("buildTime", buildTime);
        // time 证明的是「实时应答 + 时钟正常」，所以每次都现取，不缓存。
        body.put("time", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        body.put("uptimeSec", Duration.between(startedAt, Instant.now()).toSeconds());
        return body;
    }

    private static Map<String, Object> describe(ReadinessProbe.Check check) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("name", check.name());
        described.put("status", check.status().name().toLowerCase(java.util.Locale.ROOT));
        described.put("latencyMs", check.latencyMs());
        return described;
    }
}
