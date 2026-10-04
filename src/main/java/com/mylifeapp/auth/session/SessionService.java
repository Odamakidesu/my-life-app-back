package com.mylifeapp.auth.session;

import com.mylifeapp.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** ログアウト（このトークンだけを無効にする）。 */
@Service
public class SessionService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RevokedTokenStore revokedTokenStore;
    private final Clock clock;

    public SessionService(JwtTokenProvider jwtTokenProvider, RevokedTokenStore revokedTokenStore, Clock clock) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.revokedTokenStore = revokedTokenStore;
        this.clock = clock;
    }

    /**
     * ここに来るのは認証フィルタを通った（署名・期限・版が正しい）トークンだけ。
     * jti の無い旧形式のトークンは個別に無効にできないので何もしない（期限で切れる）。
     */
    public void logout(String token) {
        Claims claims = jwtTokenProvider.parseClaims(token);
        if (claims.getId() == null) {
            return;
        }
        LocalDateTime expiresAt = LocalDateTime.ofInstant(claims.getExpiration().toInstant(), clock.getZone());
        revokedTokenStore.revoke(claims.getId(), expiresAt);
    }
}
