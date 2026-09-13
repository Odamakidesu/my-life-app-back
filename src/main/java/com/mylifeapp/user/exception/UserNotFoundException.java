package com.mylifeapp.user.exception;

/** 管理操作の対象ユーザーが存在しない。 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("ユーザーが見つかりませんでした: " + id);
    }
}
