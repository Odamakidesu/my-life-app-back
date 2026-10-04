#!/usr/bin/env bash
#
# ローカル開発 DB を空にして、次回のアプリ起動時にスキーマとシードを作り直させる。
#
# スキーマは Flyway（src/main/resources/db/migration）が、開発用シードは
# db/seed/R__local_seed.sql が、アプリの起動時に投入する。ここではテーブルを消すだけ。
# 普段のスキーマ変更（新しい V<n>__*.sql）は起動するだけで既存の DB に適用されるので、
# このスクリプトは「データを捨ててやり直したいとき」専用。
#
#   ./scripts/reseed-local-db.sh          実行前に確認を求める
#   ./scripts/reseed-local-db.sh --yes    確認なしで実行する
#
# 接続先は application-local.properties から読む。認証情報は画面に出さない。
# mysql クライアントが PATH に無ければ Docker のイメージを使う。
#
# Docker の DB（docker compose）なら、このスクリプトの代わりに
#   docker compose down -v && docker compose up -d --build
# でも同じことができる。
#
# 注意: 既存のデータはすべて失われる。

set -euo pipefail

cd "$(dirname "$0")/.."

PROPS="src/main/resources/application-local.properties"
MYSQL_IMAGE="mysql:8.0.42"

[ -f "$PROPS" ] || { echo "ERROR: $PROPS が見つかりません" >&2; exit 1; }

# --- 接続情報の取り出し ------------------------------------------------------
prop() {
  # 末尾の改行(CR含む)を落として値だけを返す
  sed -n "s/^$1=//p" "$PROPS" | tail -1 | tr -d '\r'
}

URL="$(prop 'spring\.datasource\.url')"
DB_USER="$(prop 'spring\.datasource\.username')"
DB_PASS="$(prop 'spring\.datasource\.password')"
LOCATIONS="$(prop 'spring\.flyway\.locations')"

# jdbc:mysql://host:port/dbname?... を分解する
STRIPPED="${URL#jdbc:mysql://}"
HOSTPORT="${STRIPPED%%/*}"
DB_HOST="${HOSTPORT%%:*}"
DB_PORT="${HOSTPORT#*:}"; [ "$DB_PORT" = "$HOSTPORT" ] && DB_PORT=3306
DB_NAME="${STRIPPED#*/}"; DB_NAME="${DB_NAME%%\?*}"

if [ -z "$DB_NAME" ] || [ -z "$DB_USER" ]; then
  echo "ERROR: $PROPS から接続先を読み取れませんでした" >&2
  exit 1
fi

# --- クライアントの選択 ------------------------------------------------------
# パスワードは MYSQL_PWD で渡す。-p で渡すと ps から見えるうえ、
# mysql が毎回 "Using a password ... insecure" を stderr に出す。
export MYSQL_PWD="$DB_PASS"

if command -v mysql > /dev/null 2>&1; then
  CLIENT_KIND="local"
  run_mysql() { mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --default-character-set=utf8mb4 "$@"; }
elif command -v docker > /dev/null 2>&1; then
  CLIENT_KIND="docker"
  # コンテナから見たホストは host.docker.internal
  CONTAINER_HOST="$DB_HOST"
  case "$DB_HOST" in localhost|127.0.0.1) CONTAINER_HOST="host.docker.internal";; esac
  run_mysql() {
    docker run -i --rm -e MYSQL_PWD "$MYSQL_IMAGE" \
      mysql -h "$CONTAINER_HOST" -P "$DB_PORT" -u "$DB_USER" \
            --default-character-set=utf8mb4 "$@"
  }
else
  echo "ERROR: mysql クライアントも docker も見つかりません" >&2
  exit 1
fi

echo "接続先 : $DB_HOST:$DB_PORT / $DB_NAME   (クライアント: $CLIENT_KIND)"
echo
echo "警告: $DB_NAME のテーブル（note / tags / users / flyway_schema_history）を削除します。既存データは失われます。"

if [ "${1:-}" != "--yes" ]; then
  printf "続行しますか? [y/N] "
  read -r answer
  case "$answer" in [yY]|[yY][eE][sS]) ;; *) echo "中止しました"; exit 0;; esac
fi

# --- 実行 --------------------------------------------------------------------
# 外部キーの子から先に消す。
run_mysql "$DB_NAME" -e "DROP TABLE IF EXISTS note; DROP TABLE IF EXISTS tags; DROP TABLE IF EXISTS users; DROP TABLE IF EXISTS flyway_schema_history;"

echo
echo "削除しました。アプリを起動すると Flyway がスキーマとシードを作り直します。"

case "$LOCATIONS" in
  *db/seed*) ;;
  *)
    echo
    echo "注意: $PROPS の spring.flyway.locations に classpath:db/seed が含まれていません。" >&2
    echo "      このままだとスキーマだけが作られ、開発用ユーザー（admin / testuser）は入りません。" >&2
    echo "      application-local.properties.example に合わせて次の行を追加してください:" >&2
    echo "      spring.flyway.locations=classpath:db/migration,classpath:db/seed" >&2
    ;;
esac
