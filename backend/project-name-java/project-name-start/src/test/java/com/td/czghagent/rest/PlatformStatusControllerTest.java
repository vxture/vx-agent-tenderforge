// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.rest;

import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.model.TenantScope;
import com.td.czghagent.domain.model.Entitlement;
import com.td.czghagent.domain.port.EntitlementResolver;
import com.td.czghagent.domain.port.OidcGateway;
import com.td.czghagent.domain.port.UsageConsumeClient;
import com.td.czghagent.domain.repository.UsageBufferRepository;
import com.td.czghagent.domain.port.S2STokenMinter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自证端点。
 *
 * <p>它最容易犯的错是<strong>把密钥报出去</strong>，而那种错不会崩——
 * 一个泄露 client_secret 的自证端点，本身就是它要证明的那类问题。
 * 所以这里的第一条断言不是「报得对不对」，是「有没有把不该说的说出去」。
 */
class PlatformStatusControllerTest {

    private static final String SECRET = "super-secret-value";

    /**
     * 密钥确实配了、确实没被报出去。
     *
     * <p>用一个<b>配齐了密钥</b>的夹具，而不是空配置——空配置下这条断言恒真，
     * 什么都证明不了。
     */
    @Test
    void neverEchoesAnySecretIntoTheResponse() {
        Map<String, Object> body = controllerWithSecretsConfigured().status();

        assertThat(channelState(body, "C3-down"))
                .as("先确认这个夹具真的配了密钥，否则下一条断言是空的")
                .isEqualTo("configured");
        assertThat(body.toString())
                .as("issuer 可以出现，密钥不行")
                .doesNotContain(SECRET);
    }

    /**
     * 用替身时必须自报降级。
     *
     * <p>这一位为真时，界面上看到的身份、权益、模型输出都可能是编造的。
     * 不报的后果是：有人拿着一份 mock 数据做完了验收。
     */
    @Test
    void reportsDegradedWheneverAnIdentityStandInIsInUse() {
        assertThat(controller(true, false).status().get("degraded")).isEqualTo(true);
        assertThat(controller(false, false, "local", false, false).status().get("degraded"))
                .isEqualTo(false);
    }

    /**
     * 降级是<strong>三个通道的并集</strong>，不是只看身份。
     *
     * <p>只看身份的话，一套真身份配上替身权益会报 {@code degraded=false}——
     * 而那时每个人拿到的能力都是编造的、用量一笔也没入账。
     * 这正是「拿着一份 mock 数据做完验收」最可能的发生方式：
     * 登录是真的，所以没人怀疑后面。
     */
    @Test
    void countsEveryStandInTowardsDegradedNotJustIdentity() {
        assertThat(controller(false, true, "local", true, false).status().get("degraded"))
                .as("权益是替身").isEqualTo(true);
        assertThat(controller(false, true, "local", false, true).status().get("degraded"))
                .as("用量上报是替身").isEqualTo(true);
    }

    /**
     * C2 与 C3-up 的状态问的是<strong>对象自己</strong>，不是 URL 配没配。
     *
     * <p>两者不总是一致：配了 URL 却漏了令牌时装配会落到替身上，
     * 而只看 URL 会报「已配置」——恰好是最需要被看见的那种半配好状态。
     */
    @Test
    void asksTheResolverItselfRatherThanLookingAtTheUrl() {
        Map<String, Object> body = controller(false, true, "production", true, true).status();

        assertThat(channelState(body, "C2")).isEqualTo("degraded_mock");
        assertThat(channelState(body, "C3-up")).isEqualTo("degraded_mock");
        assertThat(channelState(controller(false, true, "local", true, true).status(), "C2"))
                .as("本地用替身是正常的，与部署态要区分开").isEqualTo("mock");
        assertThat(channelState(controller(false, true, "local", false, false).status(), "C3-up"))
                .isEqualTo("active");
    }

    /**
     * C1 的状态是四态，不是布尔。
     *
     * <p>「关着」「开着但缺配置」「在用替身」「已接通」对排障是四件不同的事；
     * 一个布尔只能表达其中一刀，剩下的差别就得靠猜。
     */
    @Test
    void distinguishesTheFourIdentityStates() {
        assertThat(channelState(controller(true, false, "local").status(), "C1")).isEqualTo("mock");
        assertThat(channelState(controller(true, false, "production").status(), "C1"))
                .as("部署态用替身是一种更严重的状态，必须与本地开发区分")
                .isEqualTo("degraded_mock");
        assertThat(channelState(controller(false, true, "production").status(), "C1")).isEqualTo("active");
        assertThat(channelState(controller(false, false, "production").status(), "C1"))
                .isEqualTo("configured_but_disabled");
    }

