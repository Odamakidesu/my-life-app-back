package com.mylifeapp.note;

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
 * メモの所有者分離。
 *
 * <p>この振る舞いはこれまで存在しなかった（note に所有者列が無く、
 * 認証済みユーザーなら誰でも全ユーザーのメモを読み書き削除できた）。
 * 実装だけでなく、破られていないことをここで固定する。
 */
class NoteAuthorizationTest extends AbstractIntegrationTest {

    private record Fixture(String tokenA, String tokenB, long noteOfB) {
    }

    private Fixture setUpTwoUsers(String suffix) throws Exception {
        long userA = insertUser("owner-a-" + suffix, SEEDED_PASSWORD_HASH, true, "USER");
        long userB = insertUser("owner-b-" + suffix, SEEDED_PASSWORD_HASH, true, "USER");
        insertNote(userA, "A-note-" + suffix);
        long noteOfB = insertNote(userB, "B-note-" + suffix);
        return new Fixture(
                login("owner-a-" + suffix, SEEDED_PASSWORD),
                login("owner-b-" + suffix, SEEDED_PASSWORD),
                noteOfB);
    }

    @Test
    @DisplayName("メモ一覧には自分のメモだけが含まれる")
    void listReturnsOnlyOwnNotes() throws Exception {
        Fixture fixture = setUpTwoUsers("list");

        mockMvc.perform(get("/api/notes").header("Authorization", bearer(fixture.tokenA())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'A-note-list')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'B-note-list')]").doesNotExist());
    }

    @Test
    @DisplayName("他ユーザーのメモを更新しようとすると404を返しレコードは変化しない")
    void cannotUpdateAnotherUsersNote() throws Exception {
        Fixture fixture = setUpTwoUsers("update");

        mockMvc.perform(put("/api/notes/" + fixture.noteOfB())
                        .header("Authorization", bearer(fixture.tokenA()))
                        .contentType("application/json")
                        .content("{\"title\":\"hijacked\",\"content\":\"hijacked\"}"))
                .andExpect(status().isNotFound());

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM note WHERE id = ?", String.class, fixture.noteOfB());
        assertThat(title).isEqualTo("B-note-update");
    }

    @Test
    @DisplayName("他ユーザーのメモを論理削除しようとすると404を返しdelete_flgは変化しない")
    void cannotSoftDeleteAnotherUsersNote() throws Exception {
        Fixture fixture = setUpTwoUsers("delete");

        mockMvc.perform(put("/api/notes/" + fixture.noteOfB() + "/deleted")
                        .header("Authorization", bearer(fixture.tokenA()))
                        .contentType("application/json")
                        .content("{\"delete_flg\":true}"))
                .andExpect(status().isNotFound());

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT delete_flg FROM note WHERE id = ?", Boolean.class, fixture.noteOfB());
        assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("他ユーザーのメモの重要フラグを更新しようとすると404を返す")
    void cannotFlagAnotherUsersNote() throws Exception {
        Fixture fixture = setUpTwoUsers("flag");

        mockMvc.perform(put("/api/notes/" + fixture.noteOfB() + "/important")
                        .header("Authorization", bearer(fixture.tokenA()))
                        .contentType("application/json")
                        .content("{\"important\":true}"))
                .andExpect(status().isNotFound());

        Boolean important = jdbcTemplate.queryForObject(
                "SELECT is_important FROM note WHERE id = ?", Boolean.class, fixture.noteOfB());
        assertThat(important).isFalse();
    }

    @Test
    @DisplayName("作成リクエストにidを含めても既存メモは上書きされず新規作成される")
    void createIgnoresIdInBodyAndDoesNotOverwrite() throws Exception {
        Fixture fixture = setUpTwoUsers("massassign");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(fixture.tokenA()))
                        .contentType("application/json")
                        .content("{\"id\":" + fixture.noteOfB()
                                + ",\"title\":\"injected\",\"content\":\"injected\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(fixture.noteOfB())));

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM note WHERE id = ?", String.class, fixture.noteOfB());
        assertThat(title).isEqualTo("B-note-massassign");
    }

    @Test
    @DisplayName("存在しないIDを更新すると404を返す")
    void updatingMissingNoteReturns404() throws Exception {
        Fixture fixture = setUpTwoUsers("missing");

        mockMvc.perform(put("/api/notes/99999999")
                        .header("Authorization", bearer(fixture.tokenA()))
                        .contentType("application/json")
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("未認証でメモ一覧を取得しようとすると401を返す")
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未認証でメモを論理削除しようとすると401を返す")
    void softDeleteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/notes/1/deleted")
                        .contentType("application/json")
                        .content("{\"delete_flg\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("論理削除したメモは一覧に含まれない")
    void softDeletedNotesAreExcludedFromList() throws Exception {
        long userId = insertUser("owner-softdelete", SEEDED_PASSWORD_HASH, true, "USER");
        long noteId = insertNote(userId, "to-be-deleted");
        String token = login("owner-softdelete", SEEDED_PASSWORD);

        mockMvc.perform(put("/api/notes/" + noteId + "/deleted")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"delete_flg\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'to-be-deleted')]").doesNotExist());
    }

    @Test
    @DisplayName("本文更新は他のフラグを巻き添えで戻さない")
    void contentUpdateDoesNotResetOtherFlags() throws Exception {
        long userId = insertUser("owner-flags", SEEDED_PASSWORD_HASH, true, "USER");
        long noteId = insertNote(userId, "flag-preservation");
        String token = login("owner-flags", SEEDED_PASSWORD);

        mockMvc.perform(put("/api/notes/" + noteId + "/important")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"important\":true}"))
                .andExpect(status().isOk());

        // 本文だけを更新する。以前は全カラム UPDATE だったため is_important が false に戻っていた。
        mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"updated\",\"content\":\"updated\"}"))
                .andExpect(status().isOk());

        Boolean important = jdbcTemplate.queryForObject(
                "SELECT is_important FROM note WHERE id = ?", Boolean.class, noteId);
        assertThat(important).isTrue();
    }

    @Test
    @DisplayName("タイトルが空のメモ作成は400を返す")
    void createWithBlankTitleReturns400() throws Exception {
        long userId = insertUser("owner-validation", SEEDED_PASSWORD_HASH, true, "USER");
        insertNote(userId, "seed");
        String token = login("owner-validation", SEEDED_PASSWORD);

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"\",\"content\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty());
    }

    @Test
    @DisplayName("ピン留めと完了フラグは自分のメモに対して更新できる")
    void ownerCanUpdatePinnedAndCompletedFlags() throws Exception {
        long userId = insertUser("owner-pin", SEEDED_PASSWORD_HASH, true, "USER");
        long noteId = insertNote(userId, "pin-and-complete");
        String token = login("owner-pin", SEEDED_PASSWORD);

        mockMvc.perform(put("/api/notes/" + noteId + "/pinned")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"pinned\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/notes/" + noteId + "/completed")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        Boolean pinned = jdbcTemplate.queryForObject(
                "SELECT is_pinned FROM note WHERE id = ?", Boolean.class, noteId);
        Boolean completed = jdbcTemplate.queryForObject(
                "SELECT is_completed FROM note WHERE id = ?", Boolean.class, noteId);
        assertThat(pinned).isTrue();
        assertThat(completed).isTrue();
    }

    @Test
    @DisplayName("フラグ更新のボディが空の場合は400を返す")
    void flagUpdateWithMissingValueReturns400() throws Exception {
        long userId = insertUser("owner-nullflag", SEEDED_PASSWORD_HASH, true, "USER");
        long noteId = insertNote(userId, "null-flag");
        String token = login("owner-nullflag", SEEDED_PASSWORD);

        mockMvc.perform(put("/api/notes/" + noteId + "/pinned")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("size指定は上限で丸められ無制限取得はできない")
    void pageSizeIsCapped() throws Exception {
        long userId = insertUser("owner-paging", SEEDED_PASSWORD_HASH, true, "USER");
        insertNote(userId, "paging-1");
        insertNote(userId, "paging-2");
        String token = login("owner-paging", SEEDED_PASSWORD);

        mockMvc.perform(get("/api/notes?page=0&size=1").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Total-Count", "2"));

        // 上限を超える size を要求しても MAX_PAGE_SIZE で丸められる（例外にはしない）
        mockMvc.perform(get("/api/notes?size=100000").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
