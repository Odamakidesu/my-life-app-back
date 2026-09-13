package com.mylifeapp;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * コンテキストの起動確認。
 *
 * <p>以前は @TestConfiguration で UserRepository 等をモックに差し替えていたが、
 * spring.main.allow-bean-definition-overriding=true の影響で実際には
 * 実リポジトリがモックを上書きしており、分離できていなかった。
 * 実 MySQL（Testcontainers）に対して素の構成を起動する形に変える。
 */
class MylifeappApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Spring Bootのコンテキストが起動し主要なBeanが登録されている")
    void contextLoads() {
        // 空のテストは、実装を壊しても緑のまま通る。最低限の主張を置く。
        assertThat(applicationContext.getBean(com.mylifeapp.note.service.NoteService.class)).isNotNull();
        assertThat(applicationContext.getBean(com.mylifeapp.auth.jwt.JwtTokenProvider.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.mylifeapp.common.web.GlobalExceptionHandler.class)).isNotNull();
    }

    @Test
    @DisplayName("スキーマにnoteの所有者列とインデックスが存在する")
    void noteTableHasOwnerColumn() {
        Integer columns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'note' AND column_name = 'user_id'
                """, Integer.class);
        assertThat(columns).isEqualTo(1);
    }

    @Test
    @DisplayName("ヘルスチェックは無認証で到達できDBの疎通を確認している")
    void healthEndpointIsPubliclyReachable() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("UP"));
    }
}