    /**
     * 未配置的通道如实报 not_configured。
     *
     * <p>这个端点存在的全部意义就是这一条：把「其实一直没配」在上线前暴露出来，
     * 而不是等对量时才发现。
     */
    @Test
    void reportsEveryUnconfiguredChannelHonestly() {
        Map<String, Object> body = controller(true, false).status();

        assertThat(channelState(body, "C2")).isEqualTo("mock");
        assertThat(channelState(body, "C3-up")).isEqualTo("mock");
        assertThat(channelState(body, "C3-down")).isEqualTo("not_configured");
        assertThat(channelState(body, "atlas")).isEqualTo("not_configured");
    }

    /** Atlas 未接通时明说这是已知的契约违规，不含糊成「待接入」。 */
    @Test
    void namesTheAtlasGapAsAKnownContractViolation() {
        assertThat(channelDetail(controller(true, false).status(), "atlas"))
                .contains("契约违规");
    }

    @Test
    void alwaysReportsTheProductCodeAndDeployStage() {
        Map<String, Object> body = controller(true, false, "beta").status();

        assertThat(body.get("product")).isEqualTo("tenderforge");
        assertThat(body.get("deployStage")).isEqualTo("beta");
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private static PlatformStatusController controller(boolean mock, boolean oidcEnabled) {
        return controller(mock, oidcEnabled, "local");
    }

    /** 四条平台通道全部未配置——这是当前部署的真实形态。 */
    private static PlatformStatusController controller(
            boolean mock, boolean oidcEnabled, String stage) {
        return controller(mock, oidcEnabled, stage, true, true);
    }

    private static PlatformStatusController controller(
            boolean mock, boolean oidcEnabled, String stage,
            boolean entitlementMock, boolean usageMock) {
        return new PlatformStatusController(
                new StubGateway(mock), new StubMinter(false),
                new StubResolver(entitlementMock), new StubConsume(usageMock),
                "v1.2.3", stage, oidcEnabled,
                "https://accounts.vxture.com", false,
                "", "", "");
    }

    private static PlatformStatusController controllerWithSecretsConfigured() {
        return new PlatformStatusController(
                new StubGateway(false), new StubMinter(true),
                new StubResolver(false), new StubConsume(false),
                "v1.2.3", "production", true,
                "https://accounts.vxture.com", false,
                "http://platform-api.internal", SECRET, "http://atlas.internal");
    }

    private record StubResolver(boolean mock) implements EntitlementResolver {
        @Override
        public Entitlement resolve(String workspaceId) {
            return Entitlement.none(workspaceId, "tenderforge");
        }

        @Override
        public void invalidate(String workspaceId) {
        }

        @Override
        public boolean isMock() {
            return mock;
        }
    }

    private record StubConsume(boolean mock) implements UsageConsumeClient {
        @Override
        public Outcome consume(UsageBufferRepository.BufferedUsage usage) {
            return new Outcome(200, false, false, "evt", null);
        }

        @Override
        public boolean isMock() {
            return mock;
        }
    }

    @SuppressWarnings("unchecked")
    private static String channelState(Map<String, Object> body, String code) {
        return (String) channel(body, code).get("state");
    }

    @SuppressWarnings("unchecked")
    private static String channelDetail(Map<String, Object> body, String code) {
        return (String) channel(body, code).get("detail");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> channel(Map<String, Object> body, String code) {
        return ((List<Map<String, Object>>) body.get("channels")).stream()
                .filter(item -> code.equals(item.get("code")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("自证端点漏报了通道 " + code));
    }

    /** C1b 的状态跟着换票器走，与身份网关是两条独立的判据。 */
    @Test
    void reportsTheS2sChannelSeparatelyFromIdentity() {
        assertThat(channelState(controller(true, false).status(), "C1b"))
                .isEqualTo("not_configured");
        assertThat(channelState(controllerWithSecretsConfigured().status(), "C1b"))
                .isEqualTo("configured");
    }

    private record StubMinter(boolean configured) implements S2STokenMinter {
        @Override
        public S2SToken onBehalfOf(String audience, String userAccessToken) {
            throw new UnsupportedOperationException("本用例不铸票");
        }

        @Override
        public S2SToken forService(String audience, TenantScope tenant) {
            throw new UnsupportedOperationException("本用例不铸票");
        }

        @Override
        public void invalidate(S2SToken token) {
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }

    private record StubGateway(boolean mock) implements OidcGateway {
        @Override
        public String authorizationUrl(String state, String nonce, String codeChallenge) {
            return "";
        }

        @Override
        public Tokens exchangeCode(String code, String codeVerifier) {
            return null;
        }

        @Override
        public Tokens refresh(String refreshToken) {
            return null;
        }

        @Override
        public PlatformClaims readClaims(Tokens tokens, String expectedNonce) {
            return null;
        }

        @Override
        public String subjectOfLogoutToken(String logoutToken) {
            throw new UnsupportedOperationException("本用例不涉及反向登出");
        }

        @Override
        public long sessionSeconds() {
            return 0;
        }

        @Override
        public boolean isMock() {
            return mock;
        }
    }
}
