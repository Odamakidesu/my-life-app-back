package com.mylifeapp.note.query;

/** 一覧の並べ替え（クエリ文字列では sort=DEADLINE のように名前で指定する）。 */
public enum NoteSort {
    /** ピン留め → スター → 作成日時の新しい順（画面の既定） */
    PRIORITY("n.is_pinned DESC, n.is_important DESC, n.created_at DESC, n.id DESC"),
    /** 未完了を先に、締切の近い順。締切の無いメモは最後 */
    DEADLINE("n.is_completed ASC, n.deadline IS NULL, n.deadline ASC, n.created_at DESC, n.id DESC"),
    /** 作成日時の新しい順（以前の API の並び。sort を省略したときはこれ） */
    CREATED("n.created_at DESC, n.id DESC");

    final String orderBy;

    NoteSort(String orderBy) {
        this.orderBy = orderBy;
    }
}
