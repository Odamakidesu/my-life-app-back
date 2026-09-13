package com.mylifeapp.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * エラーレスポンスの統一形式。
 * traceId はログの MDC と同じ値なので、ユーザーからの申告とログを1対1で突き合わせられる。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, error, message, path, traceId, null);
    }

    public static ApiError withFields(int status, String error, String message, String path,
                                      String traceId, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, traceId, fieldErrors);
    }
}
