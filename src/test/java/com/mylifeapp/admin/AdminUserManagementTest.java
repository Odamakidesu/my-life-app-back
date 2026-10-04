package com.mylifeapp.admin;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理画面の API（ユーザー一覧と、自分自身を変更できないこと）。
 */
class AdminUserManagementTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("管理者はユーザー一覧を取得でき、パスワードは含まれない")
    void adminListsUsers() throws Exception {
        String admin = login("admin", SEEDED_PASSWORD);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @DisplayName("一般ユーザーは一覧を取得できない（403）")
    void userCannotListUsers() throws Exception {
        insertUser("admin-list-user", SEEDED_PASSWORD_HASH, true, "USER");
        String user = login("admin-list-user", SEEDED_PASSWORD);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理者は自分自身を降格・無効化できない（400）")
    void adminCannotChangeSelf() throws Exception {
        String admin = login("admin", SEEDED_PASSWORD);
        long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'admin'", Long.class);

        mockMvc.perform(put("/api/admin/users/" + adminId + "/role")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/" + adminId + "/enabled")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("存在しないユーザーの変更は 404")
    void unknownUserIsNotFound() throws Exception {
        String admin = login("admin", SEEDED_PASSWORD);

        mockMvc.perform(put("/api/admin/users/99999999/enabled")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
    }
}
