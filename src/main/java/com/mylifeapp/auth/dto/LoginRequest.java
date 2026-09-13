package com.mylifeapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ログインのリクエスト。
 *
 * <p>toString() には username だけを載せる。@Data や @ToString を後から付けた瞬間に
 * 平文パスワードがログへ漏れるため、出力対象を明示的に固定しておく。
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class LoginRequest {

    @ToString.Include
    @NotBlank(message = "ユーザー名は必須です")
    private String username;

    @NotBlank(message = "パスワードは必須です")
    private String password;
}
