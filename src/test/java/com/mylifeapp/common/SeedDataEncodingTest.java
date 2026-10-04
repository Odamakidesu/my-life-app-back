package com.mylifeapp.common;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * シードデータの文字コード。
 *
 * <p>SQL ファイルの文字コードを指定しないと、JVM の既定文字コードで読まれる。
 * 開発機は gradle.properties で -Dfile.encoding=MS932 を指定しているため、
 * UTF-8 のシード（現在は db/seed/R__local_seed.sql、以前は data.sql）が MS932 として解釈され、
 * 日本語が壊れた状態で DB に入る。現在は spring.flyway.encoding=UTF-8 で防いでいる。
 *
 * <p>実際にこれで開発用 DB の tags と note のシードデータが全滅した。
 * DB へは「正しい UTF-8 として」壊れた文字が保存されるので、
 * 接続やテーブルの文字セットをいくら調べても原因に辿り着かない。
 * さらに MS932 に写像できないバイトは U+FFFD に潰れるため、
 * 後から逆変換しても元に戻らない。
 *
 * <p>壊れていても API は 200 を返し続けるので、これはテストでしか検知できない。
 */
class SeedDataEncodingTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("開発用シードのタグ名が文字化けせずに保存されている")
    void seededTagNamesAreNotMojibake() {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM tags WHERE user_id IS NULL ORDER BY id", String.class);

        assertThat(names).containsExactly("仕事", "プライベート", "勉強", "買い物");
    }

    @Test
    @DisplayName("開発用シードのメモ本文が文字化けせずに保存されている")
    void seededNoteTextIsNotMojibake() {
        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM note WHERE id = 1", String.class);
        String tags = jdbcTemplate.queryForObject(
                "SELECT tags FROM note WHERE id = 1", String.class);

        assertThat(title).isEqualTo("会議の準備");
        assertThat(tags).isEqualTo("仕事");
    }

    @Test
    @DisplayName("置換文字(U+FFFD)を含む行が存在しない")
    void noColumnContainsReplacementCharacter() {
        // U+FFFD が現れた時点で、元のバイトは失われている＝逆変換では戻せない。
        // 「化けているかもしれない」ではなく「もう戻せない」ことの検知になる。
        Integer corrupted = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM tags WHERE name LIKE '%�%')
                + (SELECT COUNT(*) FROM note
                    WHERE title LIKE '%�%' OR content LIKE '%�%'
                       OR IFNULL(tags,'') LIKE '%�%')
                """, Integer.class);

        assertThat(corrupted).isZero();
    }
}
