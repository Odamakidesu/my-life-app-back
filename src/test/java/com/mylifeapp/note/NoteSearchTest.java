package com.mylifeapp.note;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 一覧の検索・絞り込み・並べ替えと、締切の集計（サーバ側で行う）。
 */
class NoteSearchTest extends AbstractIntegrationTest {

    private String token(String username) throws Exception {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return login(username, SEEDED_PASSWORD);
    }

    @Test
    @DisplayName("キーワードはタイトルと本文を大文字小文字を区別せずに探し、LIKE の特殊文字は文字どおりに扱う")
    void keywordSearch() throws Exception {
        String token = token("search-keyword");
        createNote(token, "Apple pie", "", null, null);
        createNote(token, "banana", "", null, null);
        createNote(token, "100% juice", "", null, null);

        mockMvc.perform(get("/api/notes?q=APPLE").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[*].title", contains("Apple pie")));

        mockMvc.perform(get("/api/notes").param("q", "%").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("100% juice")));
    }

    @Test
    @DisplayName("タグを複数指定すると、すべてを持つメモだけが返る")
    void tagFilterRequiresAllTags() throws Exception {
        String token = token("search-tags");
        createNote(token, "両方", "仕事,勉強", null, null);
        createNote(token, "仕事だけ", "仕事", null, null);

        mockMvc.perform(get("/api/notes").param("tags", "仕事,勉強").header("Authorization", bearer(token)))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[*].title", contains("両方")));

        mockMvc.perform(get("/api/notes").param("tags", "仕事").param("sort", "CREATED")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("仕事だけ", "両方")));
    }

    @Test
    @DisplayName("ピン留め・スター・完了状態で絞り込める")
    void flagFilters() throws Exception {
        String token = token("search-flags");
        long pinned = createNote(token, "ピン", "", null, "\"isPinned\":true");
        createNote(token, "スター", "", null, "\"isImportant\":true");
        long done = createNote(token, "完了", "", null, null);
        mockMvc.perform(put("/api/notes/" + done + "/completed")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes?pinned=true").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].id", contains((int) pinned)));
        mockMvc.perform(get("/api/notes?important=true").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("スター")));
        mockMvc.perform(get("/api/notes?completed=true").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("完了")));
        mockMvc.perform(get("/api/notes?completed=false").header("Authorization", bearer(token)))
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    @DisplayName("優先度順はピン留め→スター→新しい順、締切順は未完了の締切が近い順で締切なしは最後")
    void sortOrders() throws Exception {
        String token = token("search-sort");
        createNote(token, "締切遠い", "", jstAfterHours(72), null);
        createNote(token, "締切なし", "", null, "\"isImportant\":true");
        createNote(token, "締切近い", "", jstAfterHours(2), "\"isPinned\":true");

        mockMvc.perform(get("/api/notes?sort=PRIORITY").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("締切近い", "締切なし", "締切遠い")));
        mockMvc.perform(get("/api/notes?sort=DEADLINE").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("締切近い", "締切遠い", "締切なし")));
    }

    @Test
    @DisplayName("集計は未完了の期限切れと24時間以内を数え、due で同じ条件に絞り込める")
    void summaryAndDueFilter() throws Exception {
        String token = token("search-due");
        createNote(token, "期限切れ", "", jstAfterHours(-3), null);
        createNote(token, "もうすぐ", "", jstAfterHours(5), null);
        createNote(token, "まだ先", "", jstAfterHours(48), null);
        long doneOverdue = createNote(token, "完了済みの期限切れ", "", jstAfterHours(-5), null);
        mockMvc.perform(put("/api/notes/" + doneOverdue + "/completed")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.dueSoon").value(1));

        mockMvc.perform(get("/api/notes?due=OVERDUE").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("期限切れ")));
        mockMvc.perform(get("/api/notes?due=SOON").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("もうすぐ")));
    }

    @Test
    @DisplayName("解釈できない sort は 500 ではなく 400")
    void unknownSortIsBadRequest() throws Exception {
        String token = token("search-bad-sort");

        mockMvc.perform(get("/api/notes?sort=unknown").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("他人のメモは検索に出ない")
    void searchIsScopedToOwner() throws Exception {
        String a = token("search-owner-a");
        String b = token("search-owner-b");
        createNote(b, "秘密のメモ", "仕事", null, null);

        mockMvc.perform(get("/api/notes?q=秘密").header("Authorization", bearer(a)))
                .andExpect(header().string("X-Total-Count", "0"));
    }
}
