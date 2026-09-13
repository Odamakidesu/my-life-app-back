package com.mylifeapp.common.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ALB / ECS 用のヘルスチェック。
 *
 * <p>以前は無条件に "OK" を返していた。RDS がダウンしてもコネクションプールが枯渇しても
 * 200 を返し続けるため、ECS は「全タスク正常」と判断し、ALB は壊れたタスクに
 * トラフィックを流し続け、ローリング再起動もスケーリングも発動しなかった。
 * 障害が自動検知されず、ユーザーからの申告でしか気づけない状態だった。
 *
 * <p>依存先の健全性まで見たい場合は /actuator/health/readiness も無認証で公開してある。
 * ALB のターゲットグループはそちらに向けるのが望ましい。
 */
@RestController
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthCheckController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (RuntimeException ex) {
            // 詳細は外に出さない。無認証で到達できるため、接続先やエラー内容は情報になる。
            log.error("health_check_failed component=db reason={}", ex.getClass().getSimpleName(), ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
        }
    }
}
