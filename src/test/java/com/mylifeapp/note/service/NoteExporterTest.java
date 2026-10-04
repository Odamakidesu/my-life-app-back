package com.mylifeapp.note.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteExporterTest {

    @Test
    @DisplayName("カンマ・引用符・改行を含む値は引用符で囲み、引用符は二重にする")
    void quotesSpecialCharacters() {
        assertThat(NoteExporter.escape("plain")).isEqualTo("plain");
        assertThat(NoteExporter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(NoteExporter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(NoteExporter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    @DisplayName("表計算ソフトが数式として扱う先頭文字は ' を付けて無害化する")
    void neutralizesFormulas() {
        assertThat(NoteExporter.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(NoteExporter.escape("+1")).isEqualTo("'+1");
        assertThat(NoteExporter.escape("-1")).isEqualTo("'-1");
        assertThat(NoteExporter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(NoteExporter.escape("")).isEmpty();
    }
}
