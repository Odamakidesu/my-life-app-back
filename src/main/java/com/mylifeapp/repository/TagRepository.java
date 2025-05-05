package com.mylifeapp.repository;

import com.mylifeapp.model.Tag;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends CrudRepository<Tag, Long> {
    // 必要なら追加のメソッド（後でやる）
}