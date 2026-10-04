package com.mylifeapp.note;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 繰り返しタスク: 完了にすると次の締切のメモが作られる。
 */
class NoteRecurrenceTest extends AbstractIntegrationTest {

    private String token(String username) throws Exception {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return login(username, SEEDED_PASSWORD);
    }

    private String complete(String token, long noteId, boolean value) throws Exception {
        return mockMvc.perform(put("/api/notes/" + noteId + "/completed")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"completed\":" + value + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("毎週のメモを完了にすると、1週間後の締切で同じ内容とタグのメモが作られ、繰り返しはそちらへ移る")
    void completingWeeklyNoteCreatesNext() throws Exception {
        String token = token("recurrence-weekly");
        String deadline = jstAfterHours(1);
        long noteId = createNote(token, "週次レポート", "仕事", deadline, "\"recurrence\":\"WEEKLY\"");

        long nextId = objectMapper.readTree(complete(token, noteId, true)).get("nextNoteId").asLong();

        assertThat(nextId).isNotEqualTo(noteId);
        LocalDateTime next = jdbcTemplate.queryForObject(
                "SELECT deadline FROM note WHERE id = ?", LocalDateTime.class, nextId);
        assertThat(next).isEqualTo(LocalDateTime.parse(deadline).plusWeeks(1));
        assertThat(jdbcTemplate.queryForObject("SELECT recurrence FROM note WHERE id = ?", String.class, nextId))
                .isEqualTo("WEEKLY");
        assertThat(jdbcTemplate.queryForObject("SELECT recurrence FROM note WHERE id = ?", String.class, noteId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT t.name FROM note_tags nt JOIN tags t ON t.id = nt.tag_id WHERE nt.note_id = ?
                """, String.class, nextId)).isEqualTo("仕事");
    }

    @Test
    @DisplayName("完了を戻してもう一度完了にしても、次回分は二重に作られない")
    void recompletingDoesNotDuplicate() throws Exception {
        String token = token("recurrence-twice");
        long noteId = createNote(token, "毎日の運動", "", jstAfterHours(1), "\"recurrence\":\"DAILY\"");

        complete(token, noteId, true);
        complete(token, noteId, false);
        String second = complete(token, noteId, true);

        assertThat(objectMapper.readTree(second).get("nextNoteId").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note WHERE title = '毎日の運動'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("締切を大きく過ぎてから完了にしても、次回分は今より後の締切で作られる")
    void nextDeadlineIsInTheFuture() throws Exception {
        String token = token("recurrence-late");
        long noteId = createNote(token, "遅れた日課", "", jstAfterHours(-24 * 3 - 1), "\"recurrence\":\"DAILY\"");

        long nextId = objectMapper.readTree(complete(token, noteId, true)).get("nextNoteId").asLong();

        LocalDateTime next = jdbcTemplate.queryForObject(
                "SELECT deadline FROM note WHERE id = ?", LocalDateTime.class, nextId);
        assertThat(next).isAfter(LocalDateTime.now(ZoneId.of("Asia/Tokyo")));
    }

    @Test
    @DisplayName("繰り返しの無いメモを完了にしても次回分は作られない")
    void plainNoteDoesNotRepeat() throws Exception {
        String token = token("recurrence-none");
        long noteId = createNote(token, "一度きり", "", jstAfterHours(1), null);

        assertThat(objectMapper.readTree(complete(token, noteId, true)).get("nextNoteId").isNull()).isTrue();
    }

    @Test
    @DisplayName("締切の無いメモに繰り返しは設定できない（400）")
    void recurrenceRequiresDeadline() throws Exception {
        String token = token("recurrence-no-deadline");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"t\",\"content\":\"c\",\"tags\":\"\",\"deadline\":\"\",\"recurrence\":\"DAILY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("繰り返しを設定するには締切を入力してください。"));
    }
}
