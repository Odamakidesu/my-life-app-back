package com.mylifeapp.auth.jwt;

/** ログアウトで無効にしたトークン（jti）かどうかを答える。実装は auth.session.RevokedTokenStore。 */
@FunctionalInterface
public interface TokenRevocationChecker {
    boolean isRevoked(String jti);
}
