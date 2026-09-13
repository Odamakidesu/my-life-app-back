package com.mylifeapp.note.exception;

/**
 * 指定されたメモが存在しないか、呼び出し元の所有物ではない。
 *
 * <p>「他人のメモなので拒否した」と「そもそも存在しない」を意図的に区別しない。
 * 区別すると ID を総当たりして他ユーザーのメモの存在を確認できてしまう。
 */
public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(Long id) {
        super("メモが見つかりませんでした: " + id);
    }
}
