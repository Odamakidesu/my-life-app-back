package com.mylifeapp.auth.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(JwtProperties properties) {
        // getBytes() を charset 無しで呼ぶと JVM 既定エンコーディングに依存する。
        // このプロジェクトは開発機が file.encoding=MS932 のため(build.gradle のコメント参照)、
        // 非 ASCII を含む鍵だと開発環境と ECS(UTF-8) でバイト列が変わり、署名鍵が環境間で不一致になる。
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = properties.expirationMs();

        // 「SSM のローテーション後に別の鍵が入った」を起動ログ1行で特定できるようにする。
        // 値そのものは出さない。長さとフィンガープリントだけで同一性の判定には足りる。
        log.info("jwt config loaded keyLengthBytes={} keyFingerprint={} expirationMs={}",
                keyBytes.length, fingerprint(keyBytes), this.expirationMs);
    }

    /** トークン生成 */
    public String generateToken(Authentication authentication) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(authentication.getName())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /** トークンからユーザー名を取得する。検証に失敗した場合は例外を投げる。 */
    public String getUsernameFromJWT(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * トークンを検証し、失敗した場合はその理由を返す。
     * 呼び出し側が理由をログに残せるよう、boolean ではなく列挙型を返す。
     */
    public JwtValidationResult validateToken(String authToken) {
        if (authToken == null || authToken.isBlank()) {
            return JwtValidationResult.ABSENT;
        }
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(authToken);
            return JwtValidationResult.VALID;
        } catch (ExpiredJwtException ex) {
            return JwtValidationResult.EXPIRED;
        } catch (SignatureException ex) {
            return JwtValidationResult.BAD_SIGNATURE;
        } catch (MalformedJwtException | IllegalArgumentException ex) {
            return JwtValidationResult.MALFORMED;
        } catch (UnsupportedJwtException ex) {
            return JwtValidationResult.UNSUPPORTED;
        }
    }

    private static String fingerprint(byte[] keyBytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 は JRE 必須アルゴリズムなので到達しない。
            return "unavailable";
        }
    }
}
