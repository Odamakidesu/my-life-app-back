package com.mylifeapp.note.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mylifeapp.note.model.Note;

import java.time.LocalDateTime;

/**
 * メモのレスポンス。
 *
 * <p>JSON のフィールド名は既存のフロントエンドが読んでいるものをそのまま維持する
 * （created_at / delete_flg / isImportant ...）。Java 側のフィールドは camelCase に
 * 統一したが、それを API の契約に波及させると既存クライアントが壊れるため、
 * ここで明示的に写像する。user_id は外に出さない。
 */
public record NoteResponse(
        Long id,
        String title,
        String content,
        String tags,
        @JsonProperty("isImportant") Boolean isImportant,
        @JsonProperty("isPinned") Boolean isPinned,
        @JsonProperty("isCompleted") Boolean isCompleted,
        @JsonProperty("delete_flg") Boolean deleteFlg,
        @JsonProperty("created_at") LocalDateTime createdAt,
        LocalDateTime deadline
) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getTags(),
                note.getIsImportant(),
                note.getIsPinned(),
                note.getIsCompleted(),
                note.getDeleteFlg(),
                note.getCreatedAt(),
                note.getDeadline()
        );
    }
}
