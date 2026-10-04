package com.mylifeapp.note.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mylifeapp.note.entity.Note;
import com.mylifeapp.note.entity.Recurrence;

import java.time.LocalDateTime;

/**
 * メモのレスポンス。
 *
 * <p>JSON のフィールド名は既存のフロントエンドが読んでいるものをそのまま維持する
 * （created_at / delete_flg / isImportant ...）。Java 側のフィールドは camelCase に
 * 統一したが、それを API の契約に波及させると既存クライアントが壊れるため、
 * ここで明示的に写像する。user_id は外に出さない。
 *
 * <p>tags は従来どおりカンマ区切りの名前だが、中身は note_tags（対応表）から組み立てる。
 * そのため改名したタグは即座に新しい名前で返る。
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
        LocalDateTime deadline,
        Recurrence recurrence,
        @JsonProperty("deleted_at") LocalDateTime deletedAt
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
                note.getDeadline(),
                note.getRecurrence(),
                note.getDeletedAt()
        );
    }
}
