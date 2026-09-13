package com.mylifeapp.common.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * ログに載せるユーザー識別子を作る。
 *
 * <p>「同一アカウントへの連続失敗」を数えるには識別子が要るが、生のユーザー名を
 * ログに残すとユーザー名の一覧がログ閲覧者に渡る。鍵付きハッシュの先頭数バイトだけを出す。
 * 鍵は JWT の署名鍵を流用する（同じ秘匿レベルで管理されているため）。
 */
@Component
public class PrincipalHasher {

    private final byte[] key;

    public PrincipalHasher(@Value("${jwt.secret}") String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String principal) {
        if (principal == null || principal.isBlank()) {
            return "anonymous";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(principal.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (GeneralSecurityException ex) {
            return "unavailable";
        }
    }
}
