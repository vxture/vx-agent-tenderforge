// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.DeployStage;
import com.td.czghagent.domain.port.EntitlementResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 权益解析器的阶段守卫，与身份网关同一条纪律。
 *
 * <p>部署态用替身供应<strong>编造的权益</strong>比编造的身份更隐蔽：界面一切正常，
 * 只是每个人都拿到了完整能力。所以同样是拒绝启动，而不是降级后继续。
 */
@Configuration
public class EntitlementResolverConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EntitlementResolverConfiguration.class);

    @Bean
    public EntitlementResolver entitlementResolver(
            RestClient.Builder builder,
            @Value("${app.platform.api-url:}") String apiUrl,
            @Value("${app.platform.internal-auth-token:}") String internalAuthToken,
            @Value("${app.deploy-stage:local}") String deployStage,
            @Value("${app.allow-mock-on-deploy:false}") boolean allowMock,
            @Value("${app.platform.mock-tier:}") String mockTier,
            @Value("${app.platform.mock-status:}") String mockStatus,
            @Value("${app.platform.mock-bundled:false}") boolean mockBundled
    ) {
        if (!apiUrl.isBlank() && !internalAuthToken.isBlank()) {
            LOGGER.info("C2 权益使用平台接口：{}", apiUrl);
            return new PlatformEntitlementResolver(builder, apiUrl, internalAuthToken);
        }
        DeployStage stage = DeployStage.parse(deployStage);
        if (stage.isDeployed() && !allowMock) {
            throw new IllegalStateException(
                    "部署阶段 " + stage + " 缺少 PLATFORM_API_URL / PLATFORM_INTERNAL_AUTH_TOKEN，"
                            + "拒绝以编造的权益启动。补齐配置或显式设置"
                            + " app.allow-mock-on-deploy=true（会自报降级）");
        }
        if (stage.isDeployed()) {
            LOGGER.warn("!!! 部署阶段 {} 正在使用 MOCK 权益：每个人拿到的能力都是编造的。"
                    + " 这是显式开启的降级，/api/status 会如实标注。", stage);
        } else {
            LOGGER.info("C2 权益未配置，本地使用替身（tier={}）",
                    mockTier.isBlank() ? "pro" : mockTier);
        }
        return new MockEntitlementResolver(mockTier, mockStatus, mockBundled);
    }
}
