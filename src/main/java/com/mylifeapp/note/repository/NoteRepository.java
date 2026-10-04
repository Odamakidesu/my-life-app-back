package com.mylifeapp.note.repository;

import com.mylifeapp.note.entity.Note;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * メモの永続化。
 *
 * <p>所有者チェックはコントローラの if 文ではなく、ここのクエリ条件で強制する。
 * 個別に守る設計は必ず漏れる。すべての読み書きに user_id 条件を含め、
 * 更新系は「更新件数」を返して 0 件を 404 として扱えるようにする。
 */
@Repository
public interface NoteRepository extends CrudRepository<Note, Long> {

    /**
     * 自分の有効なメモを取得する。
     *
     * <p>LIMIT / OFFSET を必ず付ける。以前は条件が delete_flg だけで LIMIT も無く、
     * EXPLAIN が type=ALL（フルスキャン）を示していた。
     * インデックス idx_note_user_active (user_id, delete_flg, created_at) が効く形にしてある。
     */
    @Query("""
            SELECT * FROM note
            WHERE user_id = :userId AND delete_flg = false
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Note> findActiveByUserId(@Param("userId") Long userId,
                                  @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM note WHERE user_id = :userId AND delete_flg = false")
    long countActiveByUserId(@Param("userId") Long userId);

    /**
     * ゴミ箱（自分の論理削除済みメモ）を取得する。
     *
     * <p>有効なメモの一覧と同じく idx_note_user_active (user_id, delete_flg, created_at) が効く。
     */
    @Query("""
            SELECT * FROM note
            WHERE user_id = :userId AND delete_flg = true
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Note> findDeletedByUserId(@Param("userId") Long userId,
                                   @Param("limit") int limit,
                                   @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM note WHERE user_id = :userId AND delete_flg = true")
    long countDeletedByUserId(@Param("userId") Long userId);

    @Query("SELECT * FROM note WHERE id = :id AND user_id = :userId")
    Optional<Note> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * このエンドポイントが所有する列だけを更新する。
     *
     * <p>以前は findById して setter を呼び save() する read-modify-write で、
     * Spring Data JDBC の save は全カラム UPDATE を発行していた。
     * そのため別リクエストが更新した is_important などが巻き添えで消えていた
     * （ユーザーには 200 が返るため気づけない）。
     */
    @Modifying
    @Query("""
            UPDATE note SET title = :title, content = :content, tags = :tags, deadline = :deadline
            WHERE id = :id AND user_id = :userId
            """)
    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("title") String title,
                      @Param("content") String content,
                      @Param("tags") String tags,
                      @Param("deadline") java.time.LocalDateTime deadline);

    @Modifying
    @Query("UPDATE note SET is_important = :value WHERE id = :id AND user_id = :userId")
    int updateImportant(@Param("id") Long id, @Param("userId") Long userId, @Param("value") boolean value);

    @Modifying
    @Query("UPDATE note SET is_pinned = :value WHERE id = :id AND user_id = :userId")
    int updatePinned(@Param("id") Long id, @Param("userId") Long userId, @Param("value") boolean value);

    @Modifying
    @Query("UPDATE note SET is_completed = :value WHERE id = :id AND user_id = :userId")
    int updateCompleted(@Param("id") Long id, @Param("userId") Long userId, @Param("value") boolean value);

    @Modifying
    @Query("UPDATE note SET delete_flg = :value WHERE id = :id AND user_id = :userId")
    int updateDeleteFlg(@Param("id") Long id, @Param("userId") Long userId, @Param("value") boolean value);

    /**
     * ゴミ箱にあるメモを物理削除する。
     *
     * <p>delete_flg = true を条件に含めるのは、一覧に出ているメモを
     * 誤操作や古い画面からの要求で一気に消せないようにするため。
     * 物理削除は取り消せないので、必ずゴミ箱を経由させる。
     */
    @Modifying
    @Query("DELETE FROM note WHERE id = :id AND user_id = :userId AND delete_flg = true")
    int deletePermanently(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * タグ名を含み得る自分のメモ（論理削除済みも含む）。
     *
     * <p>LIKE は候補の絞り込みにだけ使う。名前に % や _ が含まれると余分に拾うが、
     * 呼び出し側でカンマ区切りの要素ごとに完全一致で判定し直すので結果は変わらない。
     */
    @Query("SELECT * FROM note WHERE user_id = :userId AND tags LIKE CONCAT('%', :name, '%')")
    List<Note> findByUserIdAndTagsContaining(@Param("userId") Long userId, @Param("name") String name);

    @Modifying
    @Query("UPDATE note SET tags = :tags WHERE id = :id AND user_id = :userId")
    int updateTags(@Param("id") Long id, @Param("userId") Long userId, @Param("tags") String tags);
}
