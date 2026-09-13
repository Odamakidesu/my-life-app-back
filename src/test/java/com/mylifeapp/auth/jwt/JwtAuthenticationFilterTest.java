package com.mylifeapp.auth.jwt;

import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.user.model.User;
import com.mylifeapp.user.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * フィルタの分岐。
 *
 * <p>このクラスの分岐カバレッジは 0/26 だった。認証バイパスにも全API 401 にも
 * 直結する場所で、振る舞いを誰も確認していない状態だった。
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "test-only-dummy-secret-do-not-use-in-any-real-environment-0123456789";

    private final JwtTokenProvider tokenProvider =
            new JwtTokenProvider(new JwtProperties(SECRET, 86_400_000L));

    private final UserDetailsService userDetailsService = username -> {
        if (!"alice".equals(username)) {
            throw new UsernameNotFoundException("ユーザーが見つかりません");
        }
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setPassword("irrelevant");
        user.setEnabled(true);
        user.setRole(UserRole.USER);
        return new UserPrincipal(user);
    };

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenProvider, userDetailsService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private String validTokenForAlice() {
        return tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));
    }

    @Test
    @DisplayName("Authorizationヘッダが無い場合SecurityContextは空のまま次のフィルタに進む")
    void noHeaderLeavesContextEmpty() throws Exception {
        run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("有効なトークンの場合は認証が確立される")
    void validTokenAuthenticates() throws Exception {
        run("Bearer " + validTokenForAlice());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("不正な署名のトークンの場合は認証されない")
    void badSignatureDoesNotAuthenticate() throws Exception {
        String foreign = new JwtTokenProvider(
                new JwtProperties("another-test-only-secret-that-is-long-enough-for-hs256-9876543210ab",
                        86_400_000L))
                .generateToken(new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        run("Bearer " + foreign);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer以外のスキームのヘッダは無視される")
    void nonBearerSchemeIsIgnored() throws Exception {
        run("Basic " + validTokenForAlice());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("壊れたトークンでも例外を投げず認証されないまま次に進む")
    void malformedTokenIsRejectedWithoutThrowing() throws Exception {
        run("Bearer not-a-jwt");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("署名は有効だがユーザーが存在しない場合は認証されない")
    void unknownPrincipalDoesNotAuthenticate() throws Exception {
        String token = tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken("ghost", null, List.of()));

        run("Bearer " + token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
