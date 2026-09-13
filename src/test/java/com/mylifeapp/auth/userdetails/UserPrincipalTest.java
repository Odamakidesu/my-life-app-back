package com.mylifeapp.auth.userdetails;

import com.mylifeapp.user.model.User;
import com.mylifeapp.user.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 認可の根になる権限文字列と有効フラグ。ここがずれると全ルールが静かに死ぬ。 */
class UserPrincipalTest {

    private User user(UserRole role, boolean enabled) {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setPassword("hash");
        user.setEnabled(enabled);
        user.setRole(role);
        return user;
    }

    @Test
    @DisplayName("ユーザー取得時に付与される権限がROLE_プレフィックス付きである")
    void authoritiesCarryRolePrefix() {
        // hasRole("ADMIN") は "ROLE_ADMIN" を要求する。プレフィックスを落とすと
        // hasRole / hasAnyRole のルールが一つも一致しなくなる。
        assertThat(new UserPrincipal(user(UserRole.USER, true)).getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(new UserPrincipal(user(UserRole.ADMIN, true)).getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("roleがnullの場合はUSERとして扱われADMINには昇格しない")
    void nullRoleFallsBackToUser() {
        UserPrincipal principal = new UserPrincipal(user(null, true));

        assertThat(principal.getRole()).isEqualTo(UserRole.USER);
        assertThat(principal.getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("enabledはDBの値をそのまま反映する")
    void enabledReflectsDatabaseValue() {
        assertThat(new UserPrincipal(user(UserRole.USER, true)).isEnabled()).isTrue();
        assertThat(new UserPrincipal(user(UserRole.USER, false)).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("アカウントの期限や施錠状態は常に有効を返す")
    void accountStateAccessorsAreStable() {
        UserPrincipal principal = new UserPrincipal(user(UserRole.USER, true));

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getPassword()).isEqualTo("hash");
    }
}
