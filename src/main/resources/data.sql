-- このファイルは UTF-8。送信するバイト列の解釈をサーバに明示する。
-- これが無いと、mysql クライアントの既定文字セット（環境により latin1）で
-- 解釈され、日本語が化けたまま保存される。実際に docker の初期化で発生した。
SET NAMES utf8mb4;

-- 開発用シードデータ。spring.sql.init.mode=always のときだけ実行される（= local プロファイルのみ）。
-- ここに書かれた認証情報はローカル開発専用であり、本番には一切投入されない。
-- 本番の初期管理者作成は docs/runbook-security-hardening.md の手順に従うこと。

-- パスワードはいずれも 'testpass'（BCrypt cost 10）。ローカル専用。
INSERT INTO users (id, username, password, enabled, role) VALUES
   (1, 'admin', '$2a$10$XgTnzOcuXBA1eFJco4SIN.bDWWJ37OrxAaSeC2vaIBW2xdX3C5HJy', true, 'ADMIN'),
   (2, 'testuser', '$2a$10$XgTnzOcuXBA1eFJco4SIN.bDWWJ37OrxAaSeC2vaIBW2xdX3C5HJy', true, 'USER');

INSERT INTO tags (name, color, created_at, updated_at, delete_flg) VALUES
   ('仕事', '#007bff', NOW(), NOW(), false),
   ('プライベート', '#28a745', NOW(), NOW(), false),
   ('勉強', '#ffc107', NOW(), NOW(), false),
   ('買い物', '#17a2b8', NOW(), NOW(), false);

INSERT INTO note (user_id, title, content, tags, is_important, is_pinned, deadline, is_completed, created_at, delete_flg) VALUES
     (2, '会議の準備', '週次ミーティングの資料を作成する', '仕事', true, false, '2025-05-02 10:00:00', false, NOW(), false),
     (2, '家族とディナー', 'レストランを予約する', 'プライベート', false, false, '2025-05-03 19:00:00', false, NOW(), false),
     (2, 'Javaの復習', 'Stream APIを復習する', '勉強', true, true, '2025-05-04 21:00:00', false, NOW(), false),
     (2, 'スーパーで買い物', '野菜と牛乳を買う', '買い物', false, false, '2025-05-01 18:00:00', false, NOW(), false),
     (2, 'プロジェクト進捗確認', 'クライアントへ進捗を報告', '仕事,プライベート', true, true, '2025-05-02 15:00:00', false, NOW(), false),
     (2, '読書時間', '技術書「Clean Code」を読む', '勉強,プライベート', false, false, NULL, false, NOW(), false),
     (2, 'ネットショッピング', 'PCパーツをチェック', '買い物', false, false, NULL, false, NOW(), false),
     (2, 'チームのコードレビュー', 'PRを確認してコメント', '仕事', true, false, '2025-05-01 17:00:00', false, NOW(), false),
     (2, '散歩に出かける', '30分ほど近所を歩く', 'プライベート', false, false, NULL, true, NOW(), false),
     (1, '定例会議', 'Zoomリンクを準備して参加', '仕事', false, true, '2025-05-03 09:00:00', false, NOW(), false);
