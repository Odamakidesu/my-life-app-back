package com.mylifeapp.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * タグの作成・更新のリクエスト。
 *
 * <p>名前にカンマを許さないのは、メモがタグを「カンマ区切りの名前」で持つため。
 * カンマを含む名前を許すと、そのタグを付けたメモが別々の2つのタグとして読まれる。
 *
 * <p>色は {@code #RRGGBB} に限る。画面は文字色を決めるためにこの形で RGB を読み取る。
 */
public record TagRequest(
        @NotBlank(message = "タグ名は必須です")
        @Size(max = 50, message = "タグ名は50文字以内で入力してください")
        @Pattern(regexp = "^[^,]*$", message = "タグ名にカンマは使えません")
        String name,

        @NotBlank(message = "色は必須です")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "色は #RRGGBB の形式で指定してください")
        String color
) {
}
