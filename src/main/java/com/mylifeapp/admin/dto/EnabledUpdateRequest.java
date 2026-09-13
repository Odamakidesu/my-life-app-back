package com.mylifeapp.admin.dto;

import jakarta.validation.constraints.NotNull;

public record EnabledUpdateRequest(@NotNull(message = "enabled は必須です") Boolean enabled) {
}
