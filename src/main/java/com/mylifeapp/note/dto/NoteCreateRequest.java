package com.mylifeapp.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * メモ作成のリクエスト。
 *
 * <p>エンティティを直接バインドしていたため、リクエストボディに id を入れると
 * Spring Data JDBC が「既存」と判定して INSERT ではなく UPDATE を発行し、
 * 任意のメモを上書きできていた。id / user_id / delete_flg は受け取らない。
 * 所有者は認証情報から決める。
 */
public record NoteCreateRequest(
        @NotBlank(message = "タイトルは必須です")
        @Size(max = 255, message = "タイトルは255文字以内で入力してください")
        String title,

        @NotBlank(message = "本文は必須です")
        @Size(max = 255, message = "本文は255文字以内で入力してください")
        String content,

        String tags,
        LocalDateTime deadline,
        Boolean isImportant,
        Boolean isPinned
) {
}
