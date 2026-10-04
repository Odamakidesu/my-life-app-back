-- 初期スキーマ（Flyway 導入時点の本番スキーマ。タグの所有者列は V2 で追加する）。
--
-- Flyway 導入前から存在する DB（本番・既存のローカル Docker）では、このファイルは実行されない。
-- spring.flyway.baseline-on-migrate=true / baseline-version=1 により「V1 まで適用済み」として記録され、
-- V2 以降だけが適用される。空の DB（新しい Docker ボリューム・テスト）ではここから作られる。
--
-- 適用済みのマイグレーションは書き換えないこと（チェックサム不一致で起動に失敗する）。
-- 変更は必ず新しい V<n>__*.sql を追加して行う。

SET NAMES utf8mb4;

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
