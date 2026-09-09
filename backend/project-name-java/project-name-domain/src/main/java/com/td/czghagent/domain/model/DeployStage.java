// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.model;

import java.util.Locale;

/**
 * 部署阶段，决定 mock 实现能不能启动。
 *
 * <p>产品需要在<strong>凭证发放之前</strong>就能起栈——首次部署、本地开发、CI 都如此。
 * 但在部署态让 mock 顶替真实解析器是事故：它会供应编造的权益与身份，
 * 而界面看起来完全正常。
 *
 * <p>这个矛盾用<strong>阶段守卫</strong>解决，不是用自觉：部署态下 mock 直接拒绝启动。
 * 逃生口存在（首次部署确实可能早于凭证），但它<strong>自报家门</strong>——
 * 日志告警加上 {@code /api/status} 如实标注降级。静默兜底不存在。
 *
 * <p>取值由镜像构建时从 git ref 注入，<strong>不在 .env 里手写</strong>：
 * 允许部署方覆盖它，等于允许绕过这道守卫，那这道守卫就不存在了。
 */
public enum DeployStage {

    /** 本地开发。mock 是默认路径。 */
    LOCAL,
    /** 预发。已经是部署态。 */
    BETA,
    /** 生产。 */
    PRODUCTION;

    /**
     * 是否属于「部署态」。
     *
     * <p>beta 也算：它同样面向真实用户，一份编造的权益在那里造成的困惑不比生产少。
     */
    public boolean isDeployed() {
        return this != LOCAL;
    }

    /**
     * 解析配置值。
     *
     * <p>无法识别的取值<strong>按最严格处理</strong>（视为 PRODUCTION），而不是回落到 LOCAL：
     * 拼错一个部署阶段的后果应该是「服务拒绝用 mock 启动」，
     * 而不是「服务带着编造的数据安静地跑起来」。
     */
    public static DeployStage parse(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "local", "dev", "development", "test" -> LOCAL;
            case "beta", "staging" -> BETA;
            default -> PRODUCTION;
        };
    }
}
