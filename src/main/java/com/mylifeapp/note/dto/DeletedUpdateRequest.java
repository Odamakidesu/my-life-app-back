package com.mylifeapp.note.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

// JSON 上のキー名は既存フロントエンドに合わせて delete_flg のまま維持する。
public record DeletedUpdateRequest(
        @JsonProperty("delete_flg")
        @NotNull(message = "delete_flg は必須です")
        Boolean deleteFlg) {
}
