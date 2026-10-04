package com.mylifeapp.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.mylifeapp.note.entity.Recurrence;

import java.time.LocalDateTime;

/**
 * メモ本文の更新リクエスト。
 *
 * <p>このエンドポイントが所有する列（title / content / tags / deadline / recurrence）だけを受け取る。
 * is_important などは専用エンドポイントが持つため、ここでは触らない。
 * これにより同時編集で無関係なフラグが巻き添えで戻る現象が起きなくなる。
 */
public record NoteUpdateRequest(
        @NotBlank(message = "タイトルは必須です")
        @Size(max = 50, message = "タイトルは50文字以内で入力してください")
        String title,

        @NotBlank(message = "本文は必須です")
        @Size(max = NoteLimits.CONTENT_MAX_LENGTH, message = "本文は10000文字以内で入力してください")
        String content,

        String tags,
        LocalDateTime deadline,

        /** 繰り返しの間隔。null は繰り返さない。締切が無いと 400 */
        Recurrence recurrence
) {
}
