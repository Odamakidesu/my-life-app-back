INSERT INTO tags (name, color, created_at, updated_at, delete_flg) VALUES
   ('仕事', '#007bff', NOW(), NOW(), false),
   ('プライベート', '#28a745', NOW(), NOW(), false),
   ('勉強', '#ffc107', NOW(), NOW(), false),
   ('買い物', '#17a2b8', NOW(), NOW(), false);

INSERT INTO note (title, content, tags, is_important, is_pinned, deadline, is_completed, created_at, delete_flg) VALUES
     ('会議の準備', '週次ミーティングの資料を作成する', '仕事', true, false, '2025-05-02 10:00:00', false, NOW(), false),
     ('家族とディナー', 'レストランを予約する', 'プライベート', false, false, '2025-05-03 19:00:00', false, NOW(), false),
     ('Javaの復習', 'Stream APIを復習する', '勉強', true, true, '2025-05-04 21:00:00', false, NOW(), false),
     ('スーパーで買い物', '野菜と牛乳を買う', '買い物', false, false, '2025-05-01 18:00:00', false, NOW(), false),
     ('プロジェクト進捗確認', 'クライアントへ進捗を報告', '仕事,プライベート', true, true, '2025-05-02 15:00:00', false, NOW(), false),
     ('読書時間', '技術書「Clean Code」を読む', '勉強,プライベート', false, false, NULL, false, NOW(), false),
     ('ネットショッピング', 'PCパーツをチェック', '買い物', false, false, NULL, false, NOW(), false),
     ('チームのコードレビュー', 'PRを確認してコメント', '仕事', true, false, '2025-05-01 17:00:00', false, NOW(), false),
     ('散歩に出かける', '30分ほど近所を歩く', 'プライベート', false, false, NULL, true, NOW(), false),
     ('定例会議', 'Zoomリンクを準備して参加', '仕事', false, true, '2025-05-03 09:00:00', false, NOW(), false);

# ！！！！！！！！！！！！ユーザーのデータ投入はPOSTMANから！！！！！！！！！！！！
# INSERT INTO users (username, password, enabled)
# VALUES ('testuser', '$2a$10$nOUIs5kJ7naTuTFkBy1veuEvafYedcEhe59W9SRXhZ7Utx0P/LFZm', true);
# passwordはtestpass