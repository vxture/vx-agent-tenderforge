// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.DeployStage;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * C3 上行的装配与阶段守卫。
 *
 * <p>与身份、权益同一条纪律：部署态不许用替身上报。用替身的表现比另外两个更安静
 * ——一切正常，只是<strong>什么都没记到账上</strong>，而账单要到月底才对不上。
 */
@Configuration
@EnableScheduling
public class UsageReportingConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(UsageReportingConfiguration.class);

    @Bean
    public UsageConsumeClient usageConsumeClient(
            RestClient.Builder builder,
            @Value("${app.platform.api-url:}") String apiUrl,
            @Value("${app.platform.internal-auth-token:}") String internalAuthToken,
            @Value("${app.deploy-stage:local}") String deployStage,
            @Value("${app.allow-mock-on-deploy:false}") boolean allowMock,
            @Value("${app.platform.mock-usage-gated:false}") boolean mockGated
    ) {
        if (!apiUrl.isBlank() && !internalAuthToken.isBlank()) {
            LOGGER.info("C3 用量上报使用平台接口：{}", apiUrl);
            return new PlatformUsageConsumeClient(builder, apiUrl, internalAuthToken);
        }
        DeployStage stage = DeployStage.parse(deployStage);
        if (stage.isDeployed() && !allowMock) {
            throw new IllegalStateException(
                    "部署阶段 " + stage + " 缺少 PLATFORM_API_URL / PLATFORM_INTERNAL_AUTH_TOKEN，"
                            + "拒绝在用量不入账的情况下启动。补齐配置或显式设置"
                            + " app.allow-mock-on-deploy=true（会自报降级）");
        }
        if (stage.isDeployed()) {
            LOGGER.warn("!!! 部署阶段 {} 正在使用 MOCK 用量上报：所有用量都没有入账。", stage);
        } else {
            LOGGER.info("C3 用量上报未配置，本地使用替身（gated={}）", mockGated);
        }
        return new MockUsageConsumeClient(mockGated);
    }

    @Bean
    public UsageFlushJob usageFlushJob(
            UsageBufferRepository buffer,
            UsageConsumeClient consumeClient,
            EntitlementResolver entitlements,
            @Value("${app.platform.usage-flush-batch-size:100}") int batchSize
    ) {
        return new UsageFlushJob(buffer, consumeClient, entitlements,
                batchSize, Clock.systemDefaultZone());
    }

    /**
     * 冲洗调度。
     *
     * <p>{@code fixedDelay} 而不是 {@code fixedRate}：一轮没跑完就再起一轮，
     * 只会让两轮互相抢同一批认领。间隔按上一轮<strong>结束</strong>算，
     * 慢的时候自然退让。
     *
     * <p>可以整个关掉（{@code app.platform.usage-flush-enabled=false}）。这不是给
     * 生产用的开关，是给<strong>测试</strong>用的：集成测试会起完整上下文，
     * 一个在后台跑着的冲洗任务会去认领测试刚写进去的行，让断言随机失败——
     * 而那种失败看起来像并发 bug，不像测试污染。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "app.platform.usage-flush-enabled", havingValue = "true", matchIfMissing = true)
    public UsageFlushScheduler usageFlushScheduler(UsageFlushJob job) {
        return new UsageFlushScheduler(job);
    }

    /** 调度壳子。与任务本体分开，好让测试直接驱动任务而不必起调度器。 */
    public static class UsageFlushScheduler {

        private final UsageFlushJob job;

        public UsageFlushScheduler(UsageFlushJob job) {
            this.job = job;
        }

        @Scheduled(
                initialDelayString = "${app.platform.usage-flush-initial-delay-ms:20000}",
                fixedDelayString = "${app.platform.usage-flush-interval-ms:15000}")
        public void flush() {
            job.flushOnce();
        }

        /** 每天清一次过了对账窗口的行。 */
        @Scheduled(
                initialDelayString = "${app.platform.usage-purge-initial-delay-ms:120000}",
                fixedDelayString = "${app.platform.usage-purge-interval-ms:86400000}")
        public void purge() {
            job.purge();
        }
    }
}
