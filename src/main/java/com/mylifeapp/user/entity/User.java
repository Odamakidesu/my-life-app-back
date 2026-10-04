package com.mylifeapp.user.entity;

import com.mylifeapp.user.entity.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@Table("users")  // 対応するテーブル名
public class User {

    @Id
    private Long id;

    private String username;
    private String password;
    private boolean enabled;

    private UserRole role;

    /**
     * トークンの版。トークンに埋め込み、一致しないトークンは受け付けない。
     * パスワードを変えると 1 増え、それより前に発行した全端末のトークンが無効になる。
     */
    @Column("token_version")
    private int tokenVersion;

    // コンストラクタ、ゲッター・セッター
    public User() {}

    public User(Long id, String username, String password, boolean enabled, UserRole role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.role = role;
    }

}
