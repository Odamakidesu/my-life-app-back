package com.mylifeapp.note.dto;

/** メモの入力の上限。フロントエンドの features/note/types/schema.ts と揃えること。 */
public final class NoteLimits {

    /** 本文の上限（文字数）。note.content は TEXT（V3）。utf8mb4 で 40000 バイトに収まる。 */
    public static final int CONTENT_MAX_LENGTH = 10000;

    private NoteLimits() {
    }
}
