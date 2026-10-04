package com.mylifeapp.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定期処理（ゴミ箱の自動削除、期限切れの失効トークンの掃除）を有効にする。
 * app.scheduling.enabled=false で止められる（テストで時刻に依存した処理を走らせないため）。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
