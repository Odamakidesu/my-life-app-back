package com.mylifeapp.auth.session;

import com.mylifeapp.auth.jwt.TokenRevocationChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * ログアウトで無効にしたトークン（revoked_token）。
 *
 * <p>JWT はサーバに状態を持たないため、ログアウトしても有効期限（24時間）までは使えてしまう。
 * 端末に残ったトークンが漏れても使えないよう、ログアウトしたトークンの jti を期限まで覚えておく。
 * 期限を過ぎたトークンは署名検証の段階で弾かれるので、記録は期限が来たら消してよい。
 */
@Component
public class RevokedTokenStore implements TokenRevocationChecker {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public RevokedTokenStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void revoke(String jti, LocalDateTime expiresAt) {
        jdbcTemplate.update("INSERT IGNORE INTO revoked_token (jti, expires_at) VALUES (?, ?)", jti, expiresAt);
    }

    @Override
    public boolean isRevoked(String jti) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM revoked_token WHERE jti = ?", Integer.class, jti);
        return count != null && count > 0;
    }

    /** 期限を過ぎた記録を消す。 */
    @Scheduled(cron = "${app.revoked-token.purge-cron:0 40 3 * * *}", zone = "${app.time-zone:Asia/Tokyo}")
    public int purgeExpired() {
        int purged = jdbcTemplate.update(
                "DELETE FROM revoked_token WHERE expires_at < ?", LocalDateTime.now(clock));
        log.info("revoked_tokens_purged count={}", purged);
        return purged;
    }
}
