-- ログアウトとパスワード変更でトークンを無効にできるようにする。
--
--  * users.token_version: トークンに埋め込む版。パスワードを変えたら 1 増やし、
--    それより前に発行した（全端末の）トークンを無効にする。
--  * revoked_token: ログアウトした個々のトークン（jti）。有効期限を過ぎたら消してよい。
--
-- 種類: 追加のみ（DEFAULT 付きの列と新しいテーブル）。既存のトークンは版 0 として有効なまま。
-- 戻し方: DROP TABLE revoked_token; ALTER TABLE users DROP COLUMN token_version;

SET NAMES utf8mb4;

SET @has_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'token_version'
);
SET @ddl = IF(@has_column = 0,
    'ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS revoked_token (
    jti CHAR(36) NOT NULL PRIMARY KEY,
    expires_at DATETIME NOT NULL,
    INDEX idx_revoked_token_expires (expires_at)
);
