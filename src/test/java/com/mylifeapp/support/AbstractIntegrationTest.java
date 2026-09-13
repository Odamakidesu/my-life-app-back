package com.mylifeapp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 結合テストの共通土台。
 *
 * <p>本番と同じ MySQL 8 に対して検証する。コンテナは JVM ごとに1つだけ起動し、
 * テストクラスごとの起動・停止を避ける。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.42"))
                .withDatabaseName("mylifeapp_test");
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** data.sql が投入するローカル専用ユーザーのパスワード。 */
    protected static final String SEEDED_PASSWORD = "testpass";

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** ログインして JWT を取得する。 */
    protected String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * テスト用ユーザーを直接投入する。
     * 登録 API はパスワードポリシーやロール固定の対象なので、
     * 前提条件づくりには使わず DB に直接入れる。
     */
    protected long insertUser(String username, String bcryptHash, boolean enabled, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, enabled, role) VALUES (?, ?, ?, ?)",
                username, bcryptHash, enabled, role);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    /** SEEDED_PASSWORD に対応する BCrypt ハッシュ。 */
    protected static final String SEEDED_PASSWORD_HASH =
            "$2a$10$XgTnzOcuXBA1eFJco4SIN.bDWWJ37OrxAaSeC2vaIBW2xdX3C5HJy";

    protected long insertNote(long userId, String title) {
        jdbcTemplate.update("""
                INSERT INTO note (user_id, title, content, tags, is_important, is_pinned,
                                  deadline, is_completed, created_at, delete_flg)
                VALUES (?, ?, 'content', NULL, false, false, NULL, false, NOW(), false)
                """, userId, title);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM note WHERE user_id = ? AND title = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId, title);
    }
}
