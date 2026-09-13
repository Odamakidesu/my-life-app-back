package com.mylifeapp.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レート制限の分岐。
 *
 * <p>結合テストでは制限を無効化しているため（連続ログインが 429 になってしまう）、
 * フィルタ自体の振る舞いはここで固定する。
 */
class RateLimitFilterTest {

    // 本番では Spring が構成した ObjectMapper（JSR-310 モジュール込み）が注入される。
    // テストで素の ObjectMapper を使うと Instant を書けないため、同等の構成にしておく。
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RateLimitFilter filter(boolean enabled, int loginCapacity, int maxTracked) {
        return new RateLimitFilter(
                new RateLimitProperties(enabled, loginCapacity, Duration.ofMinutes(1),
                        2, Duration.ofHours(1), maxTracked),
                objectMapper);
    }

    private MockHttpServletResponse call(RateLimitFilter filter, String method, String path, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (ip != null) {
            request.addHeader("X-Forwarded-For", ip);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("上限内のログイン試行はそのまま通過する")
    void requestsWithinLimitPassThrough() throws Exception {
        RateLimitFilter filter = filter(true, 3, 1000);

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("上限を超えたログイン試行は429とRetry-Afterを返す")
    void requestsOverLimitAreRejected() throws Exception {
        RateLimitFilter filter = filter(true, 2, 1000);

        call(filter, "POST", "/api/auth/login", "203.0.113.2");
        call(filter, "POST", "/api/auth/login", "203.0.113.2");
        MockHttpServletResponse blocked = call(filter, "POST", "/api/auth/login", "203.0.113.2");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    @DisplayName("制限はクライアントIPごとに独立している")
    void limitsAreTrackedPerClient() throws Exception {
        RateLimitFilter filter = filter(true, 1, 1000);

        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.3").getStatus()).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.3").getStatus()).isEqualTo(429);
        // 別のクライアントは巻き添えにならない
        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.4").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ログインと登録は別々の枠で数える")
    void loginAndRegisterHaveSeparateBuckets() throws Exception {
        RateLimitFilter filter = filter(true, 1, 1000);

        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.5").getStatus()).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.5").getStatus()).isEqualTo(429);
        assertThat(call(filter, "POST", "/api/auth/register", "203.0.113.5").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("対象外のパスやメソッドは制限しない")
    void otherPathsAndMethodsAreNotLimited() throws Exception {
        RateLimitFilter filter = filter(true, 1, 1000);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "GET", "/api/notes", "203.0.113.6").getStatus()).isEqualTo(200);
            assertThat(call(filter, "GET", "/api/auth/login", "203.0.113.6").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("無効化されている場合は一切制限しない")
    void disabledFilterNeverLimits() throws Exception {
        RateLimitFilter filter = filter(false, 1, 1000);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.7").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("X-Forwarded-Forが複数ある場合は先頭のクライアントIPを使う")
    void usesFirstEntryOfForwardedFor() throws Exception {
        RateLimitFilter filter = filter(true, 1, 1000);

        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.8, 10.0.0.1").getStatus())
                .isEqualTo(200);
        assertThat(call(filter, "POST", "/api/auth/login", "203.0.113.8, 10.0.0.2").getStatus())
                .isEqualTo(429);
    }

    @Test
    @DisplayName("X-Forwarded-Forが無い場合はremoteAddrで識別する")
    void fallsBackToRemoteAddr() throws Exception {
        RateLimitFilter filter = filter(true, 1, 1000);

        assertThat(call(filter, "POST", "/api/auth/login", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/auth/login", null).getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("追跡クライアント数が上限を超えると台帳をリセットしメモリ枯渇を避ける")
    void trackerIsResetWhenTooManyClients() throws Exception {
        RateLimitFilter filter = filter(true, 1, 2);

        call(filter, "POST", "/api/auth/login", "198.51.100.1");
        call(filter, "POST", "/api/auth/login", "198.51.100.2");
        call(filter, "POST", "/api/auth/login", "198.51.100.3");
        // 上限超過でクリアされるため、最初のクライアントの枠も作り直される
        assertThat(call(filter, "POST", "/api/auth/login", "198.51.100.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("設定値が不正な場合は安全な既定値にフォールバックする")
    void invalidPropertiesFallBackToDefaults() {
        RateLimitProperties properties =
                new RateLimitProperties(true, 0, null, -5, null, 0);

        assertThat(properties.loginCapacity()).isEqualTo(10);
        assertThat(properties.loginPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.registerCapacity()).isEqualTo(5);
        assertThat(properties.registerPeriod()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.maxTrackedClients()).isEqualTo(50_000);
    }
}
