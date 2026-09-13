package com.mylifeapp.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylifeapp.common.observability.TraceIds;
import com.mylifeapp.common.web.ApiError;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ログイン / 登録に対するクライアント単位のレート制限。
 *
 * <p>BCrypt の検証は 0.5 vCPU の環境で1回あたり約 55ms を消費し、しかも
 * DaoAuthenticationProvider は存在しないユーザー名に対しても
 * mitigateAgainstTimingAttack() で同じコストを払う。つまり未認証の POST が
 * そのまま CPU を焼ける。ここで落とすのは認証処理の前でなければ意味がない。
 *
 * <p>制限はタスク内メモリで持つ。ECS のタスクを増やすと制限はタスク単位になるため、
 * 厳密な総量規制が必要になった時点で ALB / WAF 側か共有ストアへ移すこと。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(LOGIN_PATH.equals(path) || REGISTER_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientId = clientId(request);
        Bucket bucket = bucketFor(path, clientId);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        // クライアント識別子（IP）はそのまま出す。調査と遮断の判断に必要で、
        // ユーザー名やパスワードと違い、それ自体が認証情報ではない。
        log.warn("rate_limited path={} client={}", path, clientId);
        writeTooManyRequests(request, response);
    }

    private Bucket bucketFor(String path, String clientId) {
        // 無制限に増やすとこのマップ自体がメモリ枯渇の原因になる。
        // 上限に達したら作り直す（制限が一時的に緩むが、OOM よりはよい）。
        if (buckets.size() > properties.maxTrackedClients()) {
            log.warn("rate_limit_tracker_reset size={}", buckets.size());
            buckets.clear();
        }

        boolean isLogin = LOGIN_PATH.equals(path);
        int capacity = isLogin ? properties.loginCapacity() : properties.registerCapacity();
        Duration period = isLogin ? properties.loginPeriod() : properties.registerPeriod();

        return buckets.computeIfAbsent(path + "|" + clientId, key -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, period)
                        .build())
                .build());
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(properties.loginPeriod().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "リクエストが多すぎます。しばらく待ってから再度お試しください。",
                request.getRequestURI(),
                TraceIds.current());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String clientId(HttpServletRequest request) {
        // ALB 経由では remoteAddr が ALB の IP になるため X-Forwarded-For の先頭を使う。
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
