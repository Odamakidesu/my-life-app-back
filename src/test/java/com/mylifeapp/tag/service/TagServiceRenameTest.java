package com.mylifeapp.tag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagServiceRenameTest {

    @Test
    @DisplayName("完全一致する要素だけを置き換える")
    void replacesExactMatchOnly() {
        assertThat(TagService.renameTag("仕事,旧名,旧名称", "旧名", "新名")).isEqualTo("仕事,新名,旧名称");
    }

    @Test
    @DisplayName("置き換えで重複したら1つにまとめ、前後の空白と空要素は落とす")
    void deduplicatesAndTrims() {
        assertThat(TagService.renameTag(" 旧名 ,新名,,", "旧名", "新名")).isEqualTo("新名");
    }

    @Test
    @DisplayName("null はそのまま返す")
    void keepsNull() {
        assertThat(TagService.renameTag(null, "a", "b")).isNull();
    }
}
