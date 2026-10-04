package com.mylifeapp.auth.session;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ログアウトとパスワード変更によるトークンの無効化。
 */
class SessionTest extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "a-brand-new-password-123";

    private void user(String username) {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
    }

    private void expectStatus(String token, int status) throws Exception {
        mockMvc.perform(get("/api/notes").header("Authorization", bearer(token)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(status));
    }

    private String changePassword(String token, String current, String next, int expectedStatus) throws Exception {
        return mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("ログアウトしたトークンは使えなくなるが、別の端末のトークンはそのまま使える")
    void logoutRevokesOnlyThatToken() throws Exception {
        user("session-logout");
        String thisDevice = login("session-logout", SEEDED_PASSWORD);
        String otherDevice = login("session-logout", SEEDED_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(thisDevice)))
                .andExpect(status().isNoContent());

        expectStatus(thisDevice, 401);
        expectStatus(otherDevice, 200);
    }

    @Test
    @DisplayName("ログアウトは認証が必要（トークン無しは 401）")
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("パスワードを変えると全端末の古いトークンが無効になり、返された新しいトークンと新しいパスワードが使える")
    void passwordChangeRevokesOldTokens() throws Exception {
        user("session-password");
        String thisDevice = login("session-password", SEEDED_PASSWORD);
        String otherDevice = login("session-password", SEEDED_PASSWORD);

        String body = changePassword(thisDevice, SEEDED_PASSWORD, NEW_PASSWORD, 200);
        String newToken = objectMapper.readTree(body).get("token").asString();

        expectStatus(thisDevice, 401);
        expectStatus(otherDevice, 401);
        expectStatus(newToken, 200);
        login("session-password", NEW_PASSWORD);
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"session-password\",\"password\":\"" + SEEDED_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("現在のパスワードが違うと 400 で、パスワードもトークンも変わらない")
    void wrongCurrentPasswordIsRejected() throws Exception {
        user("session-wrong");
        String token = login("session-wrong", SEEDED_PASSWORD);

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"not-my-password\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("現在のパスワードが正しくありません。"));

        expectStatus(token, 200);
    }

    @Test
    @DisplayName("新しいパスワードが短い・現在と同じ場合は 400")
    void weakOrSamePasswordIsRejected() throws Exception {
        user("session-weak");
        String token = login("session-weak", SEEDED_PASSWORD);

        changePassword(token, SEEDED_PASSWORD, "short", 400);

        // 現在と同じ（testpass は12文字未満なので、先に12文字以上のパスワードへ変えてから試す）
        String body = changePassword(token, SEEDED_PASSWORD, NEW_PASSWORD, 200);
        String newToken = objectMapper.readTree(body).get("token").asString();
        changePassword(newToken, NEW_PASSWORD, NEW_PASSWORD, 400);
    }
}
