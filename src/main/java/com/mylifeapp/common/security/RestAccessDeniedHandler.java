package com.mylifeapp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylifeapp.common.observability.TraceIds;
import com.mylifeapp.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 認証済みだが権限が足りない場合に 403 を返し、拒否を記録する。 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("access_denied method={} path={}", request.getMethod(), request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "この操作を行う権限がありません。",
                request.getRequestURI(),
                TraceIds.current());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
