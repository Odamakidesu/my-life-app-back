package com.mylifeapp.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * 認証の成否を記録する。
 *
 * <p>Spring Security の認証イベントに乗ることで、コントローラや AuthenticationProvider に
 * 手を入れずに済み、将来認証方式が増えても取りこぼさない。
 *
 * <p>認証失敗は WARN に置く。ERROR にすると総当たり攻撃で大量発生してアラート疲れを起こし、
 * 本物の ERROR が埋もれる。アラートは「単位時間あたりの失敗率」で組むこと。
 */
@Component
public class AuthEventLogger {

    private static final Logger log = LoggerFactory.getLogger(AuthEventLogger.class);

    private final PrincipalHasher hasher;

    public AuthEventLogger(PrincipalHasher hasher) {
        this.hasher = hasher;
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        // 原因の「分類」だけを出す。username / password / token は出さない。
        log.warn("auth_failure reason={} principalHash={}",
                event.getException().getClass().getSimpleName(),
                hasher.hash(String.valueOf(event.getAuthentication().getName())));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("auth_success principalHash={}", hasher.hash(event.getAuthentication().getName()));
    }
}
