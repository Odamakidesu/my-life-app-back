package com.mylifeapp.user.repository;

import com.mylifeapp.user.entity.User;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ユーザーの永続化。
 *
 * <p>更新は列を限定した UPDATE で行う。save() は全列を書き戻すため、
 * 管理者の権限変更と本人のパスワード変更が重なると、片方の変更（パスワードやトークンの版）が巻き戻る。
 */
public interface UserRepository extends CrudRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Query("SELECT * FROM users ORDER BY id LIMIT :limit OFFSET :offset")
    List<User> findPage(@Param("limit") int limit, @Param("offset") long offset);

    @Modifying
    @Query("UPDATE users SET role = :role WHERE id = :id")
    int updateRole(@Param("id") Long id, @Param("role") String role);

    @Modifying
    @Query("UPDATE users SET enabled = :enabled WHERE id = :id")
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);

    /** パスワードを変え、トークンの版を進めて発行済みのトークンをすべて無効にする。 */
    @Modifying
    @Query("UPDATE users SET password = :password, token_version = token_version + 1 WHERE id = :id")
    int updatePasswordAndRevokeTokens(@Param("id") Long id, @Param("password") String password);
}
