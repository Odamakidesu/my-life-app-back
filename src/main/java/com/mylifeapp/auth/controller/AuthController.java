package com.mylifeapp.auth.controller;

import com.mylifeapp.auth.dto.LoginRequest;
import com.mylifeapp.auth.dto.LoginResponse;
import com.mylifeapp.auth.jwt.JwtTokenProvider;
import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
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

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> adminOnlyEndpoint() {
        return ResponseEntity.ok("You're an admin!");
    }
}
