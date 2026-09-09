// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.port;

import com.td.czghagent.domain.model.S2SToken;
import com.td.czghagent.domain.model.TenantScope;

/**
 * 铸造面向其他产品的短时凭证（产品接入通则 C1b）。
 *
 * <p>凭据<strong>就是 C1 的那对 OIDC client</strong>——没有另一份 S2S 凭据要申请。
 * 这一点值得写下来，因为「去要一个 S2S 密钥」是接入时最常见的一次白跑。
 *
 * <p>两种模式做成两个方法而不是一个带枚举参数的方法：它们需要的上下文不同
 * （一个要用户的票，一个要工作空间），合成一个签名会得到两个互斥的可选参数，
 * 而那种签名允许「两个都传」和「两个都不传」这两种没有意义的调用。
 */
public interface S2STokenMinter {

    /**
     * 用户在场：拿用户的 access token 换一张面向 {@code audience} 的票。
     *
     * <p>平台从 {@code subject_token} 解出上下文，所以调用方<strong>无法声称</strong>
     * 一个它没有会话的工作空间。这也是「token 永不下发浏览器」的第二个理由——
     * 它是换票的原料。
     */
    S2SToken onBehalfOf(String audience, String userAccessToken);

    /**
     * 无用户在场：显式声明工作空间。
     *
     * <p>平台铸币时校验本产品是否真的覆盖该工作空间；未覆盖时拒绝，
     * 而那正是一个新登记产品最先撞上的失败。
     */
    S2SToken forService(String audience, TenantScope tenant);

    /**
     * 丢弃一张缓存的票。
     *
     * <p>被调方回 401 之后调用它，让下一次重新铸而不是重放同一张。
     * 不做这件事的表现是：票在被调方侧失效后，调用方还会用满剩下的缓存时间，
     * 期间每一次调用都 401。
     */
    void invalidate(S2SToken token);

    /** 是否具备铸币能力。{@code /api/status} 用它如实回答这条通道配没配。 */
    boolean isConfigured();
}
