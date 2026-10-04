-- メモ本文の上限を 255 文字から広げる（VARCHAR(255) → TEXT）。
--
-- 種類: 既存行に影響しない拡張（型を広げるだけ。既存の値はそのまま入る）。
-- アプリ側の上限は NoteCreateRequest / NoteUpdateRequest の @Size（10000 文字）で縛る。
-- utf8mb4 で 10000 文字は最大 40000 バイトなので TEXT（65535 バイト）に収まる。
--
-- 何度流しても同じ結果になる（MODIFY は冪等）。
-- 戻し方: 255 文字を超える本文が無いことを確かめてから
--   ALTER TABLE note MODIFY content VARCHAR(255) NOT NULL;

SET NAMES utf8mb4;

ALTER TABLE note MODIFY content TEXT NOT NULL;
