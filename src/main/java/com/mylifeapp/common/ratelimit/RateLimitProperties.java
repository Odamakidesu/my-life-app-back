package com.mylifeapp.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 認証系エンドポイントのレート制限設定。
 * 値は「正規ユーザーの操作を妨げず、総当たりのコストを上げる」ところを狙う。
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int loginCapacity,
        Duration loginPeriod,
        int registerCapacity,
        Duration registerPeriod,
        int maxTrackedClients
) {
    public RateLimitProperties {
        if (loginCapacity <= 0) {
            loginCapacity = 10;
        }
        if (loginPeriod == null) {
            loginPeriod = Duration.ofMinutes(1);
        }
        if (registerCapacity <= 0) {
            registerCapacity = 5;
        }
        if (registerPeriod == null) {
            registerPeriod = Duration.ofHours(1);
        }
        if (maxTrackedClients <= 0) {
            maxTrackedClients = 50_000;
        }
    }
}
