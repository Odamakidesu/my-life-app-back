package com.mylifeapp.auth.jwt;

import com.mylifeapp.auth.userdetails.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization: Bearer トークンを検証して SecurityContext を確立する。
 *
 * <p>{@code @Component} は付けない。付けるとサーブレットコンテナにも自動登録され、
 * Spring Security チェーンとの二重登録になる。現状は {@code OncePerRequestFilter} が
 * 抑止しているが、継承をやめるか {@code @Order} を付けた瞬間に壊れる。
 * 登録は {@code SecurityConfig} の addFilterBefore 一箇所に限定する。
 *
 * <p>パスによる除外（shouldNotFilter）は行わない。除外リストと
 * authorizeHttpRequests のルールが二重管理になり、実際にずれていた。
 * このフィルタは「トークンがあれば検証する」だけを担い、認可は SecurityConfig に一元化する。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = getJwtFromRequest(request);
        JwtValidationResult result = jwtTokenProvider.validateToken(token);

        if (result.isValid()) {
            authenticate(token, request);
        } else if (result != JwtValidationResult.ABSENT) {
            // トークンが送られてきたのに通らなかった場合だけ記録する。
            // 「ヘッダが無かった」と「トークンが無効だった」を区別できることが、
            // クライアント/CORS の問題と鍵/期限の問題の切り分け点になる。
            // トークン本体やその断片は出さない。有効な JWT はそれ自体が認証情報であり、
            // ログに出た時点で閲覧権限がなりすまし権限に化ける。
            log.warn("jwt_rejected reason={} method={} path={}",
                    result, request.getMethod(), request.getRequestURI());
            SecurityContextHolder.clearContext();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            String username = jwtTokenProvider.getUsernameFromJWT(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (userDetails instanceof UserPrincipal principal && principal.getUserId() != null) {
                MDC.put("userId", String.valueOf(principal.getUserId()));
            }
        } catch (UsernameNotFoundException ex) {
            // 署名は有効だが、ユーザーが削除された等でもう存在しない。
            log.warn("jwt_rejected reason=PRINCIPAL_NOT_FOUND method={} path={}",
                    request.getMethod(), request.getRequestURI());
            SecurityContextHolder.clearContext();
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " の後ろ
        }
        return null;
    }
}
