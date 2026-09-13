package com.mylifeapp.admin.dto;

import com.mylifeapp.user.model.UserRole;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull(message = "role は必須です") UserRole role) {
}
