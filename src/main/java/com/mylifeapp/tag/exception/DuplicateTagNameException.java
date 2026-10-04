package com.mylifeapp.tag.exception;

/** 利用者に見えるタグと同じ名前での作成・改名。409 を返すために使う。 */
public class DuplicateTagNameException extends RuntimeException {
    public DuplicateTagNameException() {
        super("同じ名前のタグが既にあります。");
    }
}
