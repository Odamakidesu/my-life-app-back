package com.mylifeapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ユーザー登録のリクエスト。
 *
 * <p>role は受け取らない。以前は {@code @RequestParam UserRole role} で呼び出し側が
 * 権限を自己申告でき、無認証の登録エンドポイントから ADMIN を名乗れた。
 * 権限の付与は管理者専用エンドポイントに限定する。
 *
 * <p>@RequestParam ではなく @RequestBody にしているのは、クエリ文字列で送られると
 * 平文パスワードが ALB のアクセスログ・ブラウザ履歴・Referer に残るため。
 * これはアプリ側のマスキングでは防げない層で漏れる。
 */
public record RegisterRequest(
        @NotBlank(message = "ユーザー名は必須です")
        @Size(min = 3, max = 50, message = "ユーザー名は3文字以上50文字以内で入力してください")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "ユーザー名に使用できるのは英数字と _ . - です")
        String username,

        @NotBlank(message = "パスワードは必須です")
        @Size(min = 12, max = 128, message = "パスワードは12文字以上128文字以内で入力してください")
        String password
) {
}
