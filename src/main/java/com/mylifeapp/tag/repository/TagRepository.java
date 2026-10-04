package com.mylifeapp.tag.repository;

import com.mylifeapp.tag.entity.Tag;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends CrudRepository<Tag, Long> {

    /**
     * 有効なタグを取得する。
     *
     * <p>以前は findAll() で delete_flg を見ずに全件返していたため、
     * 論理削除したタグも画面の選択肢に残っていた。
     */
    @Query("SELECT * FROM tags WHERE delete_flg = false ORDER BY id")
    List<Tag> findActive();
}
