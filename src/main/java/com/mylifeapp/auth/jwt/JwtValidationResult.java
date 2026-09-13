package com.mylifeapp.auth.jwt;

/**
 * トークン検証の結果。
 *
 * <p>以前は boolean を返していたため、期限切れ・改ざん・鍵不一致が区別できなかった。
 * 鍵をローテーションして全ユーザーが弾かれたときに、それが鍵の問題なのか
 * 全員のトークンが同時に期限切れたのかを切り分ける手段が無く、
 * その切り分けに要する時間がそのまま障害の継続時間になる。
 */
public enum JwtValidationResult {
    VALID,
    /** 署名は正しいが有効期限を過ぎている。クライアントは再ログインすればよい。 */
    EXPIRED,
    /** 署名が一致しない。鍵の不一致か改ざん。1件でも出たら調査対象。 */
    BAD_SIGNATURE,
    /** JWT として解釈できない。 */
    MALFORMED,
    /** 対応していない形式・アルゴリズム。 */
    UNSUPPORTED,
    /** トークンが空、または Bearer ヘッダが無い。 */
    ABSENT;

    public boolean isValid() {
        return this == VALID;
    }
}
