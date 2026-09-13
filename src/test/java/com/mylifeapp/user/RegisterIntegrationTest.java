package com.mylifeapp.user;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterIntegrationTest extends AbstractIntegrationTest {

    private static final String STRONG_PASSWORD = "correct-horse-battery";

    @Test
    @DisplayName("roleを指定して登録してもADMIN権限は得られない")
    void registerCannotSelfAssignAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"selfadmin\",\"password\":\"" + STRONG_PASSWORD
                                + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE username = ?", String.class, "selfadmin");
        assertThat(role).isEqualTo("USER");

        // 実際に管理者専用エンドポイントへ到達できないことまで確認する。
        String token = login("selfadmin", STRONG_PASSWORD);
        mockMvc.perform(get("/api/auth/admin-only").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("登録レスポンスにパスワードハッシュが含まれない")
    void registerResponseNeverExposesPasswordHash() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"nohash\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("registerUserは生パスワードではなくBCryptハッシュを保存する")
    void registerStoresBcryptHashNotRawPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"hashed\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE username = ?", String.class, "hashed");
        assertThat(stored).startsWith("$2a$").isNotEqualTo(STRONG_PASSWORD);
    }

    @Test
    @DisplayName("既に存在するユーザー名で登録すると409を返す")
    void duplicateUsernameReturns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"dupe\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        // 以前は一意制約違反が 500 として漏れ、200 との差分でユーザー名を列挙できた。
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"dupe\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("短すぎるパスワードでの登録は400を返す")
    void weakPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"weakpass\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("管理者はロール変更APIで昇格でき一般ユーザーは403になる")
    void onlyAdminCanChangeRoles() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"promoteme\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        long targetId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "promoteme");

        String userToken = login("promoteme", STRONG_PASSWORD);
        mockMvc.perform(put("/api/admin/users/" + targetId + "/role")
                        .header("Authorization", bearer(userToken))
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        String adminToken = login("admin", SEEDED_PASSWORD);
        mockMvc.perform(put("/api/admin/users/" + targetId + "/role")
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("ADMINロールのユーザーはadmin-onlyに200でアクセスできる")
    void adminReachesAdminOnlyEndpoint() throws Exception {
        String adminToken = login("admin", SEEDED_PASSWORD);

        // 以前は shouldNotFilter が /api/auth/ 配下の JWT 検証をスキップしていたため、
        // 正規の ADMIN でさえ常に匿名扱いになりこのエンドポイントに到達できなかった。
        mockMvc.perform(get("/api/auth/admin-only").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理者が無効化したユーザーは以後ログインできない")
    void adminCanDisableUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"tobedisabled\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        long targetId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "tobedisabled");

        String adminToken = login("admin", SEEDED_PASSWORD);
        mockMvc.perform(put("/api/admin/users/" + targetId + "/enabled")
                        .header("Authorization", bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"tobedisabled\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
