// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.domain.port.S2STokenMinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 阶段守卫：决定用真实身份服务还是 mock。
 *
 * <p>产品需要在<strong>凭证发放之前</strong>就能起栈——首次部署、本地开发、CI 都如此。
 * 但部署态让 mock 顶替真实身份是事故：它会供应编造的用户与工作空间，
 * 而界面看起来完全正常。
 *
 * <p>所以规则不是「记得配好」，是：<strong>部署态下 mock 拒绝启动</strong>。
 * 逃生口存在（首次部署确实可能早于凭证），但它<strong>自报家门</strong>——
 * 启动时告警，并由 {@code /api/status} 如实标注。静默兜底不存在。
 */
@Configuration
public class OidcGatewayConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcGatewayConfiguration.class);

    /**
     * S2S 换票器。
     *
     * <p>与身份网关共用同一份配置判据：能不能登录和能不能换票，取决于同一对
     * client 凭据。分开判会得到「登录用真的、换票用替身」这种半真半假的状态，
     * 而它在界面上完全看不出来。
     */
    @Bean
    public S2STokenMinter s2sTokenMinter(RestClient.Builder builder, OidcProperties properties,
                                         OidcDiscovery discovery) {
        if (properties.isConfigured()) {
            return new PlatformS2STokenMinter(builder, properties, discovery);
        }
        LOGGER.info("S2S 换票未配置，使用替身——它铸的票在任何真实被调方那里都会被拒");
        return new MockS2STokenMinter();
    }

    @Bean
    public OidcGateway oidcGateway(OidcProperties properties, OidcDiscovery discovery,
                                   OidcTokenClient tokenClient, IdTokenVerifier verifier,
                                   LogoutTokenVerifier logoutTokenVerifier,
                                   @Value("${app.allow-mock-on-deploy:false}") boolean allowMock) {
        if (properties.isConfigured()) {
            LOGGER.info("OIDC RP 使用平台身份服务：issuer={} client={}",
                    properties.issuer(), properties.clientId());
            return new PlatformOidcGateway(
                    properties, discovery, tokenClient, verifier, logoutTokenVerifier);
        }
        if (properties.deployStage().isDeployed() && !allowMock) {
            // 拒绝启动，而不是降级后继续。一个供应编造身份的部署，
            // 比一个起不来的部署危险得多——后者会被立刻发现。
            throw new IllegalStateException(
                    "部署阶段 " + properties.deployStage() + " 缺少 OIDC 配置，拒绝以 mock 身份启动。"
                            + " 补齐 app.oidc.* 或显式设置 app.allow-mock-on-deploy=true（会自报降级）");
        }
        if (properties.deployStage().isDeployed()) {
            LOGGER.warn("!!! 部署阶段 {} 正在使用 MOCK 身份服务：所有登录都是编造的。"
                    + " 这是显式开启的降级，/api/status 会如实标注。", properties.deployStage());
        } else {
            LOGGER.info("OIDC RP 未配置，本地使用 mock 身份服务");
        }
        return new MockOidcGateway(properties);
    }
}
