package com.mylifeapp.common.web;

/**
 * 入力の組み合わせや現在の状態に照らして受け付けられない要求（400）。
 * Bean Validation の注釈だけでは書けない検証（例: 締切の無い繰り返し、現在のパスワードの不一致）に使う。
 * メッセージはそのまま利用者に表示されるので、次に何をすればよいかが分かる文にすること。
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
