package com.mylifeapp.tag;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フロントエンド（tagApiSchema）が読むタグ一覧の形との契約。
 */
class TagApiContractTest extends AbstractIntegrationTest {

    private String token(String username) throws Exception {
        insertUser(username, SEEDED_PASSWORD_HASH, true, "USER");
        return login(username, SEEDED_PASSWORD);
    }

    @Test
    @DisplayName("一覧は id / name / color だけを返し、永続化の項目を出さない")
    void listExposesOnlyContractFields() throws Exception {
        mockMvc.perform(get("/api/tags").header("Authorization", bearer(token("tag-contract"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].color").exists())
                .andExpect(jsonPath("$[0].created_at").doesNotExist())
                .andExpect(jsonPath("$[0].updated_at").doesNotExist())
                .andExpect(jsonPath("$[0].delete_flg").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].deleteFlg").doesNotExist());
    }

    @Test
    @DisplayName("論理削除したタグは一覧に含めない")
    void listExcludesDeletedTags() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO tags (name, color, created_at, updated_at, delete_flg)
                VALUES ('削除済みタグ', '#000000', NOW(), NOW(), 1)
                """);

        mockMvc.perform(get("/api/tags").header("Authorization", bearer(token("tag-deleted"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("仕事")))
                .andExpect(jsonPath("$[*].name", not(hasItem("削除済みタグ"))));
    }
}
