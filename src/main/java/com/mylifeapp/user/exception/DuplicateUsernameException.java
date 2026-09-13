package com.mylifeapp.user.exception;

/** 既に使われているユーザー名での登録。409 を返すために使う。 */
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException() {
        super("このユーザー名は既に使用されています。");
    }
}
