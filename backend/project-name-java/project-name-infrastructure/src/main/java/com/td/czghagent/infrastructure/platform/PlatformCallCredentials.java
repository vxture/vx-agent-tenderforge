// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14
package com.td.czghagent.infrastructure.platform;

import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.port.S2STokenMinter;
import org.springframework.http.HttpHeaders;

/**
 * C2 权益 / C3 用量两条上行通道的凭证：一张 {@code aud=vxture} 的 S2S 票。
 *
 * <p><strong>取代共享口令 {@code x-vxture-internal-auth}。</strong>那把口令不带身份——
 * 平台无从知道是谁在问、问的工作空间是不是它的；通则已把它列为退役凭证。
 * 票上的 {@code act.sub} 是本产品，{@code workspace_id} 在铸币时已由平台核对过
 * 「本产品确实覆盖这个工作空间」，平台据此把请求绑到票上的工作空间。
 *
 * <p>凭据仍是 C1 的那对 OIDC client——接一个新智能体只填值，不多申请一份密钥。
 *
 * <p>用 service 模式而不是 OBO：冲洗任务里没有用户在场。两条通道用同一种票，
 * 就只有一种失败形状要理解。
 */
public final class PlatformCallCredentials {

    /** 平台面 S2S 票的受众。跨仓契约值，与平台侧 PLATFORM_S2S_AUDIENCE 同值。 */
    public static final String AUDIENCE = "vxture";

    private final S2STokenMinter minter;

    public PlatformCallCredentials(S2STokenMinter minter) {
        this.minter = minter;
    }

    /**
     * 为一个工作空间铸票；铸不出时<strong>抛出</strong>，由调用方决定降级形状
     * （权益 fail-closed，用量留在缓冲区）。
     *
     * <p>平台答 {@code invalid_target} 意味着本产品在该工作空间没有有效的订阅或开通。
     */
    public S2SToken mint(String workspaceId) {
        return minter.forWorkspace(AUDIENCE, workspaceId);
    }

    /** 平台回 401 之后作废这张票，下一次重铸而不是重放。 */
    public void invalidate(S2SToken token) {
        minter.invalidate(token);
    }

    public boolean isConfigured() {
        return minter.isConfigured();
    }

    public static void apply(HttpHeaders headers, S2SToken token) {
        headers.setBearerAuth(token.value());
    }
}
