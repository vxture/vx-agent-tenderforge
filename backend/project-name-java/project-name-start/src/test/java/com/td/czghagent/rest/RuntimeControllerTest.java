// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-10
package com.td.czghagent.rest;

import com.td.czghagent.domain.port.ReadinessProbe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存活/就绪的身份块。
 *
 * <p>这里守的<strong>不是「有没有值」，是「字段叫什么名字」</strong>。
 * 组织规范 025 §3 把字段名固定死，理由在规范 §1 写着：同一产品不同服务各写各的
 * （{@code sha} vs {@code gitSha}、{@code deployStage} vs {@code stage}），
 * 跨产品聚合就取不到值——而<strong>取不到值和「这个服务没部署」在聚合端长得一模一样</strong>。
 * 所以名字漂了必须红，哪怕内容完全正确。
 *
 * <p>第二件要守的是<strong>诚实兜底</strong>：规范 §6 把硬编 {@code version:"1.0.0"}、
 * {@code sha:"dev"} 列为禁止项。没注入构建信息时应当如实报 {@code unknown}，
 * 而不是编一个看起来像真的值。
 */
class RuntimeControllerTest {

    /** 规范 §3 的必填字段，一个都不能少、不能改名。 */
    private static final List<String> REQUIRED = List.of(
            "status", "service", "version", "gitSha", "stage", "buildTime", "time");

    private static RuntimeController controller(ReadinessProbe probe) {
        return new RuntimeController(probe, "v1.2.3", "8a03ec48",
                "2026-09-10T07:30:00Z", "production");
    }

    private static ReadinessProbe probeReturning(ReadinessProbe.Check... checks) {
        return () -> List.of(checks);
    }

    @Test
    void livenessCarriesEveryRequiredIdentityFieldUnderTheContractName() {
        Map<String, Object> body = controller(probeReturning()).health();

        assertThat(body).containsKeys(REQUIRED.toArray(String[]::new));
        assertThat(body)
                .containsEntry("status", "ok")
                .containsEntry("service", "tenderforge-api")
                .containsEntry("version", "v1.2.3")
                .containsEntry("gitSha", "8a03ec48")
                .containsEntry("stage", "production")
                .containsEntry("buildTime", "2026-09-10T07:30:00Z");
    }

    @Test
    void livenessStatusIsOkNotUp() {
        // 规范把 liveness 的 status 定为字面量 "ok"。此前本仓报的是 "UP"——
        // 值本身没错，但聚合端按 "ok" 判定，"UP" 会被读成非正常。
        assertThat(controller(probeReturning()).health()).containsEntry("status", "ok");
    }

    @Test
    void livenessTouchesNoDependency() {
        // 零依赖是硬要求（规范 §2 铁律 + 020 §4）：存活端点探依赖，
        // 会让一次数据库抖动把一个本来健康的容器重启掉。
        // 这里给一个「一被调用就炸」的探针——存活仍必须正常返回。
        ReadinessProbe exploding = () -> {
            throw new AssertionError("存活端点不该调用就绪探针");
        };
        assertThat(new RuntimeController(exploding, "v1", "sha", "t", "local").health())
                .containsEntry("status", "ok");
    }

    @Test
    void gitShaDropsTheImageTagPrefix() {
        // `sha-` 是<b>镜像 tag 的形态</b>，不是数据。带着它，聚合端拿到的值
        // 不能直接 `git show`。规范 §3 明确要求不带前缀。
        RuntimeController prefixed = new RuntimeController(
                probeReturning(), "v1", "sha-763c71c", "t", "production");
        assertThat(prefixed.health()).containsEntry("gitSha", "763c71c");
    }

    @Test
    void missingBuildInjectionFallsBackHonestlyInsteadOfInventingAValue() {
        // 未注入时的诚实兜底。规范 §6 禁止编造——宁可 unknown。
        RuntimeController bare = new RuntimeController(
                probeReturning(), "dev", "unknown", "unknown", "local");
        assertThat(bare.health())
                .containsEntry("version", "dev")
                .containsEntry("gitSha", "unknown")
                .containsEntry("buildTime", "unknown")
                .containsEntry("stage", "local");
    }

    @Test
    void timeIsTakenFreshOnEveryCallSoItProvesTheClockNotACachedString() throws Exception {
        RuntimeController subject = controller(probeReturning());
        String first = (String) subject.health().get("time");
        Thread.sleep(1100);
        String second = (String) subject.health().get("time");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void readinessCarriesTheSameIdentityBlockPlusChecks() {
        var body = controller(probeReturning(
                new ReadinessProbe.Check("db", ReadinessProbe.Status.UP, 3))).ready();

        assertThat(body.getStatusCode().value()).isEqualTo(200);
        assertThat(body.getBody()).containsKeys(REQUIRED.toArray(String[]::new));
        assertThat(body.getBody()).containsEntry("status", "ready");
        assertThat(body.getBody()).containsKey("checks");
    }

    @Test
    void readinessFailsWithFiveOhThreeAndSaysFailNotBlocked() {
        // 规范 §3：readiness 的 status ∈ ready|degraded|fail，fail → 503。
        // 此前本仓报的是 "blocked"，那个值不在值域里。
        var body = controller(probeReturning(
                new ReadinessProbe.Check("db", ReadinessProbe.Status.DOWN, 12))).ready();

        assertThat(body.getStatusCode().value()).isEqualTo(503);
        assertThat(body.getBody()).containsEntry("status", "fail");
    }

    @Test
    void readinessNeverLeaksTheRawFailureText() {
        // 依赖失败的异常文本里可能带内网主机名与连接串。探针是公开信息，
        // 只报 name/status/latencyMs 三样。
        var body = controller(probeReturning(
                new ReadinessProbe.Check("db", ReadinessProbe.Status.DOWN, 12))).ready();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) body.getBody().get("checks");
        assertThat(checks).hasSize(1);
        assertThat(checks.getFirst()).containsOnlyKeys("name", "status", "latencyMs");
    }
}
