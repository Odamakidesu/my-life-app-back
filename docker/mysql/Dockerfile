# ローカル検証用の MySQL。
#
# スキーマとシードはここでは作らない。アプリの起動時に Flyway が
# src/main/resources/db/migration（と db/seed）から作り、以後のスキーマ変更も起動時に追いつかせる。
# 以前は schema.sql / data.sql を初期化スクリプトとして焼き込んでいたが、
# 初回（ボリュームが空のとき）しか実行されないため、スキーマ変更が既存の DB に届かなかった。
#
# DB を空からやり直すには:  docker compose down -v && docker compose up -d --build
FROM mysql:8.0.42

# 文字セットの既定値。初期化スクリプトを実行する mysql クライアントにも効かせる。
COPY docker/mysql/my.cnf /etc/mysql/conf.d/charset.cnf
