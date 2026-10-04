package com.mylifeapp.note.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteQueryRepositoryTest {

    @Test
    @DisplayName("LIKE の特殊文字はエスケープされ、文字どおりに検索される")
    void escapesLikeWildcards() {
        assertThat(NoteQueryRepository.escapeLike("100%_\\")).isEqualTo("100\\%\\_\\\\");
        assertThat(NoteQueryRepository.escapeLike("普通の文字")).isEqualTo("普通の文字");
    }
}
