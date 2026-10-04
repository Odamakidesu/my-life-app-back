package com.mylifeapp.tag.dto;

import com.mylifeapp.tag.entity.Tag;

/**
 * タグのレスポンス。
 *
 * <p>以前はエンティティをそのまま返しており、created_at / updated_at / delete_flg が
 * API に出ていた。フロントエンドが読むのは id / name / color / editable だけなので、それ以外は出さない。
 *
 * <p>user_id は出さず、呼び出し元が編集できるかどうか（editable）だけを返す。
 * 一覧は共通タグと自分のタグしか返さないため、「自分のタグ = 編集可」になる。
 */
public record TagResponse(
        Long id,
        String name,
        String color,
        boolean editable
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor(), tag.getUserId() != null);
    }
}
