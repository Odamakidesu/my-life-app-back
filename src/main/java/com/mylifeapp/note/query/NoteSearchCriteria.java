package com.mylifeapp.note.query;

import java.util.List;

/**
 * 一覧の検索条件。null（tags は空）の項目は絞り込まない。
 *
 * @param keyword   タイトルか本文に含まれる文字列（大文字小文字は区別しない）
 * @param tags      すべてを含むメモに絞るタグ名
 * @param pinned    true ならピン留めだけ
 * @param important true ならスターだけ
 * @param completed true なら完了済みだけ、false なら未完了だけ
 * @param due       締切による絞り込み
 * @param sort      並べ替え
 */
public record NoteSearchCriteria(
        String keyword,
        List<String> tags,
        Boolean pinned,
        Boolean important,
        Boolean completed,
        DueFilter due,
        NoteSort sort
) {
    public NoteSearchCriteria {
        tags = tags == null ? List.of() : List.copyOf(tags);
        sort = sort == null ? NoteSort.CREATED : sort;
    }

    public static NoteSearchCriteria none() {
        return new NoteSearchCriteria(null, List.of(), null, null, null, null, NoteSort.CREATED);
    }
}
