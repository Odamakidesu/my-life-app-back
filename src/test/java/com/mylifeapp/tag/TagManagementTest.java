package com.mylifeapp.tag;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * タグの作成・編集・削除と所有者分離。
 *
 * <p>共通タグ（user_id IS NULL）は全員に見えるが誰も編集できず、
 * 利用者のタグは本人にだけ見えて本人だけが編集できる。
 */
class TagManagementTest extends AbstractIntegrationTest {

    private record UserFixture(long id, String token) {
    }

    private UserFixture user(String username) throws Exception {
        long id = insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return new UserFixture(id, login(username, SEEDED_PASSWORD));
    }

    private long createTag(String token, String name, String color) throws Exception {
        String body = mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"color\":\"" + color + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.color").value(color))
                .andExpect(jsonPath("$.editable").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long sharedTagId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE name = ? AND user_id IS NULL", Long.class, name);
    }

    @Test
    @DisplayName("作成したタグは本人の一覧にだけ現れ、共通タグは編集不可として返る")
    void createdTagIsVisibleOnlyToOwner() throws Exception {
        UserFixture owner = user("tag-owner-visible");
        UserFixture other = user("tag-other-visible");
        createTag(owner.token(), "自分専用", "#112233");

        mockMvc.perform(get("/api/tags").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '自分専用')].editable", hasItem(true)))
                .andExpect(jsonPath("$[?(@.name == '仕事')].editable", hasItem(false)));

        mockMvc.perform(get("/api/tags").header("Authorization", bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem("自分専用"))));
    }

    @Test
    @DisplayName("共通タグや自分に見えるタグと同じ名前では作成できない")
    void rejectsDuplicateName() throws Exception {
        UserFixture owner = user("tag-dup");
        createTag(owner.token(), "重複チェック", "#112233");

        for (String name : new String[]{"仕事", "重複チェック"}) {
            mockMvc.perform(post("/api/tags")
                            .header("Authorization", bearer(owner.token()))
                            .contentType("application/json")
                            .content("{\"name\":\"" + name + "\",\"color\":\"#000000\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("別の利用者は同じ名前のタグを作れる（タグは利用者ごとに独立）")
    void sameNameAllowedForDifferentUsers() throws Exception {
        createTag(user("tag-same-a").token(), "同名タグ", "#112233");
        createTag(user("tag-same-b").token(), "同名タグ", "#445566");
    }

    @Test
    @DisplayName("カンマを含む名前や #RRGGBB でない色は 400")
    void rejectsInvalidInput() throws Exception {
        UserFixture owner = user("tag-invalid");

        mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content("{\"name\":\"a,b\",\"color\":\"#000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content("{\"name\":\"色不正\",\"color\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.color").exists());
    }

    @Test
    @DisplayName("改名すると自分のメモに付いた旧名も新名に置き換わる")
    void renamePropagatesToOwnNotes() throws Exception {
        UserFixture owner = user("tag-rename");
        long tagId = createTag(owner.token(), "旧名", "#112233");
        long noteId = insertNote(owner.id(), "改名対象");
        jdbcTemplate.update("UPDATE note SET tags = '仕事,旧名,旧名称' WHERE id = ?", noteId);

        mockMvc.perform(put("/api/tags/" + tagId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content("{\"name\":\"新名\",\"color\":\"#abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名"))
                .andExpect(jsonPath("$.color").value("#abcdef"));

        String tags = jdbcTemplate.queryForObject("SELECT tags FROM note WHERE id = ?", String.class, noteId);
        // 部分一致の「旧名称」は書き換えない
        assertThat(tags).isEqualTo("仕事,新名,旧名称");
    }

    @Test
    @DisplayName("共通タグは更新も削除もできず 404 になり、レコードは変化しない")
    void cannotModifySharedTag() throws Exception {
        UserFixture owner = user("tag-shared");
        long shared = sharedTagId("仕事");

        mockMvc.perform(put("/api/tags/" + shared)
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json")
                        .content("{\"name\":\"乗っ取り\",\"color\":\"#000000\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tags/" + shared).header("Authorization", bearer(owner.token())))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE id = ? AND name = '仕事' AND delete_flg = false",
                Integer.class, shared)).isEqualTo(1);
    }

    @Test
    @DisplayName("他人のタグは更新も削除もできず 404 になる")
    void cannotModifyAnotherUsersTag() throws Exception {
        long tagOfB = createTag(user("tag-owner-b").token(), "Bのタグ", "#112233");
        UserFixture a = user("tag-owner-a");

        mockMvc.perform(put("/api/tags/" + tagOfB)
                        .header("Authorization", bearer(a.token()))
                        .contentType("application/json")
                        .content("{\"name\":\"乗っ取り\",\"color\":\"#000000\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tags/" + tagOfB).header("Authorization", bearer(a.token())))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM tags WHERE id = ?", String.class, tagOfB)).isEqualTo("Bのタグ");
    }

    @Test
    @DisplayName("削除したタグは一覧から消え、同じ名前で作り直せる")
    void deleteHidesTagAndFreesName() throws Exception {
        UserFixture owner = user("tag-delete");
        long tagId = createTag(owner.token(), "消すタグ", "#112233");

        mockMvc.perform(delete("/api/tags/" + tagId).header("Authorization", bearer(owner.token())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tags").header("Authorization", bearer(owner.token())))
                .andExpect(jsonPath("$[*].name", not(hasItem("消すタグ"))));

        createTag(owner.token(), "消すタグ", "#445566");
    }
}
