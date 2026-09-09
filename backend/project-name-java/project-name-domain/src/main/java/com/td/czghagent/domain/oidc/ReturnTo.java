// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.oidc;

/**
 * 登录后回跳目标的白名单化。
 *
 * <p>开放重定向是登录回路上最经典的一处漏洞：攻击者把 {@code returnTo} 指向自己的域名，
 * 用户在<strong>真实的</strong>登录页完成认证后被送到钓鱼站，而地址栏全程可信。
 *
 * <p>所以判据是<strong>白名单而不是黑名单</strong>：只接受站内绝对路径，
 * 其余一律回落到首页。试图枚举「哪些形式是危险的」必然漏——
 * {@code //evil.com}、{@code /\evil.com}、{@code https:/evil.com} 都曾是绕过手法。
 */
public final class ReturnTo {

    public static final String DEFAULT = "/";

    private ReturnTo() {
    }

    /**
     * 规范化回跳目标；任何不是站内路径的取值都回落到首页。
     *
     * <p>回落而不是报错：用户点了一个带脏参数的链接，不该看到一个错误页面，
     * 他该正常登录并落到首页。
     */
    public static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT;
        }
        String value = candidate.trim();
        if (!value.startsWith("/")) {
            return DEFAULT;
        }
        // 「以 / 开头」不足以说明是站内：`//host` 是协议相对 URL，
        // `/\host` 被部分浏览器等同处理，两者都会离站。
        if (value.length() > 1 && (value.charAt(1) == '/' || value.charAt(1) == '\\')) {
            return DEFAULT;
        }
        // 控制字符可以把一个路径拆成两行，进而伪造出别的东西。
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < 0x20 || value.charAt(index) == 0x7f) {
                return DEFAULT;
            }
        }
        return value;
    }
}
