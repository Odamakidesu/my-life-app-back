package com.mylifeapp.note;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * エクスポート（JSON / CSV）。自分のゴミ箱以外のメモだけが出る。
 */
class NoteExportTest extends AbstractIntegrationTest {

    private String token(String username) throws Exception {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return login(username, SEEDED_PASSWORD);
    }

    @Test
    @DisplayName("JSON は一覧 API と同じ形の配列で、ゴミ箱のメモと他人のメモは含まない")
    void exportsJson() throws Exception {
        String token = token("export-json");
        String other = token("export-json-other");
        createNote(token, "残る", "仕事", null, null);
        long trashed = createNote(token, "ゴミ箱", "", null, null);
        createNote(other, "他人", "", null, null);
        mockMvc.perform(put("/api/notes/" + trashed + "/deleted")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"delete_flg\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/export?format=json").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("残る"))
                .andExpect(jsonPath("$[0].tags").value("仕事"))
                .andExpect(jsonPath("$[0].created_at").exists());
    }

    @Test
    @DisplayName("CSV は BOM 付き UTF-8 で、数式に見える値は無害化される")
    void exportsCsv() throws Exception {
        String token = token("export-csv");
        createNote(token, "=1+1", "仕事,勉強", null, null);

        byte[] body = mockMvc.perform(get("/api/notes/export?format=csv").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body[0]).isEqualTo((byte) 0xEF);
        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).startsWith("id,title,content,tags,");
        assertThat(lines[1]).contains(",'=1+1,本文,\"仕事,勉強\",");
    }

    @Test
    @DisplayName("未対応の形式は 400")
    void rejectsUnknownFormat() throws Exception {
        String token = token("export-bad");

        mockMvc.perform(get("/api/notes/export?format=xml").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }
}
