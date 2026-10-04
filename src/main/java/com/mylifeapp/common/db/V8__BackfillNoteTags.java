package com.mylifeapp.common.db;

import com.mylifeapp.tag.support.TagNames;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * note.tags（カンマ区切りの名前）から note_tags（V7）へ既存の対応を写す。
 *
 * <p>SQL ではなく Java で書くのは、名前の分割と照合を SQL で行うと
 * note.tags と tags.name の照合順序が環境ごとに違った場合に「Illegal mix of collations」で
 * マイグレーションが失敗し、アプリが起動しなくなるため。名前はバインド変数で比較するので、
 * アプリの通常の処理（NoteTagService）と同じ照合になる。
 *
 * <p>名前の解決順はアプリと同じ: 見えるタグ（共通・自分）→ 自分や共通の削除済みタグ → 無ければ自分のタグとして作る。
 * 何度流しても対応が重複しない（INSERT IGNORE）。
 *
 * <p>Flyway の classpath 走査ではなく Spring の Bean として登録する
 * （Spring Boot が javaMigrations に渡す）。実行可能 jar の中のクラス走査に依存しないため。
 */
@Component
public class V8__BackfillNoteTags extends BaseJavaMigration {

    /** 名前だけが分かっていて色の無いタグに付ける色（画面の既定色と同じ） */
    static final String DEFAULT_COLOR = "#6c757d";

    @Override
    public void migrate(Context context) {
        JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        Map<String, Long> resolved = new HashMap<>();

        List<Map<String, Object>> notes = db.queryForList(
                "SELECT id, user_id, tags FROM note WHERE tags IS NOT NULL AND tags <> ''");
        for (Map<String, Object> note : notes) {
            long noteId = ((Number) note.get("id")).longValue();
            long userId = ((Number) note.get("user_id")).longValue();
            List<String> names = TagNames.parse((String) note.get("tags"));
            int order = 0;
            for (String name : names) {
                String truncated = name.length() > TagNames.MAX_LENGTH ? name.substring(0, TagNames.MAX_LENGTH) : name;
                String key = userId + "\u0000" + truncated.toLowerCase(Locale.ROOT);
                Long tagId = resolved.computeIfAbsent(key, k -> resolveOrCreate(db, userId, truncated));
                db.update("INSERT IGNORE INTO note_tags (note_id, tag_id, sort_order) VALUES (?, ?, ?)",
                        noteId, tagId, order++);
            }
        }
    }

    private static Long resolveOrCreate(JdbcTemplate db, long userId, String name) {
        List<Long> existing = db.queryForList("""
                SELECT id FROM tags
                WHERE (user_id IS NULL OR user_id = ?) AND name = ?
                ORDER BY delete_flg, id
                LIMIT 1
                """, Long.class, userId, name);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO tags (user_id, name, color, created_at, updated_at, delete_flg)
                    VALUES (?, ?, ?, NOW(), NOW(), false)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, DEFAULT_COLOR);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }
}
