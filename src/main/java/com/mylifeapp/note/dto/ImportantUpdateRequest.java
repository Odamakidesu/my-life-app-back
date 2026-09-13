package com.mylifeapp.note.dto;

import jakarta.validation.constraints.NotNull;

public record ImportantUpdateRequest(@NotNull(message = "important は必須です") Boolean important) {
}
