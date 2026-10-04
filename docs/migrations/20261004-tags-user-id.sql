-- タグを利用者ごとに作成・編集できるようにする（tags に所有者列を追加）。
--
-- 既存のタグは user_id = NULL（全ユーザー共通・編集不可）として残る。
-- 列の追加だけで既存データの書き換えは無いため、適用前のアプリはこの列を無視してそのまま動く。
-- 先にこの SQL を本番 DB へ適用し、そのあとでアプリをデプロイすること
-- （逆順だと新しいアプリが存在しない列を参照して /api/tags が 500 になる）。
--
-- 戻す場合（利用者が作ったタグは失われる）:
--   DELETE FROM tags WHERE user_id IS NOT NULL;
--   ALTER TABLE tags DROP FOREIGN KEY fk_tags_user, DROP INDEX idx_tags_user_active, DROP COLUMN user_id;

SET NAMES utf8mb4;

ALTER TABLE tags
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users (id),
    ADD INDEX idx_tags_user_active (user_id, delete_flg);
