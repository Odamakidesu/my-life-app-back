package com.mylifeapp.user.dto;

import com.mylifeapp.user.model.User;
import com.mylifeapp.user.model.UserRole;

/**
 * ユーザーのレスポンス。
 *
 * <p>以前は {@code ResponseEntity<User>} でエンティティをそのまま返しており、
 * User.password に @JsonIgnore が無いため BCrypt ハッシュが JSON に載っていた。
 * password をそもそも持たない型を返すことで、構造的に再発しないようにする。
 */
public record UserResponse(
        Long id,
        String username,
        boolean enabled,
        UserRole role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.isEnabled(), user.getRole());
    }
}
