package com.mylifeapp.note.repository;

import com.mylifeapp.note.model.Note;
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
}
