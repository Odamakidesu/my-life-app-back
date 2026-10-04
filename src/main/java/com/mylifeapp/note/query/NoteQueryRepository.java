package com.mylifeapp.note.query;

import com.mylifeapp.note.entity.Note;
import com.mylifeapp.note.entity.Recurrence;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 条件が利用者の指定で変わるメモの一覧（検索・絞り込み・並べ替え・集計）。
 *
 * <p>以前は画面が全件（最大 500 件 × 50 ページ）を取得してブラウザで絞り込んでいた。
 * メモが増えるほど初回表示が遅くなり、通信量も増えるため、条件と並べ替えを SQL に落として
 * 必要なページだけを返す。
 *
 * <p>SQL の断片はすべてこのクラスの定数か列挙型の値から組み立て、利用者の入力は必ずバインド変数で渡す。
 * 所有者の条件（n.user_id = :userId AND n.delete_flg = false）は常に先頭に入る。
 */
@Repository
public class NoteQueryRepository {

    private static final long DUE_SOON_HOURS = 24;

    private static final RowMapper<Note> NOTE_ROW_MAPPER = NoteQueryRepository::mapNote;

    private final NamedParameterJdbcTemplate jdbc;

    public NoteQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Note> search(long userId, NoteSearchCriteria criteria, LocalDateTime now, int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(userId, criteria, now, params);
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(
                "SELECT n.* FROM note n WHERE " + where
                        + " ORDER BY " + criteria.sort().orderBy
                        + " LIMIT :limit OFFSET :offset",
                params, NOTE_ROW_MAPPER);
    }

    public long count(long userId, NoteSearchCriteria criteria, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(userId, criteria, now, params);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM note n WHERE " + where, params, Long.class);
        return count == null ? 0 : count;
    }

    /** 一覧の上部に出す集計（全件・期限切れ・24時間以内）。 */
    public Map<String, Object> summarize(long userId, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("now", now)
                .addValue("soon", now.plusHours(DUE_SOON_HOURS));
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(is_completed = false AND deadline < :now), 0) AS overdue,
                       COALESCE(SUM(is_completed = false AND deadline >= :now AND deadline <= :soon), 0) AS due_soon
                FROM note
                WHERE user_id = :userId AND delete_flg = false
                """, params);
    }

    private static String where(long userId, NoteSearchCriteria criteria, LocalDateTime now,
                                MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("n.user_id = :userId AND n.delete_flg = false");
        params.addValue("userId", userId);

        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            sql.append(" AND (n.title LIKE :keyword OR n.content LIKE :keyword)");
            params.addValue("keyword", "%" + escapeLike(criteria.keyword().trim()) + "%");
        }
        if (Boolean.TRUE.equals(criteria.pinned())) {
            sql.append(" AND n.is_pinned = true");
        }
        if (Boolean.TRUE.equals(criteria.important())) {
            sql.append(" AND n.is_important = true");
        }
        if (criteria.completed() != null) {
            sql.append(" AND n.is_completed = :completed");
            params.addValue("completed", criteria.completed());
        }
        if (criteria.due() != null) {
            params.addValue("now", now);
            switch (criteria.due()) {
                case OVERDUE -> sql.append(" AND n.is_completed = false AND n.deadline < :now");
                case SOON -> {
                    sql.append(" AND n.is_completed = false AND n.deadline >= :now AND n.deadline <= :soon");
                    params.addValue("soon", now.plusHours(DUE_SOON_HOURS));
                }
            }
        }
        if (!criteria.tags().isEmpty()) {
            // 指定したタグをすべて持つメモ。名前の比較は tags.name の照合順序（大文字小文字を区別しない）に従う。
            sql.append("""
                     AND n.id IN (
                        SELECT nt.note_id FROM note_tags nt JOIN tags t ON t.id = nt.tag_id
                        WHERE t.name IN (:tagNames)
                        GROUP BY nt.note_id
                        HAVING COUNT(DISTINCT t.name) = :tagCount)""");
            params.addValue("tagNames", criteria.tags()).addValue("tagCount", criteria.tags().size());
        }
        return sql.toString();
    }

    /** LIKE の特殊文字（\ % _）を文字どおりに扱わせる。MySQL の既定のエスケープ文字は \ 。 */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Note mapNote(ResultSet rs, int rowNum) throws SQLException {
        Note note = new Note();
        note.setId(rs.getLong("id"));
        note.setUserId(rs.getLong("user_id"));
        note.setTitle(rs.getString("title"));
        note.setContent(rs.getString("content"));
        note.setTags(rs.getString("tags"));
        note.setIsImportant(rs.getBoolean("is_important"));
        note.setIsPinned(rs.getBoolean("is_pinned"));
        note.setIsCompleted(rs.getBoolean("is_completed"));
        note.setDeleteFlg(rs.getBoolean("delete_flg"));
        // DATETIME は壁時計の値として読む（Timestamp を経由すると JVM のタイムゾーンで変換される）
        note.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        note.setDeadline(rs.getObject("deadline", LocalDateTime.class));
        note.setDeletedAt(rs.getObject("deleted_at", LocalDateTime.class));
        String recurrence = rs.getString("recurrence");
        note.setRecurrence(recurrence == null ? null : Recurrence.valueOf(recurrence));
        return note;
    }
}
