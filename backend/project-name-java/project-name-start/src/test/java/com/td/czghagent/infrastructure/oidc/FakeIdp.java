// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
package com.td.czghagent.infrastructure.oidc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一个真的会说 HTTP 的假 IdP。
 *
 * <p>用它而不是 mock 掉 {@code IdTokenVerifier}：这条链上最值得保护的东西
 * ——JWKS 按 kid 取键、算法白名单、aud/iss/exp 校验——<b>全部发生在 nimbus 内部</b>。
 * 把 verifier 打桩掉，测的就只剩自己写的那几行胶水，而真正会出事的部分一行没测。
 *
 * <p>用 JDK 自带的 {@link HttpServer} 而不是引 MockWebServer：这里需要的只是
 * 「两个固定路径返回两段 JSON」，为此多背一个测试依赖不划算。
 */
final class FakeIdp implements AutoCloseable {

    private final HttpServer server;
    private final RSAKey signingKey;
    private final RSAKey strangerKey;
    private final AtomicReference<String> tokenResponse = new AtomicReference<>();
    private final AtomicReference<Integer> tokenStatus = new AtomicReference<>(200);

    FakeIdp() throws Exception {
        this.signingKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
        // 一把 IdP 从未公布过的键。用它签出来的票必须被拒——
        // 这正是「按 kid 取键」要挡住的东西。
        this.strangerKey = new RSAKeyGenerator(2048).keyID("stranger-key").generate();

        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange ->
                respond(exchange, 200, discoveryDocument()));
        // 用 JWKSet 生成而不是手拼字符串：拼错一个括号，测试会以「验签失败」
        // 的形式失败，而那看起来像是被测代码的问题。
        server.createContext("/jwks", exchange ->
                respond(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString()));
        server.createContext("/token", exchange ->
                respond(exchange, tokenStatus.get(), tokenResponse.get()));
        server.start();
    }

    String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 设定 /token 的下一次应答。 */
    void respondToTokenWith(int status, String body) {
        tokenStatus.set(status);
        tokenResponse.set(body);
    }

    /** 用 IdP 公布过的键正常签一张票。 */
    String sign(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    /**
     * 签一张 access token。
     *
     * <p>与 id_token 用同一把键，但用途完全不同：产品<b>不验</b>它的签名，
     * 只读里面的 active_org / active_workspace / roles 来决定本地渲染与查询范围。
     * 真正的授权判定发生在被调方，那里会验签。这个区别值得在测试里也保持——
     * 用同一个方法签两种票，会让「这里为什么不验签」这个问题看起来像是疏忽。
     */
    String signAccessToken(JWTClaimsSet claims) throws Exception {
        return sign(claims);
    }

    /** 用一把 IdP 从未公布的键签票；kid 也是未知的。 */
    String signWithUnknownKey(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(strangerKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(strangerKey));
        return jwt.serialize();
    }

    /**
     * 构造一张 {@code alg: none} 的票：三段结构齐全，签名段为空。
     *
     * <p>这是最经典的一种伪造——如果验证方按「头部说什么算法就用什么算法」办事，
     * 它会一路放行。
     */
    String signWithNoneAlgorithm(JWTClaimsSet claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                claims.toJSONObject().toString().getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    /**
     * 算法混淆：拿 RSA <b>公钥</b>当 HMAC 密钥签一张 HS256。
     *
     * <p>公钥是公开的，所以任何人都能签出这样一张票。验证方只要把
     * 「用配置里的那把键去验」实现成「用键的字节去验」，就会认它。
     */
    String signWithPublicKeyAsHmacSecret(JWTClaimsSet claims) throws Exception {
        byte[] secret = signingKey.toPublicJWK().toJSONString()
                .getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[Math.max(32, secret.length)];
        System.arraycopy(secret, 0, padded, 0, Math.min(secret.length, padded.length));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(padded));
        return jwt.serialize();
    }

    private String discoveryDocument() {
        return """
                {
                  "issuer": "%s",
                  "authorization_endpoint": "%s/authorize",
                  "token_endpoint": "%s/token",
                  "jwks_uri": "%s/jwks",
                  "end_session_endpoint": "%s/logout"
                }
                """.formatted(issuer(), issuer(), issuer(), issuer(), issuer());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
