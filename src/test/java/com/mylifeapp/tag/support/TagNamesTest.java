package com.mylifeapp.tag.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagNamesTest {

    @Test
    @DisplayName("空白を除き、空の要素と大文字小文字だけ違う重複を捨て、順序は保つ")
    void parsesAndNormalizes() {
        assertThat(TagNames.parse(" 仕事 ,Alpha,,alpha, 勉強")).containsExactly("仕事", "Alpha", "勉強");
    }

    @Test
    @DisplayName("null と空文字は空のリスト")
    void emptyInputs() {
        assertThat(TagNames.parse(null)).isEmpty();
        assertThat(TagNames.parse("  ")).isEmpty();
        assertThat(TagNames.join(TagNames.parse(null))).isEmpty();
    }
}
