package com.mylifeapp.note.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * メモ。
 *
 * <p>フィールドは camelCase に統一し、DB 列名は {@code @Column} で明示する。
 * 以前は created_at / delete_flg と isImportant が混在しており、
 * NamingStrategy の結果が偶然一致しているだけだったため、
 * 列を追加する人がどちらの流儀に従うべきか判断できなかった。
 *
 * <p>このクラスは永続化の型であり、HTTP の入出力には使わない。
 * リクエストは NoteCreateRequest / NoteUpdateRequest、レスポンスは NoteResponse を使う。
 */
@Table("note")
@Getter
@Setter
public class Note {

    @Id
    private Long id;

    /** 所有者。認可はこの列をクエリ条件に落として強制する。 */
    @Column("user_id")
    private Long userId;

    private String title;
    private String content;
    private String tags;

    @Column("is_important")
    private Boolean isImportant = false;

    @Column("is_pinned")
    private Boolean isPinned = false;

    @Column("is_completed")
    private Boolean isCompleted = false;

    @Column("delete_flg")
    private Boolean deleteFlg = false;

    @Column("created_at")
    private LocalDateTime createdAt;

    private LocalDateTime deadline;

    public Note() {
    }
}
