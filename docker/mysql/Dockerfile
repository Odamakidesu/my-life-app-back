# ローカル検証用の MySQL。
#
# スキーマとシードを COPY でイメージに焼き込む。バインドマウントを使わないのは、
# Docker Desktop (Windows/WSL2) でドライブ共有が有効でないホストや、
# パスに非 ASCII を含むホストではマウントが失敗するため。
# ビルドコンテキスト経由なら、その差異を受けない。
#
# /docker-entrypoint-initdb.d はデータボリュームが空のとき（＝初回のみ）実行される。
# スキーマを変えたあとに作り直すには:  docker compose down -v && docker compose up -d --build
FROM mysql:8.0.42

COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/01-schema.sql
COPY src/main/resources/data.sql   /docker-entrypoint-initdb.d/02-data.sql
