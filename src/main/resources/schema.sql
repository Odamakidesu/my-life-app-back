-- このファイルは UTF-8。送信するバイト列の解釈をサーバに明示する。
-- これが無いと、mysql クライアントの既定文字セット（環境により latin1）で
-- 解釈され、日本語が化けたまま保存される。実際に docker の初期化で発生した。
SET NAMES utf8mb4;

-- 開発用スキーマ。spring.sql.init.mode=always のときだけ実行される。
-- 既定は never（application.properties）で、local プロファイルだけが always に上書きする。
-- 本番では絶対に実行されない。本番のスキーマ変更は docs/runbook-security-hardening.md の手順に従うこと。

-- DROP は外部キーの子から先に行う。
DROP TABLE IF EXISTS note;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   username VARCHAR(50) NOT NULL UNIQUE,
   password VARCHAR(255) NOT NULL,
   enabled TINYINT(1) NOT NULL DEFAULT 1,
   role VARCHAR(20) NOT NULL DEFAULT 'USER'
);

CREATE TABLE tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  color VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  delete_flg TINYINT(1) NOT NULL
);

CREATE TABLE note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 所有者。これが無いと全ユーザーのメモが共有され、認可を差し込む場所が存在しない。
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    tags TEXT,
    is_important TINYINT(1) NOT NULL DEFAULT false,
    is_pinned TINYINT(1) NOT NULL DEFAULT false,
    deadline DATETIME,
    is_completed TINYINT(1) NOT NULL DEFAULT false,
    created_at DATETIME NOT NULL,
    delete_flg TINYINT(1) NOT NULL DEFAULT false,
    CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- 一覧クエリ (WHERE user_id = ? AND delete_flg = ? ORDER BY created_at) 用。
    -- 列順は「等値の user_id → 低選択性の delete_flg → ソートキー created_at」。
    INDEX idx_note_user_active (user_id, delete_flg, created_at)
);
