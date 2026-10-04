package com.mylifeapp.tag.repository;

import com.mylifeapp.tag.entity.Tag;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * タグの永続化。
 *
 * <p>メモと同じく、所有者チェックはクエリ条件で強制する。
 * 共通タグ（user_id IS NULL）は読めるが、更新系の条件には絶対に入らない。
 */
@Repository
public interface TagRepository extends CrudRepository<Tag, Long> {

    /**
     * 利用者に見える有効なタグ（共通タグ＋自分のタグ）。共通タグを先に並べる。
     *
     * <p>以前は findAll() で delete_flg を見ずに全件返していたため、
     * 論理削除したタグも画面の選択肢に残っていた。
     */
    @Query("""
            SELECT * FROM tags
            WHERE delete_flg = false AND (user_id IS NULL OR user_id = :userId)
            ORDER BY user_id IS NOT NULL, id
            """)
    List<Tag> findVisible(@Param("userId") Long userId);

    @Query("SELECT * FROM tags WHERE id = :id AND user_id = :userId AND delete_flg = false")
    Optional<Tag> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 利用者に見えるタグに同じ名前があるか（excludeId は自分自身を除くため。新規作成時は -1）。
     *
     * <p>メモはタグを名前で持つため、見えるタグの中で名前が重複すると色が決まらない。
     * 比較は列の照合順序に従う（既定の utf8mb4_0900_ai_ci では大文字小文字を区別しない）。
     * 画面の絞り込みも大文字小文字を区別しないので、それと揃う。
     */
    @Query("""
            SELECT COUNT(*) FROM tags
            WHERE delete_flg = false AND (user_id IS NULL OR user_id = :userId)
              AND name = :name AND id <> :excludeId
            """)
    long countVisibleByName(@Param("userId") Long userId,
                            @Param("name") String name,
                            @Param("excludeId") Long excludeId);

    @Modifying
    @Query("""
            UPDATE tags SET name = :name, color = :color, updated_at = :updatedAt
            WHERE id = :id AND user_id = :userId AND delete_flg = false
            """)
    int updateOwned(@Param("id") Long id,
                    @Param("userId") Long userId,
                    @Param("name") String name,
                    @Param("color") String color,
                    @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE tags SET delete_flg = true, updated_at = :updatedAt
            WHERE id = :id AND user_id = :userId AND delete_flg = false
            """)
    int softDeleteOwned(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * メモに付けるタグ名を ID に解決する。見えるタグ（共通・自分）を優先し、無ければ削除済みのタグを使う。
     * 削除済みのタグを使うのは、削除したタグ名が残ったメモを編集して保存しても名前が変わらないようにするため。
     */
    @Query("""
            SELECT * FROM tags
            WHERE (user_id IS NULL OR user_id = :userId) AND name = :name
            ORDER BY delete_flg, id
            LIMIT 1
            """)
    Optional<Tag> findResolvable(@Param("userId") Long userId, @Param("name") String name);
}
