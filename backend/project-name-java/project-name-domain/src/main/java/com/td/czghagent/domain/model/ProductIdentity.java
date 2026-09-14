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

    /**
     * C3 下发（provisioning webhook）的<strong>对外</strong>接收路径。
     *
     * <p><strong>通则规定所有产品同一个路径</strong>，变的只有域名——本产品的完整地址是
     * {@code https://tenderforge.vxture.com/api/webhooks/vxture}。三段各答一问：
     * {@code api} 说明这是机器接口、不与前端路由抢地址；{@code webhooks} 说明入站、
     * 外部来源、必须验签；{@code vxture} 说明谁发的，将来接第二家时
     * {@code /api/webhooks/<对方>} 自然并列。<strong>版本不进路径</strong>——
     * URL 在平台侧按产品登记一次，把版本写进路径等于每次信封升级都要逐个产品改登记。
     *
     * <p><strong>它是常量而不是五处字面量，是因为这个路径曾经在本仓错过一次。</strong>
     * 它同时出现在控制器映射、会话豁免名单、集成测试、部署文档与 {@code .env.example}
     * 里；改了其中一处而漏掉会话豁免名单，表现是平台 POST 过来被登录过滤器挡掉，
     * 而平台那边只看见一个非 2xx、重试十次、然后放弃。
     * {@code scripts/guardrails/check_webhook_path.py} 守着这几处与本常量一致。
     *
     * <p>早期本仓取的是 {@code /provisioning/webhook}——那不是规范，是从 vxtpl
     * 抄来的。vxtpl 与 yucer 的路由注释都写着「product_200 section 4」，
     * 而那一节<strong>通篇没有规定过路径</strong>，两边各自造了一个又互相印证。
     */
    public static final String PLATFORM_WEBHOOK_PATH = "/api/webhooks/vxture";

    private ProductIdentity() {
    }
}
