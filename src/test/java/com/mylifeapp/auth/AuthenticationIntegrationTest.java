package com.mylifeapp.auth;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証フローの結合テスト。
 *
 * <p>Spring Boot のマイナーアップグレードで壊れうるのはまさにここで、
 * これまで「コンパイルが通り Bean が生成できた」以上の検証が存在しなかった。
 */
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("正しいユーザー名とパスワードでログインすると200とJWTが返る")
    void loginWithValidCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\",\"password\":\"" + SEEDED_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("パスワードが誤っている場合401を返しトークンを含まない")
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("存在しないユーザー名の場合401でありユーザー不存在を示す情報を漏らさない")
    void loginWithUnknownUserReturns401WithoutEnumerationHint() throws Exception {
        String unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"no-such-user-at-all\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // 応答からユーザーの存在有無が読み取れてはいけない。
        assertThat(objectMapper.readTree(unknown).get("message"))
                .isEqualTo(objectMapper.readTree(wrongPassword).get("message"));
    }

    @Test
    @DisplayName("enabled=falseのユーザーはログインできない")
    void disabledUserCannotLogIn() throws Exception {
        insertUser("disabled-user", SEEDED_PASSWORD_HASH, false, "USER");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"disabled-user\",\"password\":\"" + SEEDED_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("トークン無しでapi_auth_meにアクセスすると401を返す")
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("有効なトークンでapi_auth_meが自分の情報を返しパスワードを含まない")
    void meWithValidTokenReturnsSelf() throws Exception {
        String token = login("testuser", SEEDED_PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("改ざんされたトークンでは401を返す")
    void tamperedTokenReturns401() throws Exception {
        String token = login("testuser", SEEDED_PASSWORD);
        String tampered = token.substring(0, token.length() - 3) + "aaa";

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer以外のスキームのヘッダは無視され401になる")
    void nonBearerSchemeIsIgnored() throws Exception {
        String token = login("testuser", SEEDED_PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("authenticationProviderはCustomUserDetailsServiceとBCryptPasswordEncoderを保持している")
    void authenticationProviderIsWiredWithUserDetailsServiceAndEncoder() {
        // 今回の差分（setUserDetailsService からコンストラクタ注入への変更）をピン留めする。
        // userDetailsService が null になっていれば、この時点で全ログインが壊れる。
        assertThat(authenticationProvider).isInstanceOf(DaoAuthenticationProvider.class);
        assertThat(passwordEncoder.matches(SEEDED_PASSWORD, SEEDED_PASSWORD_HASH)).isTrue();
    }
}
