package com.mylifeapp.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 1リクエストを追跡するための相関IDを MDC に載せる。
 *
 * <p>ALB が必ず付与する X-Amzn-Trace-Id を拾って引き継ぐことで、
 * ALB のアクセスログとアプリケーションログを突き合わせられるようにする。
 * これが無いと「特定ユーザーだけか / 特定タスクだけか / 全体か」を切り分けられず、
 * 部分障害と全体障害の区別ができない。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Amzn-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TraceIds.TRACE_ID, traceId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
            MDC.put("status", String.valueOf(response.getStatus()));
        } finally {
            // Tomcat はスレッドを使い回す。クリアを落とすと、別ユーザーのリクエストに
            // 前のユーザーの traceId / userId が付く。調査を誤らせるだけでなく情報の混線になる。
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        // エラーディスパッチでも相関IDを維持する。
        return false;
    }
}
