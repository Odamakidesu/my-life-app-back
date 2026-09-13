package com.mylifeapp.note.dto;

import jakarta.validation.constraints.NotNull;

public record PinnedUpdateRequest(@NotNull(message = "pinned は必須です") Boolean pinned) {
}
