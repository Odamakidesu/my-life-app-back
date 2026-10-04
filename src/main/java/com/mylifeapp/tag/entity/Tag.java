package com.mylifeapp.tag.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * タグ。
 *
 * <p>フィールドは camelCase に統一し、DB 列名は {@code @Column} で明示する（Note と同じ流儀）。
 * 以前は created_at / delete_flg をそのままフィールド名にしており、
 * さらにこのクラスを API の応答に直接使っていたため、それらが JSON に出ていた。
 *
 * <p>このクラスは永続化の型であり、HTTP の入出力には使わない。レスポンスは TagResponse を使う。
 */
@Table("tags")
@Getter
@Setter
public class Tag {

    @Id
    private Long id;

    /**
     * 所有者。NULL は全ユーザー共通のタグ（初期データ）で、利用者は編集できない。
     * 利用者が作ったタグは自分にだけ見え、自分だけが編集・削除できる。
     */
    @Column("user_id")
    private Long userId;

    private String name;
    private String color;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("delete_flg")
    private Boolean deleteFlg = false;

    public Tag() {
    }
}
