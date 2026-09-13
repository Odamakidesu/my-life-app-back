package com.mylifeapp.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 時刻をコンテナから取得できるようにする。
 * LocalDateTime.now() を直接呼ぶと、作成日時を検証するテストが書けない。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
