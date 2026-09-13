package com.mylifeapp.note.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

@Table("note") // ←これ必須！
@Getter
@Setter
public class Note {

    @Id
    private Long id;

    private String title;
    private String content;
    private LocalDateTime created_at;
    private String tags;
    private Boolean isImportant;
    private Boolean isPinned;
    private Boolean isCompleted;
    private Boolean delete_flg = false;
    private LocalDateTime deadline;

    // --- デフォルトコンストラクタ（必須！）---
    public Note() {}

    // --- titleとcontentをセットできるコンストラクタ ---
    public Note(String title, String content) {
        this.title = title;
        this.content = content;
        this.created_at = LocalDateTime.now();
    }
}