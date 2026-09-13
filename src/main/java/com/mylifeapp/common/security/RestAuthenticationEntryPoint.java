package com.mylifeapp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylifeapp.common.observability.TraceIds;
import com.mylifeapp.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未認証アクセスに 401 を返す。
 *
 * <p>formLogin も httpBasic も設定していないため、これを明示しないと Spring Security は
 * 既定の {@code Http403ForbiddenEntryPoint} を使い、トークン期限切れでも 403 を返していた。
 * クライアントは「再ログインが必要」と「権限不足」を区別できず、403 のまま操作不能になる。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("auth_required method={} path={} reason={}",
                request.getMethod(), request.getRequestURI(), authException.getClass().getSimpleName());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "認証が必要です。再度ログインしてください。",
                request.getRequestURI(),
                TraceIds.current());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
