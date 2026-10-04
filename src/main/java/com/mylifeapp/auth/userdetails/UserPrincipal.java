package com.mylifeapp.auth.userdetails;

import com.mylifeapp.user.entity.User;
import com.mylifeapp.user.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 認証済みユーザー。
 *
 * <p>DB の enabled と role を反映する。以前は本クラスが実装済みにもかかわらず使われておらず、
 * {@code CustomUserDetailsService} が Spring の {@code User} を3引数で生成していたため
 * enabled が常に true 扱いになり、アカウント無効化が機能していなかった。
 */
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    /** ノートの所有者判定に使う。認可はこの値をクエリ条件に落として強制する。 */
    public Long getUserId() {
        return user.getId();
    }

    /** 発行するトークンに埋め込み、受け取ったトークンと照合する版。 */
    public int getTokenVersion() {
        return user.getTokenVersion();
    }

    public UserRole getRole() {
        return user.getRole() == null ? UserRole.USER : user.getRole();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole("ADMIN") は "ROLE_ADMIN" を要求する。プレフィックスを落とすと
        // hasRole / hasAnyRole のルールが一つも一致しなくなる。
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + getRole().name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
