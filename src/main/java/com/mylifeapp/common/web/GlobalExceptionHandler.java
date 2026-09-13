package com.mylifeapp.common.web;

import com.mylifeapp.common.observability.TraceIds;
import com.mylifeapp.note.exception.NoteNotFoundException;
import com.mylifeapp.user.exception.DuplicateUsernameException;
import com.mylifeapp.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 例外を HTTP ステータスに写像する唯一の場所。
 *
 * <p>これが無かったため、コントローラが投げた例外はすべて 500 になり、
 * Tomcat の {@code /error} への ERROR ディスパッチが認可で弾かれて
 * 最終的に「403・ボディ空」としてクライアントに届いていた。
 * 404 も 409 も 500 も区別できず、例外メッセージも失われていた。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoteNotFoundException.class)
    public ResponseEntity<ApiError> handleNoteNotFound(NoteNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ApiError> handleDuplicateUsername(DuplicateUsernameException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // 一意制約・外部キー違反など。原因の詳細はログにだけ残し、クライアントには返さない。
        log.warn("data_integrity_violation path={} cause={}",
                request.getRequestURI(), ex.getMostSpecificCause().getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "Conflict", "データの整合性に反する操作です。", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError body = ApiError.withFields(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", "入力内容に誤りがあります。",
                request.getRequestURI(), TraceIds.current(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 本文を読み取れなかった場合。壊れた JSON、型の合わない値、
     * 解釈できない日時文字列などが該当する。
     *
     * <p>これを拾わないと 500 になる。クライアントの送信内容が原因なのに
     * サーバ障害として扱われ、調査の向きを誤らせるうえ、
     * 利用者にも「サーバーのエラー」としか伝わらない。
     * 原因の詳細（どの型のどの位置か）はログにだけ残す。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        log.warn("unreadable_request_body path={} cause={}",
                request.getRequestURI(), ex.getMostSpecificCause().getClass().getSimpleName());
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "リクエストの内容を解釈できませんでした。入力形式を確認してください。", request);
    }

    /**
     * 認証・認可の例外はフィルタチェーンの EntryPoint / AccessDeniedHandler が処理するが、
     * コントローラ内（@PreAuthorize など）で発生した分はここに来る。
     * ここで握ると 500 になってしまうため、明示的に 401 / 403 に写像する。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("authentication_failed path={} reason={}", request.getRequestURI(), ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "認証に失敗しました。", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("access_denied path={}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Forbidden", "この操作を行う権限がありません。", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 内部の詳細はレスポンスに出さない。traceId でログと突き合わせる。
        log.error("unhandled_exception method={} path={}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "サーバー内部でエラーが発生しました。", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                           HttpServletRequest request) {
        ApiError body = ApiError.of(status.value(), error, message, request.getRequestURI(), TraceIds.current());
        return ResponseEntity.status(status).body(body);
    }
}
