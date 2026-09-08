// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.oidc;

import com.td.czghagent.domain.model.DeployStage;
import com.td.czghagent.domain.model.PlatformClaims;
import com.td.czghagent.domain.model.RpSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1 登录回路里那些「错了不会崩」的判定。
 *
 * <p>这一组保护的全是安静失败的路径：开放重定向不会报错，它只是把用户送到别处；
 * 裸角色比对不会报错，它只是永远返回 false；mock 在部署态启动不会报错，
 * 它只是供应编造的身份。
 */
class OidcContractTest {

    // ── PKCE ────────────────────────────────────────────────────────────────

    @Test
    void pkceChallengeIsTheS256DigestOfTheVerifier() {
        // RFC 7636 附录 B 的官方示例向量。用它而不是「自己算一遍再断言相等」，
        // 后者只能证明实现自洽，证明不了它与 IdP 对得上。
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        assertThat(Pkce.challengeOf(verifier))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void pkceOnlyOffersS256() {
        assertThat(Pkce.METHOD)
                .as("plain 方式把 verifier 原样发出去，等于没有保护；"
                        + "把它留成一个可选项，早晚有人在排障时切过去")
                .isEqualTo("S256");
    }

    @Test
    void everyVerifierIsDistinct() {
        assertThat(Pkce.createVerifier()).isNotEqualTo(Pkce.createVerifier());
        assertThat(Pkce.createOpaqueValue()).isNotEqualTo(Pkce.createOpaqueValue());
    }

    // ── 开放重定向 ──────────────────────────────────────────────────────────

    @Test
    void returnToAcceptsOnlyInSitePaths() {
        assertThat(ReturnTo.sanitize("/planner/bids")).isEqualTo("/planner/bids");
        assertThat(ReturnTo.sanitize("/planner/bids?tab=1#top")).isEqualTo("/planner/bids?tab=1#top");
    }

    /**
     * 这一组每一条都是真实的绕过手法。
     *
     * <p>判据必须是白名单：试图枚举「哪些形式是危险的」必然漏，
     * 而漏掉的那一条不会报错——用户在真实的登录页认证完，被送到钓鱼站，
     * 地址栏全程可信。
     */
    @Test
    void returnToRejectsEveryKnownOffSiteForm() {
        assertThat(ReturnTo.sanitize("//evil.example")).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize("/\\evil.example")).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize("https://evil.example")).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize("javascript:alert(1)")).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize("planner/bids")).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize("/ok\r\nSet-Cookie: x=1")).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize(null)).isEqualTo(ReturnTo.DEFAULT);
        assertThat(ReturnTo.sanitize("   ")).isEqualTo(ReturnTo.DEFAULT);
    }

    // ── 阶段守卫 ────────────────────────────────────────────────────────────

    /**
     * 拼错的部署阶段按<strong>最严格</strong>处理，不回落到 LOCAL。
     *
     * <p>回落的后果是「服务带着编造的数据安静地跑起来」；
     * 从严的后果是「服务拒绝用 mock 启动」。后者会被立刻发现。
     */
    @Test
    void unknownDeployStageIsTreatedAsProduction() {
        assertThat(DeployStage.parse("prod")).isEqualTo(DeployStage.PRODUCTION);
        assertThat(DeployStage.parse("产线")).isEqualTo(DeployStage.PRODUCTION);
        assertThat(DeployStage.parse("typo-here")).isEqualTo(DeployStage.PRODUCTION);
        assertThat(DeployStage.parse(null)).isEqualTo(DeployStage.LOCAL);
        assertThat(DeployStage.parse("")).isEqualTo(DeployStage.LOCAL);
    }

    @Test
    void betaCountsAsDeployed() {
        assertThat(DeployStage.BETA.isDeployed())
                .as("beta 同样面向真实用户，一份编造的权益在那里造成的困惑不比生产少")
                .isTrue();
        assertThat(DeployStage.LOCAL.isDeployed()).isFalse();
    }

    // ── 平台声明 ────────────────────────────────────────────────────────────

    /**
     * 角色是带 scope 前缀的串，裸比对必然漏。
     *
     * <p>这条是 vxtpl 的实测教训：{@code roles} 里装的是 {@code workspace:owner}
     * 而不是 {@code owner}，按后者比对的代码会永远返回 false，
     * 表现为「明明是所有者却没有权限」。
     */
    @Test
    void roleMatchingRequiresTheScopePrefix() {
        PlatformClaims claims = claimsWithRoles(List.of("workspace:owner", "org:member"));

        assertThat(claims.hasRole("workspace", "owner")).isTrue();
        assertThat(claims.hasRole("org", "member")).isTrue();
        assertThat(claims.hasRole("workspace", "member")).isFalse();
        assertThat(claims.hasRole("org", "owner")).isFalse();
    }

    @Test
    void missingWorkspaceYieldsNoTenantRatherThanADefaultOne() {
        PlatformClaims claims = new PlatformClaims(
                "sub-1", "张三", null, null, null, null, List.of());

        assertThat(claims.tenant())
                .as("兜底到某个默认工作空间的表现是「他看到了别人的数据」")
                .isNull();
        assertThat(claims.toCurrentUser().tenant()).isNull();
    }

    @Test
    void claimsProjectIntoTheCallerIdentity() {
        PlatformClaims claims = claimsWithRoles(List.of("workspace:owner"));

        assertThat(claims.toCurrentUser().roleCode()).isEqualTo("ADMIN");
        assertThat(claimsWithRoles(List.of()).toCurrentUser().roleCode()).isEqualTo("PLANNER");
    }

    // ── 会话续期时点 ────────────────────────────────────────────────────────

    /**
     * 提前一分钟换票。
     *
     * <p>不等到过期才换：一次调用可能在票有效时开始、在票过期后才到达被调方，
     * 而那种失败只在慢请求上偶发，最难复现。
     */
    @Test
    void sessionRefreshesBeforeTheAccessTokenActuallyExpires() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 12, 0, 0);

        assertThat(sessionExpiringAt(now.plusSeconds(120)).needsRefresh(now)).isFalse();
        assertThat(sessionExpiringAt(now.plusSeconds(30)).needsRefresh(now)).isTrue();
        assertThat(sessionExpiringAt(now.minusSeconds(1)).needsRefresh(now)).isTrue();
    }

    private static PlatformClaims claimsWithRoles(List<String> roles) {
        return new PlatformClaims(
                "sub-1", "张三", "z@example.com", null,
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222", roles);
    }

    private static RpSession sessionExpiringAt(LocalDateTime accessExpiresAt) {
        return new RpSession(
                "session-1", "sub-1", "张三", null, null, null, "",
                "access", "refresh", accessExpiresAt, accessExpiresAt.plusHours(12));
    }
}
