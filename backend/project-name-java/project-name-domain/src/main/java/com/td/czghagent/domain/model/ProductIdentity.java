// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

/**
 * 产品在平台上的身份，全仓唯一真源。
 *
 * <p>平台的每一条通道都用它：C2 的 {@code ?product=}、C3 上报的 {@code product}、
 * S2S 换票后 {@code act.sub} 应等于的值、被调方验票时 {@code aud} 应等于的值。
 *
 * <p><strong>永远不要从 {@code OIDC_CLIENT_ID} 反推产品码。</strong>
 * beta 环境的 client 是 {@code tenderforge-beta}，而产品码仍是 {@code tenderforge}
 * ——非生产栈上两者必然分叉，而分叉的表现是「用量报到了一个不存在的产品上」，
 * 平台侧返 {@code unknown_product}，本地一切看起来正常。
 *
 * <p><strong>它是源码字面量，不是环境变量。</strong>环境变量意味着同一份镜像可以
 * 冒充另一个产品上报用量；而产品码属于「这份代码是谁」，不属于「这次部署在哪」。
 * 换产品走一次性重命名脚本，那是一次会进 diff、会被评审看见的显式动作。
 */
public final class ProductIdentity {

    /** 平台登记的产品码。匹配 {@code ^[a-z][a-z0-9_-]{0,31}$}。 */
    public static final String PRODUCT_CODE = "tenderforge";

    /**
     * 换票时声明的受众常量，平台自身的 {@code /platform/*} 与 {@code /usage/*} 用它。
     * 调用 Atlas / Runos 时受众是对方的产品码，不是这个值。
     */
    public static final String PLATFORM_AUDIENCE = "vxture";

    private ProductIdentity() {
    }
}
