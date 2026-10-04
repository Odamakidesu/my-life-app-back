package com.mylifeapp.note;

import com.mylifeapp.note.support.TrashPurgeJob;
import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ゴミ箱に入れた日時と、保持期間を過ぎたメモの自動削除。
 */
class TrashRetentionTest extends AbstractIntegrationTest {

    @Autowired
    private TrashPurgeJob trashPurgeJob;

    private String token(String username) throws Exception {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return login(username, SEEDED_PASSWORD);
    }

    private void setDeleted(String token, long noteId, boolean value) throws Exception {
        mockMvc.perform(put("/api/notes/" + noteId + "/deleted")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"delete_flg\":" + value + "}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ゴミ箱に入れた日時が記録されて新しい順に並び、復元すると消える")
    void recordsDeletedAt() throws Exception {
        String token = token("trash-deleted-at");
        long first = createNote(token, "先に削除", "", null, null);
        long second = createNote(token, "後で削除", "", null, null);
        setDeleted(token, first, true);
        setDeleted(token, second, true);
        jdbcTemplate.update("UPDATE note SET deleted_at = deleted_at - INTERVAL 1 HOUR WHERE id = ?", first);

        mockMvc.perform(get("/api/notes/deleted").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[*].title", contains("後で削除", "先に削除")))
                .andExpect(jsonPath("$[0].deleted_at").isNotEmpty());

        setDeleted(token, first, false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM note WHERE id = ?", Boolean.class, first)).isTrue();
    }

    @Test
    @DisplayName("保持期間を過ぎたゴミ箱のメモだけが自動で完全に削除される")
    void purgesExpiredTrash() throws Exception {
        String token = token("trash-retention-purge");
        long expired = createNote(token, "期限切れのゴミ", "仕事", null, null);
        long recent = createNote(token, "最近のゴミ", "", null, null);
        long active = createNote(token, "一覧のメモ", "", null, null);
        setDeleted(token, expired, true);
        setDeleted(token, recent, true);
        jdbcTemplate.update(
                "UPDATE note SET deleted_at = NOW() - INTERVAL ? DAY WHERE id = ?",
                trashPurgeJob.retentionDays() + 1, expired);

        trashPurgeJob.purge();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note WHERE id = ?", Integer.class, expired))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note WHERE id IN (?, ?)", Integer.class, recent, active)).isEqualTo(2);
    }
}
