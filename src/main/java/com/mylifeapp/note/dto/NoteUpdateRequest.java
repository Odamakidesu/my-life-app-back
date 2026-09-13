package com.mylifeapp.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * メモ本文の更新リクエスト。
 *
 * <p>このエンドポイントが所有する列（title / content / tags / deadline）だけを受け取る。
 * is_important などは専用エンドポイントが持つため、ここでは触らない。
 * これにより同時編集で無関係なフラグが巻き添えで戻る現象が起きなくなる。
 */
public record NoteUpdateRequest(
        @NotBlank(message = "タイトルは必須です")
        @Size(max = 255, message = "タイトルは255文字以内で入力してください")
        String title,

        @NotBlank(message = "本文は必須です")
        @Size(max = 255, message = "本文は255文字以内で入力してください")
        String content,

        String tags,
        LocalDateTime deadline
) {
}
