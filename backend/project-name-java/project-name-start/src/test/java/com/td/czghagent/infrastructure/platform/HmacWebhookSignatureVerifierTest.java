// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-09
package com.td.czghagent.infrastructure.platform;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhook 验签。
 *
 * <p>这一组保护的是本产品在平台面前的<strong>身份边界</strong>：
 * 验松了，任何人都能往里发开通事件；验紧了（比如把字节改写掉），
 * 平台每一条投递都被拒，而重试会一直打到有人去看日志为止。
 */
class HmacWebhookSignatureVerifierTest {

    private static final String SECRET = "whsec_current";
    private static final String NEXT_SECRET = "whsec_next";
    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");

    private final HmacWebhookSignatureVerifier verifier = verifierWith(SECRET, NEXT_SECRET);

    // ── 正常路径 ────────────────────────────────────────────────────────────

    @Test
    void acceptsASignatureItJustProducedTheInputsFor() {
        byte[] body = "{\"type\":\"tenant.provisioned\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(body, header(NOW.getEpochSecond(), SECRET, body))).isTrue();
    }

    /**
     * 轮换期间新旧密钥都认。
     *
     * <p>只认当前密钥的话，轮换那一刻所有投递会同时开始失败——
     * 而轮换是一次计划内的、本该无感的操作。
     */
    @Test
    void acceptsEitherSecretWhileRotating() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(body, header(NOW.getEpochSecond(), SECRET, body))).isTrue();
        assertThat(verifier.verify(body, header(NOW.getEpochSecond(), NEXT_SECRET, body)))
                .as("平台可能已经切到新密钥，而我们还没撤掉旧的").isTrue();
    }

    /** 一个头里可以带多个 v1，任一命中即通过。 */
    @Test
    void acceptsWhenAnyOfSeveralCandidatesMatches() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String good = hmac(SECRET, NOW.getEpochSecond(), body);

        assertThat(verifier.verify(body, "t=" + NOW.getEpochSecond()
                + ",v1=" + "0".repeat(64) + ",v1=" + good)).isTrue();
    }

    /** 十六进制大小写不携带信息，不该成为拒绝的理由。 */
    @Test
    void doesNotCareAboutHexCase() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String upper = hmac(SECRET, NOW.getEpochSecond(), body).toUpperCase(java.util.Locale.ROOT);

        assertThat(verifier.verify(body, "t=" + NOW.getEpochSecond() + ",v1=" + upper)).isTrue();
    }

    /** 不认识的键（比如将来的 v2）要忽略，不能因为看见它就整个拒绝。 */
    @Test
    void toleratesSignatureSchemesItHasNeverSeen() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String good = hmac(SECRET, NOW.getEpochSecond(), body);

        assertThat(verifier.verify(body,
                "t=" + NOW.getEpochSecond() + ",v1=" + good + ",v2=whatever")).isTrue();
    }

    // ── 字节精确 ────────────────────────────────────────────────────────────

    /**
     * 非 ASCII 内容照样验得过。
     *
     * <p>这条盯的是「用 String 接收再 getBytes 拿去算 HMAC」那个 bug：
     * 全英文的 payload 一切正常，直到某个租户名里出现一个中文字符，
     * 那条投递开始永远验签失败、被平台无限重试，而日志里说不出任何原因。
     */
    @Test
    void verifiesABodyThatIsNotPureAscii() {
        byte[] body = "{\"name\":\"华东区投标中心\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(body, header(NOW.getEpochSecond(), SECRET, body))).isTrue();
    }

    /**
     * 同一段文字换一种编码就<strong>不是</strong>同一段字节。
     *
     * <p>这是上一条的反面：签名绑的是字节，不是「看起来一样的字符串」。
     * 任何在中途解码再编码的实现都会在这里露馅。
     */
    @Test
    void refusesBytesThatWentThroughTheWrongCharsetOnTheWayIn() {
        byte[] utf8 = "{\"name\":\"投标\"}".getBytes(StandardCharsets.UTF_8);
        // 「按 Latin-1 解码、按 UTF-8 编码」——这正是用 String 接收请求体
        // 又赶上一个没声明 charset 的 Content-Type 时会发生的事。
        byte[] mangled = new String(utf8, StandardCharsets.ISO_8859_1)
                .getBytes(StandardCharsets.UTF_8);
        String signedOverUtf8 = header(NOW.getEpochSecond(), SECRET, utf8);

        assertThat(mangled).as("先确认这两串字节确实不同，否则下一条断言是空的")
                .isNotEqualTo(utf8);
        assertThat(verifier.verify(utf8, signedOverUtf8)).isTrue();
        assertThat(verifier.verify(mangled, signedOverUtf8))
                .as("被改写过的字节验不过——这就是那个 bug 上线后的样子")
                .isFalse();
    }

    @Test
    void refusesABodyThatWasTamperedWithAfterSigning() {
        byte[] signed = "{\"seq\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] delivered = "{\"seq\":9}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(delivered, header(NOW.getEpochSecond(), SECRET, signed)))
                .isFalse();
    }

    // ── 拒绝路径 ────────────────────────────────────────────────────────────

    /**
     * <strong>没配密钥就一律拒绝。</strong>
     *
     * <p>放行是最糟的兜底：一个漏配了密钥的部署会变成任何人都能往里发
     * 开通事件的开放端点，而它看起来完全正常——没有报错，也没有告警。
     */
    @Test
    void refusesEverythingWhenNoSecretIsConfigured() {
        HmacWebhookSignatureVerifier unconfigured = verifierWith("", "");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.verify(body, header(NOW.getEpochSecond(), SECRET, body)))
                .as("拿着一把有效密钥签的请求，在没配密钥的这一侧也必须被拒")
                .isFalse();
        assertThat(unconfigured.verify(body, "t=1,v1=" + "0".repeat(64))).isFalse();
    }

    @Test
    void refusesASignatureFromTheWrongSecret() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(body, header(NOW.getEpochSecond(), "whsec_attacker", body)))
                .isFalse();
    }

    /**
     * 时间戳超出容差即拒——两个方向都要拒。
     *
     * <p>只挡「太旧」会让一个把时钟拨到未来的重放永远有效：
     * 攻击者截获一条签名后可以拿着它慢慢用。
     */
    @Test
    void refusesAStaleOrFutureDatedSignature() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        long tooOld = NOW.getEpochSecond() - 301;
        long tooNew = NOW.getEpochSecond() + 301;

        assertThat(verifier.verify(body, header(tooOld, SECRET, body)))
                .as("五分钟前签的").isFalse();
        assertThat(verifier.verify(body, header(tooNew, SECRET, body)))
                .as("签在未来——只挡旧的等于给重放留了一扇永久的门").isFalse();
        assertThat(verifier.verify(body, header(NOW.getEpochSecond() - 299, SECRET, body)))
                .as("容差之内仍要放行，否则正常的时钟漂移会变成随机失败").isTrue();
    }

    @Test
    void refusesAHeaderItCannotParse() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String good = hmac(SECRET, NOW.getEpochSecond(), body);

        assertThat(verifier.verify(body, null)).isFalse();
        assertThat(verifier.verify(body, "")).isFalse();
        assertThat(verifier.verify(body, "garbage")).isFalse();
        assertThat(verifier.verify(body, "v1=" + good)).as("缺时间戳").isFalse();
        assertThat(verifier.verify(body, "t=" + NOW.getEpochSecond())).as("缺签名").isFalse();
        assertThat(verifier.verify(body, "t=not-a-number,v1=" + good)).isFalse();
    }

    @Test
    void refusesAMissingBody() {
        assertThat(verifier.verify(null, "t=" + NOW.getEpochSecond() + ",v1=" + "0".repeat(64)))
                .isFalse();
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private static HmacWebhookSignatureVerifier verifierWith(String current, String next) {
        return new HmacWebhookSignatureVerifier(
                current, next, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String header(long timestamp, String secret, byte[] body) {
        return "t=" + timestamp + ",v1=" + hmac(secret, timestamp, body);
    }

    /** 独立于生产实现重新算一遍签名——照抄实现的测试只能证明它没变。 */
    private static String hmac(String secret, long timestamp, byte[] body) {
        try {
            byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.US_ASCII);
            byte[] payload = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, payload, 0, prefix.length);
            System.arraycopy(body, 0, payload, prefix.length, body.length);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
