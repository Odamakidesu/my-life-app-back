package com.mylifeapp.note.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * メモとタグの対応（note_tags）。
 *
 * <p>所有者の確認は呼び出し側（NoteService）がメモを自分のものとして取得・更新できた後にだけ呼ぶことで行う。
 * ここに渡す note_id は必ずその確認を通ったもの。
 */
@Repository
public class NoteTagRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public NoteTagRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** メモのタグを付け替える（順序は tagIds の順）。 */
    public void replace(long noteId, List<Long> tagIds) {
        jdbc.update("DELETE FROM note_tags WHERE note_id = :noteId", Map.of("noteId", noteId));
        if (tagIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] rows = new MapSqlParameterSource[tagIds.size()];
        for (int i = 0; i < tagIds.size(); i++) {
            rows[i] = new MapSqlParameterSource()
                    .addValue("noteId", noteId)
                    .addValue("tagId", tagIds.get(i))
                    .addValue("sortOrder", i);
        }
        jdbc.batchUpdate(
                "INSERT INTO note_tags (note_id, tag_id, sort_order) VALUES (:noteId, :tagId, :sortOrder)", rows);
    }

    /** メモごとのタグ名（付けた順）。タグの無いメモはキーに含まれない。 */
    public Map<Long, List<String>> findNamesByNoteIds(Collection<Long> noteIds) {
        Map<Long, List<String>> names = new HashMap<>();
        if (noteIds.isEmpty()) {
            return names;
        }
        jdbc.query("""
                SELECT nt.note_id, t.name
                FROM note_tags nt JOIN tags t ON t.id = nt.tag_id
                WHERE nt.note_id IN (:noteIds)
                ORDER BY nt.note_id, nt.sort_order
                """,
                Map.of("noteIds", noteIds),
                rs -> {
                    names.computeIfAbsent(rs.getLong("note_id"), id -> new ArrayList<>()).add(rs.getString("name"));
                });
        return names;
    }

    /** メモに付いているタグの ID（付けた順）。 */
    public List<Long> findTagIds(long noteId) {
        return jdbc.queryForList(
                "SELECT tag_id FROM note_tags WHERE note_id = :noteId ORDER BY sort_order",
                Map.of("noteId", noteId), Long.class);
    }
}
