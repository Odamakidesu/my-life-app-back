package com.mylifeapp.auth.controller;

import com.mylifeapp.auth.dto.LoginRequest;
import com.mylifeapp.auth.dto.LoginResponse;
import com.mylifeapp.auth.dto.PasswordChangeRequest;
import com.mylifeapp.auth.session.SessionService;
import com.mylifeapp.auth.jwt.JwtTokenProvider;
import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.user.dto.UserResponse;
import com.mylifeapp.user.entity.User;
import com.mylifeapp.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionService sessionService;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          SessionService sessionService,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionService = sessionService;
        this.userService = userService;
    }

    @PostMapping(value = "/login", produces = "application/json")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        // 認証に失敗した場合の AuthenticationException は GlobalExceptionHandler が 401 に写像する。
        // 失敗の記録は AuthEventLogger が認証イベントとして拾う。
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(), loginRequest.getPassword()
            )
        );

        String token = jwtTokenProvider.generateToken(authentication);
        return ResponseEntity.ok(new LoginResponse(token));
    }

    /**
     * 現在のユーザー。
     *
     * <p>このパスは認証必須（SecurityConfig の permitAll 列挙に含めていない）。
     * 以前は /api/auth/** が前方一致で permitAll だったため匿名で到達でき、
     * Authentication が null になって NullPointerException を起こしていた。
     * 未認証なら RestAuthenticationEntryPoint が 401 を返すため、ここには到達しない。
     */
    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return new UserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.isEnabled(),
                principal.getRole());
    }

    /**
     * ログアウト。送ってきたトークンを無効にする（他の端末のログインはそのまま）。
     * 認証必須なので、ここに来るのは有効なトークンだけ。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        sessionService.logout(authorization.substring("Bearer ".length()));
        return ResponseEntity.noContent().build();
    }

    /**
     * パスワード変更。発行済みのトークン（他の端末を含む）はすべて無効になるので、
     * この端末で使い続けるための新しいトークンを返す。
     */
    @PutMapping("/password")
    public LoginResponse changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody PasswordChangeRequest request) {
        User updated = userService.changePassword(
                principal.getUserId(), request.currentPassword(), request.newPassword());
        log.info("password_changed userId={}", principal.getUserId());
        return new LoginResponse(jwtTokenProvider.generateToken(updated.getUsername(), updated.getTokenVersion()));
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> adminOnlyEndpoint() {
        return ResponseEntity.ok("You're an admin!");
    }
}
