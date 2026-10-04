-- 開発用シードデータ（ローカル・Docker・テストのみ）。
--
-- spring.flyway.locations に classpath:db/seed を含めたときだけ実行される。
-- 本番の既定値（application.properties）は classpath:db/migration だけなので、本番には入らない。
-- ここに書かれた認証情報はローカル開発専用。本番の初期管理者作成は
-- docs/runbook-security-hardening.md の手順に従うこと。
--
-- 繰り返し可能マイグレーション（R__）なので、内容を変えると次回起動時に再実行される。
-- そのため何度流しても重複しないよう、すべて「無ければ入れる」形で書く。
-- 既存のローカル DB に初めて Flyway を当てたときも、足りない行だけが入る。

SET NAMES utf8mb4;

-- パスワードはいずれも 'testpass'（BCrypt cost 10）。ローカル専用。
INSERT IGNORE INTO users (id, username, password, enabled, role) VALUES
   (1, 'admin', '$2a$10$XgTnzOcuXBA1eFJco4SIN.bDWWJ37OrxAaSeC2vaIBW2xdX3C5HJy', true, 'ADMIN'),
   (2, 'testuser', '$2a$10$XgTnzOcuXBA1eFJco4SIN.bDWWJ37OrxAaSeC2vaIBW2xdX3C5HJy', true, 'USER');

-- 共通タグ（user_id = NULL）。
INSERT INTO tags (name, color, created_at, updated_at, delete_flg)
SELECT seed.name, seed.color, NOW(), NOW(), false
FROM (
    SELECT 1 AS ord, '仕事' AS name, '#007bff' AS color
    UNION ALL SELECT 2, 'プライベート', '#28a745'
    UNION ALL SELECT 3, '勉強', '#ffc107'
    UNION ALL SELECT 4, '買い物', '#17a2b8'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM tags t WHERE t.name = seed.name AND t.user_id IS NULL
)
ORDER BY seed.ord;

INSERT INTO note (user_id, title, content, tags, is_important, is_pinned, deadline, is_completed, created_at, delete_flg)
SELECT seed.user_id, seed.title, seed.content, seed.tags, seed.is_important, seed.is_pinned,
       seed.deadline, seed.is_completed, NOW(), false
FROM (
    SELECT 1 AS ord, 2 AS user_id, '会議の準備' AS title, '週次ミーティングの資料を作成する' AS content, '仕事' AS tags,
           true AS is_important, false AS is_pinned, CAST('2025-05-02 10:00:00' AS DATETIME) AS deadline, false AS is_completed
    UNION ALL SELECT 2, 2, '家族とディナー', 'レストランを予約する', 'プライベート', false, false, '2025-05-03 19:00:00', false
    UNION ALL SELECT 3, 2, 'Javaの復習', 'Stream APIを復習する', '勉強', true, true, '2025-05-04 21:00:00', false
    UNION ALL SELECT 4, 2, 'スーパーで買い物', '野菜と牛乳を買う', '買い物', false, false, '2025-05-01 18:00:00', false
    UNION ALL SELECT 5, 2, 'プロジェクト進捗確認', 'クライアントへ進捗を報告', '仕事,プライベート', true, true, '2025-05-02 15:00:00', false
    UNION ALL SELECT 6, 2, '読書時間', '技術書「Clean Code」を読む', '勉強,プライベート', false, false, NULL, false
    UNION ALL SELECT 7, 2, 'ネットショッピング', 'PCパーツをチェック', '買い物', false, false, NULL, false
    UNION ALL SELECT 8, 2, 'チームのコードレビュー', 'PRを確認してコメント', '仕事', true, false, '2025-05-01 17:00:00', false
    UNION ALL SELECT 9, 2, '散歩に出かける', '30分ほど近所を歩く', 'プライベート', false, false, NULL, true
    UNION ALL SELECT 10, 1, '定例会議', 'Zoomリンクを準備して参加', '仕事', false, true, '2025-05-03 09:00:00', false
) AS seed
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = seed.user_id)
  AND NOT EXISTS (
    SELECT 1 FROM note n WHERE n.user_id = seed.user_id AND n.title = seed.title
)
ORDER BY seed.ord;

-- メモとタグの対応（note_tags。V7 以降はこちらが正で、note.tags は旧版のための写し）。
-- 見本のメモは V8 の移行より後に入るので、ここで対応を張る。
INSERT IGNORE INTO note_tags (note_id, tag_id, sort_order)
SELECT n.id, t.id, seed.sort_order
FROM (
    SELECT 2 AS user_id, '会議の準備' AS title, '仕事' AS tag, 0 AS sort_order
    UNION ALL SELECT 2, '家族とディナー', 'プライベート', 0
    UNION ALL SELECT 2, 'Javaの復習', '勉強', 0
    UNION ALL SELECT 2, 'スーパーで買い物', '買い物', 0
    UNION ALL SELECT 2, 'プロジェクト進捗確認', '仕事', 0
    UNION ALL SELECT 2, 'プロジェクト進捗確認', 'プライベート', 1
    UNION ALL SELECT 2, '読書時間', '勉強', 0
    UNION ALL SELECT 2, '読書時間', 'プライベート', 1
    UNION ALL SELECT 2, 'ネットショッピング', '買い物', 0
    UNION ALL SELECT 2, 'チームのコードレビュー', '仕事', 0
    UNION ALL SELECT 2, '散歩に出かける', 'プライベート', 0
    UNION ALL SELECT 1, '定例会議', '仕事', 0
) AS seed
JOIN note n ON n.user_id = seed.user_id AND n.title = seed.title
JOIN tags t ON t.user_id IS NULL AND t.name = seed.tag;
