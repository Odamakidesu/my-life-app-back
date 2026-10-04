-- タグを利用者ごとに作成・編集できるようにする（tags に所有者列を追加）。
--
-- 既存のタグは user_id = NULL（全ユーザー共通・編集不可）として残る。
--
-- 列が既にある場合は何もしない。Flyway 導入前に docs/migrations の手順で
-- 手作業で適用済みの DB でも、起動に失敗せずにそのまま追いつけるようにするため。
-- MySQL 8 には ADD COLUMN IF NOT EXISTS が無いので、information_schema を見て文を組み立てる。

SET NAMES utf8mb4;

SET @has_user_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tags' AND column_name = 'user_id'
);

SET @ddl = IF(@has_user_id = 0,
    'ALTER TABLE tags
         ADD COLUMN user_id BIGINT NULL AFTER id,
         ADD CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users (id),
         ADD INDEX idx_tags_user_active (user_id, delete_flg)',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
