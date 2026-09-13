#!/usr/bin/env bash
#
# ローカル開発 DB にスキーマとシードを投入し直す。
#
# application-local.properties は spring.sql.init.mode=never にしてある。
# 起動のたびに DROP TABLE が走ると、自分で作ったメモが毎回消えるため。
# 投入し直したいときだけ、このスクリプトを明示的に実行する。
#
#   ./scripts/reseed-local-db.sh          実行前に確認を求める
#   ./scripts/reseed-local-db.sh --yes    確認なしで実行する
#
# 接続先は application-local.properties から読む。認証情報は画面に出さない。
# mysql クライアントが PATH に無ければ Docker のイメージを使う。
#
# 注意: schema.sql の先頭は DROP TABLE。既存のデータは失われる。

set -euo pipefail

cd "$(dirname "$0")/.."

PROPS="src/main/resources/application-local.properties"
SCHEMA="src/main/resources/schema.sql"
DATA="src/main/resources/data.sql"
MYSQL_IMAGE="mysql:8.0.42"

for f in "$PROPS" "$SCHEMA" "$DATA"; do
  [ -f "$f" ] || { echo "ERROR: $f が見つかりません" >&2; exit 1; }
done

# --- 接続情報の取り出し ------------------------------------------------------
prop() {
  # 末尾の改行(CR含む)を落として値だけを返す
  sed -n "s/^$1=//p" "$PROPS" | tail -1 | tr -d '\r'
}

URL="$(prop 'spring\.datasource\.url')"
DB_USER="$(prop 'spring\.datasource\.username')"
DB_PASS="$(prop 'spring\.datasource\.password')"

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
# それを grep で潰すと、出力が無いときに grep が終了コード 1 を返し、
# set -o pipefail と組み合わさってスクリプトごと落ちる（実際に踏んだ）。
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
echo "投入   : $SCHEMA, $DATA"
echo
echo "警告: schema.sql は DROP TABLE から始まります。$DB_NAME の既存データは失われます。"

if [ "${1:-}" != "--yes" ]; then
  printf "続行しますか? [y/N] "
  read -r answer
  case "$answer" in [yY]|[yY][eE][sS]) ;; *) echo "中止しました"; exit 0;; esac
fi

# --- 実行 --------------------------------------------------------------------
# SQL ファイルは UTF-8 で、先頭に SET NAMES utf8mb4 がある。
# 標準入力でそのまま渡すため、クライアント側の文字コードに影響されない。
echo
echo "schema.sql を実行中..."
run_mysql "$DB_NAME" < "$SCHEMA"
echo "data.sql を実行中..."
run_mysql "$DB_NAME" < "$DATA"

# --- 検証 --------------------------------------------------------------------
# 投入できたことと、日本語が化けていないことを確認する。
# 化けても INSERT は成功するので、件数だけでは検知できない。
echo
echo "検証中..."
RESULT="$(run_mysql -N -B -e "
SELECT CONCAT(
  (SELECT COUNT(*) FROM $DB_NAME.tags), ' ',
  (SELECT COUNT(*) FROM $DB_NAME.note), ' ',
  (SELECT COUNT(*) FROM $DB_NAME.users), ' ',
  (SELECT COUNT(*) FROM $DB_NAME.tags WHERE name LIKE '%�%')
   + (SELECT COUNT(*) FROM $DB_NAME.note
        WHERE title LIKE '%�%' OR content LIKE '%�%' OR IFNULL(tags,'') LIKE '%�%'), ' ',
  (SELECT HEX(name) FROM $DB_NAME.tags WHERE id = 1)
);")"

set -- $RESULT
TAGS=$1; NOTES=$2; USERS=$3; BROKEN=$4; HEX1=$5

echo "  tags=$TAGS  note=$NOTES  users=$USERS"

STATUS=0
if [ "$BROKEN" != "0" ]; then
  echo "  NG: 置換文字(U+FFFD)を含む行が $BROKEN 件あります（文字化け）" >&2
  STATUS=1
fi
# 「仕事」の UTF-8 バイト列。ここがずれていれば投入経路の文字コードが壊れている。
if [ "$HEX1" != "E4BB95E4BA8B" ]; then
  echo "  NG: tags.id=1 のバイト列が想定と違います (実際: $HEX1 / 期待: E4BB95E4BA8B)" >&2
  STATUS=1
fi

if [ "$STATUS" -eq 0 ]; then
  echo "  OK: 文字化けなし"
  echo
  echo "完了しました。"
else
  echo
  echo "投入は行われましたが検証に失敗しました。" >&2
  exit 1
fi
