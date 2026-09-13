package com.mylifeapp.note.dto;

import jakarta.validation.constraints.NotNull;

// 以前はフィールド名が important で getter だけ getCompleted という食い違いがあった。
public record CompletedUpdateRequest(@NotNull(message = "completed は必須です") Boolean completed) {
}
