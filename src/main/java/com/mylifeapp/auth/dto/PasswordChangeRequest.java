package com.mylifeapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** パスワード変更のリクエスト。新しいパスワードの規則は登録時（RegisterRequest）と同じ。 */
public record PasswordChangeRequest(
        @NotBlank(message = "現在のパスワードを入力してください")
        String currentPassword,

        @NotBlank(message = "新しいパスワードを入力してください")
        @Size(min = 12, max = 128, message = "パスワードは12文字以上128文字以内で入力してください")
        String newPassword
) {
}
