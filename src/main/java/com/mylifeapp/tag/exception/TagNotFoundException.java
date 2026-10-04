package com.mylifeapp.tag.exception;

/**
 * 指定されたタグが存在しないか、呼び出し元が編集できるタグではない（共通タグ・他人のタグ）。
 * メモと同じく、ID の総当たりで他人のタグの存在が分からないよう区別しない。
 */
public class TagNotFoundException extends RuntimeException {
    public TagNotFoundException(Long id) {
        super("タグが見つかりませんでした: " + id);
    }
}
