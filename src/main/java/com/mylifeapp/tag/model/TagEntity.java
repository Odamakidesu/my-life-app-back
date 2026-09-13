package com.mylifeapp.tag.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "tags")
public class TagEntity {
    @Id
    private Long id;

    private String name;
    private String color;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updated_at;
    private Boolean delete_flg;
}