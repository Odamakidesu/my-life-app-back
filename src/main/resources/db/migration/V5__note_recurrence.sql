-- 繰り返しタスク（note.recurrence）を追加する。
-- 値は DAILY / WEEKLY / MONTHLY のいずれか。NULL は繰り返さない。
-- 繰り返しのメモを完了にすると、次の締切のメモが作られる（NoteService.setCompleted）。
--
-- 種類: 追加のみ（NULL 許容の列）。既存のメモはすべて「繰り返さない」になる。
-- 戻し方: ALTER TABLE note DROP COLUMN recurrence;

SET NAMES utf8mb4;

SET @has_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'note' AND column_name = 'recurrence'
);
SET @ddl = IF(@has_column = 0,
    'ALTER TABLE note ADD COLUMN recurrence VARCHAR(10) NULL AFTER deadline',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
