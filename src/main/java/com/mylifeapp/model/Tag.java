package com.mylifeapp.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "tags")
public class Tag {
    @Id
    private Long id;
    private String name;
    private String color;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private Boolean delete_flg;

    // 引数なしコンストラクタだけ残す
    public Tag() {}
}