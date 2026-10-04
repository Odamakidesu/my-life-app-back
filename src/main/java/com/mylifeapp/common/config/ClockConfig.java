package com.mylifeapp.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 時刻をコンテナから取得できるようにする。
 * LocalDateTime.now() を直接呼ぶと、作成日時を検証するテストが書けない。
 *
 * <p>タイムゾーンは OS に任せず明示する。メモの締切は利用者が日本時間で入力した壁時計の時刻
 * （datetime-local の値）をそのまま保存しているため、「期限切れ」「24時間以内」の判定や
 * 作成日時もそれと同じ日本時間でなければならない。ECS のコンテナは既定で UTC なので、
 * OS に任せると判定が9時間ずれる。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Tokyo}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
