package com.mylifeapp.user.model;

import com.mylifeapp.user.model.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
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
