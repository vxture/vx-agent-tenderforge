// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
package com.td.czghagent.domain.model;

/**
 * 平台控制台里、本产品会把用户送去的地址。
 *
 * <p>资料（显示名、头像）归平台 IdP，本产品只读；要改只能去控制台。地址只在这里由
 * {@code app.platform.console-url} 拼出——前端另存一份就是第二个来源，
 * 控制台换域名时那一份一定会被忘掉。
 */
public final class ConsoleLinks {

    /** 控制台的个人资料页（平台 console 导航里的「个人资料」）。 */
    public static final String PROFILE_PATH = "/profile";

    private ConsoleLinks() {
    }

    /**
     * 个人资料页的绝对地址。
     *
     * <p>未配置控制台地址时返回 {@code null}：界面据此不渲染入口，
     * 而不是拼出一个指向本站自己的相对链接。
     */
    public static String profile(String consoleBaseUrl) {
        if (consoleBaseUrl == null || consoleBaseUrl.isBlank()) {
            return null;
        }
        return consoleBaseUrl.trim().replaceAll("/+$", "") + PROFILE_PATH;
    }
}
