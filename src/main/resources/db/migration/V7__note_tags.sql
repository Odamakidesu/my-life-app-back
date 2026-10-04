-- メモとタグの対応を正規化する（note.tags のカンマ区切りの名前 → note_tags）。
--
-- これまではメモがタグを名前で持っていたため、タグを改名すると全メモの文字列を書き換える必要があり、
-- 名前の部分一致で候補を探す処理が壊れやすかった。対応表にすると改名はタグの行を1つ変えるだけになる。
--
-- 種類: 拡張（テーブル追加）。既存の対応は V8（Java: NoteTagsBackfill）で note.tags から写す。
-- note.tags は旧版へ戻せるように残し、アプリは当面両方に書く。削除は次のリリースで行う。
-- 戻し方: DROP TABLE note_tags;（note.tags は書き続けているので旧版はそのまま動く）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS note_tags (
    note_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    -- メモに付けた順（画面の表示順）
    sort_order INT NOT NULL,
    PRIMARY KEY (note_id, tag_id),
    INDEX idx_note_tags_tag (tag_id),
    CONSTRAINT fk_note_tags_note FOREIGN KEY (note_id) REFERENCES note (id) ON DELETE CASCADE,
    CONSTRAINT fk_note_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
);
