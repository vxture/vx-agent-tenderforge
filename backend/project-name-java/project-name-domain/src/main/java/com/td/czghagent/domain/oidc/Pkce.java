// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.domain.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE（RFC 7636）的 S256 方式。
 *
 * <p>授权码流程即使有 client_secret 也要带 PKCE：secret 保护的是「谁在换票」，
 * PKCE 保护的是「这张码是不是发给我的那张」。授权码经由浏览器地址栏回来，
 * 中途被截获时，没有 verifier 的持有者换不出 token。
 *
 * <p><strong>只实现 S256，不实现 plain。</strong>plain 方式把 verifier 原样发出去，
 * 等于没有保护；把它作为一个可选项留在代码里，早晚会有人在排障时切过去。
 */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** RFC 7636 允许 43–128 字符；取上限，随机性没有理由省。 */
    private static final int VERIFIER_BYTES = 96;

    public static final String METHOD = "S256";

    private Pkce() {
    }

    /** 生成 verifier。它<strong>绝不下发浏览器</strong>，只存服务端。 */
    public static String createVerifier() {
        byte[] bytes = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 由 verifier 派生 challenge，随授权请求发给 IdP。 */
    public static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    /** 生成 state / nonce 这类一次性随机串。 */
    public static String createOpaqueValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
