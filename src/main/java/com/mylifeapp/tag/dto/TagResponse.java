package com.mylifeapp.tag.dto;

import com.mylifeapp.tag.entity.Tag;

/**
 * タグのレスポンス。
 *
 * <p>以前はエンティティをそのまま返しており、created_at / updated_at / delete_flg が
 * API に出ていた。フロントエンドが読むのは id / name / color だけなので、それ以外は出さない。
 */
public record TagResponse(
        Long id,
        String name,
        String color
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor());
    }
}
