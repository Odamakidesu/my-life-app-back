package com.mylifeapp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylifeapp.auth.jwt.JwtAuthenticationFilter;
import com.mylifeapp.auth.jwt.JwtTokenProvider;
import com.mylifeapp.auth.userdetails.CustomUserDetailsService;
import com.mylifeapp.common.ratelimit.RateLimitFilter;
import com.mylifeapp.common.ratelimit.RateLimitProperties;
import com.mylifeapp.common.security.RestAccessDeniedHandler;
import com.mylifeapp.common.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.transport.HttpsRedirectFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.api.endpoint.base-url}")
    private String baseUrl;

    /**
     * HTTPS を強制するか。フロントエンドが S3 ウェブサイトエンドポイント（HTTPS 非対応）で
     * 配信されている間は有効化できないため既定は false。
     * CloudFront + ACM への移行後に true にすること。
     */
    @Value("${app.security.require-https:false}")
    private boolean requireHttps;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtTokenProvider jwtTokenProvider,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          RateLimitProperties rateLimitProperties,
                          ObjectMapper objectMapper) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // 認証は Authorization ヘッダのみで行い、Cookie もセッションも使わないため CSRF は該当しない。
            // この判断はトークンが Cookie に無いことに完全に依存する。
            // トークンを Cookie 保存に変える場合は、その時点で CSRF 対策が必須要件になる。
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                    .frameOptions(frame -> frame.deny()))
            .authorizeHttpRequests(authz -> authz
                    // エラーディスパッチを認可で潰さない。ここを閉じていたため、
                    // 例外が発生すると 500 ではなく「403・ボディ空」がクライアントに返っていた。
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                    .requestMatchers("/error").permitAll()

                    // 静的資産
                    .requestMatchers(HttpMethod.GET,
                            "/", "/index.html", "/favicon.ico",
                            "/static/**", "/css/**", "/js/**", "/img/**").permitAll()

                    // ヘルスチェック。ALB / ECS から無認証で到達する必要がある。
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    .requestMatchers(HttpMethod.GET,
                            "/actuator/health/readiness", "/actuator/health/liveness").permitAll()

                    // 認証系。前方一致 (/api/auth/**) にすると、このクラスに後から追加された
                    // メソッドが自動的に無認証公開になる。HTTP メソッド込みの完全一致で列挙する。
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()

                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(rateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        if (requireHttps) {
            // requiresChannel(...) は Spring Security 6.4 で非推奨になったため後継を使う。
            // ALB が TLS を終端するため、スキーマの判定は server.forward-headers-strategy=framework
            // が解決する X-Forwarded-Proto に依存する。
            http.addFilterBefore(new HttpsRedirectFilter(), HeaderWriterFilter.class);
        }

        return http.build();
    }

    /**
     * Component スキャンさせずにここで生成する。
     * スキャン対象にするとサーブレットコンテナにも自動登録され、二重登録になる。
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(rateLimitProperties, objectMapper);
    }

    /**
     * このプロバイダは Bean としてグローバルな AuthenticationManager に一度だけ登録される。
     * 以前は (1) この Bean、(2) filterChain の authenticationProvider(...)、
     * (3) 独自の authenticationManager(HttpSecurity) Bean の3箇所で登録されており、
     * ProviderManager が親子で同じプロバイダを持ってログイン1回につきユーザー検索が2回走っていた。
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(baseUrl)); // フロントエンドのURL
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // ワイルドカードは将来追加する内部ヘッダをブラウザ経由で注入する余地を残す。実際に使うものだけ許可する。
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("X-Total-Count"));
        // 認証は Authorization ヘッダで行うため Cookie の送出許可は不要。
        // true のままにすると、トークンを Cookie に移した瞬間に CSRF が成立する。
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
