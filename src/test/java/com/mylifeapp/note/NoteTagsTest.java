package com.mylifeapp.note;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * メモとタグの対応（note_tags）。API の tags は従来どおりカンマ区切りの名前。
 */
class NoteTagsTest extends AbstractIntegrationTest {

    private record Fixture(long id, String token) {
    }

    private Fixture user(String username) throws Exception {
        long id = insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return new Fixture(id, login(username, SEEDED_PASSWORD));
    }

    private long createTag(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"color\":\"#123456\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("付けた順と名前が保たれ、空白と大文字小文字だけ違う重複はまとめられる")
    void keepsOrderAndNormalizes() throws Exception {
        Fixture a = user("note-tags-order");
        createTag(a.token(), "Alpha");

        long noteId = createNote(a.token(), "順序", " 勉強 ,Alpha,alpha,,仕事", null, null);

        mockMvc.perform(get("/api/notes").header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$[0].id").value(noteId))
                .andExpect(jsonPath("$[0].tags").value("勉強,Alpha,仕事"));
    }

    @Test
    @DisplayName("タグを改名すると、そのタグを付けたメモの表示も新しい名前になる")
    void renameIsReflectedThroughNoteTags() throws Exception {
        Fixture a = user("note-tags-rename");
        long tagId = createTag(a.token(), "旧名");
        createNote(a.token(), "改名", "旧名,仕事", null, null);

        mockMvc.perform(put("/api/tags/" + tagId)
                        .header("Authorization", bearer(a.token()))
                        .contentType("application/json")
                        .content("{\"name\":\"新名\",\"color\":\"#123456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes").param("tags", "新名").header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tags").value("新名,仕事"));
    }

    @Test
    @DisplayName("削除したタグの名前はメモに残り、編集して保存しても消えない")
    void deletedTagNameStaysOnNote() throws Exception {
        Fixture a = user("note-tags-deleted");
        long tagId = createTag(a.token(), "消すタグ");
        long noteId = createNote(a.token(), "残る名前", "消すタグ", null, null);
        mockMvc.perform(delete("/api/tags/" + tagId).header("Authorization", bearer(a.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", bearer(a.token()))
                        .contentType("application/json")
                        .content("{\"title\":\"残る名前\",\"content\":\"本文\",\"tags\":\"消すタグ\",\"deadline\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").value("消すタグ"));

        // 削除済みのタグに付け直しただけで、新しいタグは作られていない
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE user_id = ? AND name = '消すタグ'", Integer.class, a.id()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("まだ無い名前は自分のタグとして作られ、他人には見えない")
    void unknownNameCreatesOwnTag() throws Exception {
        Fixture a = user("note-tags-new-a");
        Fixture b = user("note-tags-new-b");

        createNote(a.token(), "新しい名前", "はじめての名前", null, null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE user_id = ? AND name = 'はじめての名前'", Integer.class, a.id()))
                .isEqualTo(1);
        mockMvc.perform(get("/api/tags").header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$[?(@.name == 'はじめての名前')]").doesNotExist());
    }

    @Test
    @DisplayName("51文字以上のタグ名は 400")
    void tooLongTagNameIsRejected() throws Exception {
        Fixture a = user("note-tags-long");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(a.token()))
                        .contentType("application/json")
                        .content("{\"title\":\"t\",\"content\":\"c\",\"tags\":\"" + "あ".repeat(51) + "\",\"deadline\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ゴミ箱から完全に削除したメモの対応も消える")
    void permanentDeleteRemovesLinks() throws Exception {
        Fixture a = user("note-tags-purge");
        long noteId = createNote(a.token(), "消える", "仕事", null, null);
        mockMvc.perform(put("/api/notes/" + noteId + "/deleted")
                        .header("Authorization", bearer(a.token()))
                        .contentType("application/json")
                        .content("{\"delete_flg\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/notes/" + noteId).header("Authorization", bearer(a.token())))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_tags WHERE note_id = ?", Integer.class, noteId)).isZero();
    }
}
