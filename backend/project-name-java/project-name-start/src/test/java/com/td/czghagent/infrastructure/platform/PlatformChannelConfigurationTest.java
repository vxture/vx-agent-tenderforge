// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.port.UsageConsumeClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C2 / C3 上行通道的阶段守卫。
 *
 * <p>共享口令退役后，这两条通道要的只剩「平台地址」与「能换票的 OIDC client」。
 * 缺任何一项，部署态都必须照旧拒绝启动——否则就是替身在供应编造的权益、
 * 吞掉所有用量，而界面一切正常。
 */
class PlatformChannelConfigurationTest {

    private static final String PLATFORM = "http://platform.internal:8080";

    private final RecordingPlatformMinter minter = new RecordingPlatformMinter();

    @Test
    void usesThePlatformOnceTheAddressAndTheOidcClientAreBothThere() {
        assertThat(entitlements(PLATFORM, "production"))
                .isInstanceOf(PlatformEntitlementResolver.class);
        assertThat(usage(PLATFORM, "production"))
                .isInstanceOf(PlatformUsageConsumeClient.class);
    }

    @Test
    void refusesToStartDeployedWithoutAnOidcClientToMintWith() {
        minter.configured = false;

        assertThatThrownBy(() -> entitlements(PLATFORM, "production"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OIDC");
        assertThatThrownBy(() -> usage(PLATFORM, "production"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OIDC");
    }

    @Test
    void refusesToStartDeployedWithoutThePlatformAddress() {
        assertThatThrownBy(() -> entitlements("", "production"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PLATFORM_API_URL");
        assertThatThrownBy(() -> usage("", "production"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PLATFORM_API_URL");
    }

    @Test
    void fallsBackToTheMockOnlyLocally() {
        minter.configured = false;

        assertThat(entitlements("", "local").isMock()).isTrue();
        assertThat(usage("", "local").isMock()).isTrue();
    }

    private EntitlementResolver entitlements(String apiUrl, String stage) {
        return new EntitlementResolverConfiguration().entitlementResolver(
                RestClient.builder(), apiUrl, minter, stage, false, "", "", false);
    }

    private UsageConsumeClient usage(String apiUrl, String stage) {
        return new UsageReportingConfiguration().usageConsumeClient(
                RestClient.builder(), apiUrl, minter, stage, false, false);
    }
}
