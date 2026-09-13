package com.mylifeapp.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** JWT の生成と検証。DB を必要としない純粋な単体テスト。 */
class JwtTokenProviderTest {

    private static final String SECRET =
            "test-only-dummy-secret-do-not-use-in-any-real-environment-0123456789";
    private static final String OTHER_SECRET =
            "another-test-only-secret-that-is-long-enough-for-hs256-9876543210ab";

    private static final Authentication AUTH =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());

    private JwtTokenProvider provider(String secret, long expirationMs) {
        return new JwtTokenProvider(new JwtProperties(secret, expirationMs));
    }

    @Test
    @DisplayName("生成したトークンからユーザー名を復元できる")
    void roundTripsSubject() {
        JwtTokenProvider provider = provider(SECRET, 86_400_000L);
        String token = provider.generateToken(AUTH);

        assertThat(provider.validateToken(token)).isEqualTo(JwtValidationResult.VALID);
        assertThat(provider.getUsernameFromJWT(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("jwt_expiration_msに設定した値がトークンのexpに反映される")
    void expirationPropertyIsReflectedInToken() {
        long expirationMs = 3_600_000L; // 1時間
        JwtTokenProvider provider = provider(SECRET, expirationMs);

        String token = provider.generateToken(AUTH);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        long actual = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        // 秒精度に丸められるため 1 秒の誤差を許容する。
        assertThat(actual).isCloseTo(expirationMs, org.assertj.core.data.Offset.offset(1_000L));
    }

    @Test
    @DisplayName("有効期限切れトークンはEXPIREDを返す")
    void expiredTokenIsReportedAsExpired() {
        // 既に期限切れのトークンを直接組み立てる。
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(provider(SECRET, 86_400_000L).validateToken(expired))
                .isEqualTo(JwtValidationResult.EXPIRED);
    }

    @Test
    @DisplayName("別の秘密鍵で署名したトークンはBAD_SIGNATUREを返す")
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = provider(OTHER_SECRET, 86_400_000L).generateToken(AUTH);

        // 期限切れと鍵不一致を区別できることが、鍵ローテーション事故の切り分け点になる。
        assertThat(provider(SECRET, 86_400_000L).validateToken(foreign))
                .isEqualTo(JwtValidationResult.BAD_SIGNATURE);
    }

    @Test
    @DisplayName("JWTとして解釈できない文字列はMALFORMEDを返す")
    void garbageIsReportedAsMalformed() {
        assertThat(provider(SECRET, 86_400_000L).validateToken("not-a-jwt"))
                .isEqualTo(JwtValidationResult.MALFORMED);
    }

    @Test
    @DisplayName("nullや空文字はABSENTを返す")
    void nullOrBlankIsReportedAsAbsent() {
        JwtTokenProvider provider = provider(SECRET, 86_400_000L);

        assertThat(provider.validateToken(null)).isEqualTo(JwtValidationResult.ABSENT);
        assertThat(provider.validateToken("   ")).isEqualTo(JwtValidationResult.ABSENT);
    }

    @Test
    @DisplayName("鍵のバイト列はプラットフォーム既定エンコーディングに依存しない")
    void keyBytesAreCharsetIndependent() {
        // 開発機は file.encoding=MS932 のため、charset 指定が無いと環境間で署名鍵がずれる。
        String secretWithNonAscii = SECRET + "日本語を含む鍵";
        JwtTokenProvider provider = provider(secretWithNonAscii, 86_400_000L);

        String token = provider.generateToken(AUTH);
        SecretKey utf8Key = Keys.hmacShaKeyFor(secretWithNonAscii.getBytes(StandardCharsets.UTF_8));

        assertThat(Jwts.parser().verifyWith(utf8Key).build()
                .parseSignedClaims(token).getPayload().getSubject()).isEqualTo("alice");
    }
}
