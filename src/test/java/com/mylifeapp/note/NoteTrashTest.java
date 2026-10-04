package com.mylifeapp.note;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ゴミ箱（論理削除済みメモの一覧・復元・完全削除）。
 */
class NoteTrashTest extends AbstractIntegrationTest {

    private record Fixture(long userId, String token) {
    }

    private Fixture user(String username) throws Exception {
        long id = insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return new Fixture(id, login(username, SEEDED_PASSWORD));
    }

    private void softDelete(String token, long noteId) throws Exception {
        mockMvc.perform(put("/api/notes/" + noteId + "/deleted")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"delete_flg\":true}"))
                .andExpect(status().isOk());
    }

    private int countNote(long noteId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note WHERE id = ?", Integer.class, noteId);
    }

    @Test
    @DisplayName("ゴミ箱には自分の削除済みメモだけが並び、件数がヘッダに載る")
    void listsOnlyOwnDeletedNotes() throws Exception {
        Fixture a = user("trash-list-a");
        Fixture b = user("trash-list-b");
        long deletedA = insertNote(a.userId(), "A-deleted");
        insertNote(a.userId(), "A-active");
        long deletedB = insertNote(b.userId(), "B-deleted");
        softDelete(a.token(), deletedA);
        softDelete(b.token(), deletedB);

        mockMvc.perform(get("/api/notes/deleted").header("Authorization", bearer(a.token())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("A-deleted"))
                .andExpect(jsonPath("$[0].delete_flg").value(true));
    }

    @Test
    @DisplayName("復元したメモは一覧に戻りゴミ箱から消える")
    void restoreMovesNoteBack() throws Exception {
        Fixture a = user("trash-restore");
        long noteId = insertNote(a.userId(), "restore-me");
        softDelete(a.token(), noteId);

        mockMvc.perform(put("/api/notes/" + noteId + "/deleted")
                        .header("Authorization", bearer(a.token()))
                        .contentType("application/json")
                        .content("{\"delete_flg\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes").header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$[?(@.title == 'restore-me')]").exists());
        mockMvc.perform(get("/api/notes/deleted").header("Authorization", bearer(a.token())))
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    @DisplayName("ゴミ箱のメモは完全に削除できる")
    void deletesPermanentlyFromTrash() throws Exception {
        Fixture a = user("trash-purge");
        long noteId = insertNote(a.userId(), "purge-me");
        softDelete(a.token(), noteId);

        mockMvc.perform(delete("/api/notes/" + noteId).header("Authorization", bearer(a.token())))
                .andExpect(status().isNoContent());

        assertThat(countNote(noteId)).isZero();
    }

    @Test
    @DisplayName("ゴミ箱に入っていないメモは完全削除できず 404 になり、レコードは残る")
    void cannotPurgeActiveNote() throws Exception {
        Fixture a = user("trash-active");
        long noteId = insertNote(a.userId(), "still-active");

        mockMvc.perform(delete("/api/notes/" + noteId).header("Authorization", bearer(a.token())))
                .andExpect(status().isNotFound());

        assertThat(countNote(noteId)).isOne();
    }

    @Test
    @DisplayName("他人のゴミ箱のメモは完全削除できず 404 になり、レコードは残る")
    void cannotPurgeAnotherUsersNote() throws Exception {
        Fixture a = user("trash-owner-a");
        Fixture b = user("trash-owner-b");
        long noteOfB = insertNote(b.userId(), "B-trash");
        softDelete(b.token(), noteOfB);

        mockMvc.perform(delete("/api/notes/" + noteOfB).header("Authorization", bearer(a.token())))
                .andExpect(status().isNotFound());

        assertThat(countNote(noteOfB)).isOne();
    }
}
