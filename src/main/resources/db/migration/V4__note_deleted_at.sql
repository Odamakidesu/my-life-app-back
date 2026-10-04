-- ゴミ箱に入れた日時（note.deleted_at）を追加する。ゴミ箱の並び順と、一定期間後の自動削除に使う。
--
-- 種類: 追加のみ（NULL 許容の列と索引）。
-- 既にゴミ箱にあるメモは、この版を適用した時点を削除日時とみなす
-- （適用直後に一斉に自動削除されないように、保持期間をここから数え始める）。
--
-- 列・索引が既にある場合は何もしない（V2 と同じく information_schema を見て文を組み立てる）。
-- 戻し方: ALTER TABLE note DROP INDEX idx_note_trash, DROP COLUMN deleted_at;

SET NAMES utf8mb4;

SET @has_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'note' AND column_name = 'deleted_at'
);
SET @ddl = IF(@has_column = 0,
    'ALTER TABLE note ADD COLUMN deleted_at DATETIME NULL AFTER delete_flg',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自動削除（WHERE delete_flg = true AND deleted_at < ?）用。
SET @has_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'note' AND index_name = 'idx_note_trash'
);
SET @ddl = IF(@has_index = 0,
    'ALTER TABLE note ADD INDEX idx_note_trash (delete_flg, deleted_at)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE note SET deleted_at = NOW() WHERE delete_flg = true AND deleted_at IS NULL;
