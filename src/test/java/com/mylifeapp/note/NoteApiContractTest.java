package com.mylifeapp.note;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フロントエンド（my-life-app-front）が実際に送受信する形との契約。
 *
 * <p>リポジトリが分かれているため、片側だけを変えても気づけない。
 * ここでは NoteApiRepository が組み立てるペイロードをそのまま送り、
 * レスポンスのキー名が noteApiSchema の期待どおりであることを固定する。
 */
class NoteApiContractTest extends AbstractIntegrationTest {

    private String tokenFor(String username) throws Exception {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return login(username, SEEDED_PASSWORD);
    }

    @Test
    @DisplayName("一覧のJSONキーはフロントエンドが読む名前と一致する")
    void listResponseKeysMatchFrontendContract() throws Exception {
        String username = "contract-list";
        String token = tokenFor(username);
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        insertNote(userId, "契約確認");

        mockMvc.perform(get("/api/notes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                // noteApiSchema が必須として扱うキー
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].content").exists())
                .andExpect(jsonPath("$[0].created_at").exists())
                // 任意扱いだが名前が変わるとフラグ表示が壊れる
                .andExpect(jsonPath("$[0].isImportant").exists())
                .andExpect(jsonPath("$[0].isPinned").exists())
                .andExpect(jsonPath("$[0].isCompleted").exists())
                .andExpect(jsonPath("$[0].delete_flg").exists())
                // 所有者はクライアントに渡さない
                .andExpect(jsonPath("$[0].user_id").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }

    @Test
    @DisplayName("締切が未入力（空文字）の作成リクエストを受け付ける")
    void createAcceptsEmptyDeadlineString() throws Exception {
        // datetime-local を空のまま送ると、フロントは deadline に空文字を載せる。
        // これが 400 や 500 になると、締切なしのメモが一切作れなくなる。
        String token = tokenFor("contract-empty-deadline");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"締切なし\",\"content\":\"本文\",\"tags\":\"仕事\",\"deadline\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deadline").doesNotExist())
                .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    @DisplayName("datetime-local 形式（秒なし）の締切を受け付ける")
    void createAcceptsDatetimeLocalWithoutSeconds() throws Exception {
        String token = tokenFor("contract-deadline");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"締切あり\",\"content\":\"本文\",\"tags\":\"\",\"deadline\":\"2026-12-31T23:59\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deadline").value("2026-12-31T23:59:00"));
    }

    @Test
    @DisplayName("更新リクエストも空文字の締切を受け付ける")
    void updateAcceptsEmptyDeadlineString() throws Exception {
        String username = "contract-update";
        String token = tokenFor(username);
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        long noteId = insertNote(userId, "更新対象");

        mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"更新後\",\"content\":\"本文\",\"tags\":\"\",\"deadline\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("更新後"));
    }

    @Test
    @DisplayName("ページング引数はフロントが送る page/size の名前で効く")
    void paginationUsesFrontendParameterNames() throws Exception {
        String username = "contract-paging";
        String token = tokenFor(username);
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        insertNote(userId, "p1");
        insertNote(userId, "p2");
        insertNote(userId, "p3");

        // NoteApiRepository は size=500 で最後のページまで辿る
        mockMvc.perform(get("/api/notes?page=0&size=500").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/notes?page=1&size=500").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("エラー応答はフロントが読む message と traceId を含む")
    void errorResponseCarriesMessageAndTraceId() throws Exception {
        String token = tokenFor("contract-error");

        String body = mockMvc.perform(put("/api/notes/99999999")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"t\",\"content\":\"c\",\"tags\":\"\",\"deadline\":\"\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // traceId が無いと、利用者からの申告をサーバログと突き合わせられない
        assertThat(objectMapper.readTree(body).get("traceId")).isNotNull();
    }

    @Test
    @DisplayName("壊れたJSONは500ではなく400を返す")
    void malformedJsonReturnsBadRequest() throws Exception {
        String token = tokenFor("contract-malformed");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\": \"壊れている\","))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("締切に解釈できない文字列が来た場合も400を返す")
    void unparsableDeadlineReturnsBadRequest() throws Exception {
        String token = tokenFor("contract-baddate");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"t\",\"content\":\"c\",\"tags\":\"\",\"deadline\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("作成レスポンスの作成日時は保存された値と一致する（秒精度）")
    void createdAtInResponseMatchesPersistedValue() throws Exception {
        String token = tokenFor("contract-createdat");

        String body = mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"時刻確認\",\"content\":\"本文\",\"tags\":\"\",\"deadline\":\"\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var created = objectMapper.readTree(body);
        long noteId = created.get("id").asLong();
        String fromResponse = created.get("created_at").asText();

        // note.created_at は DATETIME（秒精度）。ナノ秒付きで返すと MySQL 側で丸められ、
        // 作成直後の表示とリロード後の表示が最大1秒ずれる。
        assertThat(fromResponse).doesNotContain(".");

        String fromDatabase = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s') FROM note WHERE id = ?",
                String.class, noteId);
        assertThat(fromResponse).isEqualTo(fromDatabase);
    }
}
