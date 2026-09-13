# ローカル検証スタック（Docker）

AWS へ上げる前に、手元で認証・DB・バックエンドを本番と同じ形で動かすための構成。

## 何が本番と同じで、何が違うか

「ローカルでは動いたのに本番で壊れた」を減らすのが目的なので、**差分を意図的に最小にしてある**。

| 項目 | この構成 | 本番（ECS） |
|---|---|---|
| 設定の注入 | 環境変数（docker-compose.yml） | 環境変数（ECS タスク定義の environment / secrets） |
| `SPRING_SQL_INIT_MODE` | `never` | `never` |
| DB スキーマ投入 | DB イメージの初期化スクリプト（初回のみ） | 手動マイグレーション |
| 実行ユーザー | 非 root (`app`) | 非 root (`app`) |
| CPU / メモリ | 0.5 vCPU / 1024MB | cpu=512 / memory=1024 |
| 実効ヒープ | 約 718MB（`MaxRAMPercentage=70`） | 約 718MB |
| MySQL | 8.0.42 | RDS MySQL 8 |
| **差分1: ログ形式** | 人間可読（既定） | JSON（`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`） |
| **差分2: レート制限** | ログイン 100回/分 | ログイン 10回/分 |
| **差分3: TLS** | なし（平文 HTTP） | ALB で終端（移行後） |

差分1・2 は `.env` で本番と同じ値にできる（下記「本番に寄せる」）。

## 使い方

```bash
# 起動（初回はイメージのビルドに数分かかる）
docker compose up -d --build

# 状態を見る
docker compose ps
docker compose logs -f backend

# 停止（データは残る）
docker compose down

# データを捨てて初期化（スキーマを変更したときは必須）
docker compose down -v && docker compose up -d --build
```

起動後のエンドポイント:

| URL | 内容 |
|---|---|
| http://localhost:8081/health | ヘルスチェック（DB 疎通込み） |
| http://localhost:8081/actuator/health/readiness | ALB 用の readiness |
| http://localhost:8081/api/... | API |
| `localhost:13306` | MySQL（`mylifeapp` / `localonly-app-password`） |

ポートの既定値が 8081 / 13306 なのは、手元の 8080 と 3306 が
別プロセスに使われているため。変えたい場合は `.env` で上書きする。

## 初期ユーザー

`data.sql` が投入する**ローカル専用**のユーザー。本番では実行されない。

| ユーザー名 | パスワード | 権限 |
|---|---|---|
| `admin` | `testpass` | ADMIN |
| `testuser` | `testpass` | USER |

```bash
# ログインしてトークンを取る
curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","password":"testpass"}'
```

`testuser` にメモ 9 件、`admin` に 1 件が割り当てられている。
所有者分離が効いていることを確かめるには、両方でログインして
`GET /api/notes` の件数が分かれることを見ればよい。

## フロントエンドと繋ぐ

このスタックはフロントエンドを含まない。フロントは開発サーバーで動かす。

```bash
cd ../my-life-app-front
npm start          # http://localhost:5173 で起動する
```

バックエンドの CORS 許可オリジンは既定で `http://localhost:5173`。
フロント側の接続先は `my-life-app-front/.env.local` の
`REACT_APP_API_BASE_URL=http://localhost:8081/api` が既にこのスタックを指している。

別のポートを使う場合は両方を合わせること（`.env` の `FRONTEND_ORIGIN` と
フロント側の `REACT_APP_API_BASE_URL`）。片方だけ変えると、
ブラウザのコンソールに CORS エラーが出るだけで API 側には何も残らない。

## 設定の上書き

```bash
cp .env.example .env
```

`.env` は `.gitignore` 対象。すべての項目に既定値があるため、
`.env` が無くてもそのまま起動する。実在の環境の資格情報は絶対に書かないこと。

### 本番に寄せる（リリース前の確認）

`.env` に以下を足すと、ログ形式とレート制限が本番と同じになる。

```bash
RATE_LIMIT_LOGIN_CAPACITY=10
```

ログを本番と同じ JSON 形式で見るには、`docker-compose.yml` の
コメントアウトしてある `LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs` を有効にする。
CloudWatch Logs Insights のクエリが手元で試せるようになる。

### SQL を見たいとき

`docker-compose.yml` の `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_JDBC: DEBUG` を有効にする。
**本番では絶対に有効にしないこと。** バインド値にはメモ本文・ユーザー名・
BCrypt ハッシュが含まれ、CloudWatch へ全量流出する。

## よくあるつまずき

**スキーマを変えたのに反映されない**
初期化スクリプトは DB のデータボリュームが空のときしか走らない。
`docker compose down -v && docker compose up -d --build` でやり直す。

**`docker compose up` が MySQL の接続エラーで落ちる**
バックエンドは DB の healthcheck が通るまで起動しない設定になっている
（`depends_on: condition: service_healthy`）。それでも落ちる場合は
`docker compose logs db` を見ること。なお healthcheck に `mysqladmin ping` を
使ってはいけない。初期化中の一時サーバにも応答してしまい、
「ping は通るのに接続すると落ちる」状態を正常と誤判定する。

**ポートが衝突する**
`.env` で `BACKEND_PORT` / `MYSQL_PORT` を変える。

**動作が重い**
既定は本番と同じ 0.5 vCPU に制限してある。ログインが 1 回あたり
0.3 秒前後かかるのは BCrypt のコストで、本番でも同じ。
開発中に困る場合だけ `.env` で `BACKEND_CPUS` / `BACKEND_MEMORY` を上げる。
ただし上げるとメモリ起因の問題は再現しなくなる。

**日本語を含む JSON を curl で送ると 400 になる**
Git Bash など、シェルが UTF-8 以外で引数を渡していることが原因。
サーバは不正なバイト列を正しく 400 で弾いている。
ファイルに UTF-8 で書いて `--data-binary @file.json` で送ること。

## Docker を使わず、手元の MySQL で動かす場合

IDE から直接起動する場合など、Docker スタックを使わない運用も残してある。
その場合の接続先は `src/main/resources/application-local.properties`（.gitignore 対象）に書く。
ひな形は `application-local.properties.example` をコピーして使う。

```bash
cp src/main/resources/application-local.properties.example \n   src/main/resources/application-local.properties
# 接続先と jwt.secret を書き換える
```

### スキーマとシードの投入

`spring.sql.init.mode` は **`never`** にしてある。`always` にすると
起動のたびに `schema.sql` の `DROP TABLE` が走り、自分で作ったメモが毎回消える。
エラーにならないので気づきにくく、気づいた時には戻せない。

投入し直したいときだけ明示的に実行する。

```bash
./scripts/reseed-local-db.sh          # 確認プロンプトあり
./scripts/reseed-local-db.sh --yes    # 確認なし
```

このスクリプトは接続先を `application-local.properties` から読み、
`schema.sql` → `data.sql` を流したあと、**件数だけでなく文字化けの有無まで検証する**。
文字化けしても INSERT は成功するため、件数の確認だけでは検知できない。

その場かぎりで投入したい場合は起動時に上書きしてもよい。

```bash
./gradlew bootRun --args='--spring.sql.init.mode=always'
```

空の DB に対して `never` のまま起動すると
`Table 'mylifeapp.note' doesn't exist` で失敗する。その場合も上のコマンドで投入する。

### 文字コードについて

`schema.sql` / `data.sql` は UTF-8 で、先頭に `SET NAMES utf8mb4;` がある。
これが無いと、読み込む側の既定文字コード次第で日本語が化けたまま保存される。
実際に次の2通りで発生した。

| 経路 | 化けた原因 | 対策 |
|---|---|---|
| Spring の `spring.sql.init` | `spring.sql.init.encoding` 未指定で JVM 既定（MS932）で読んでいた | `application.properties` に `spring.sql.init.encoding=UTF-8` |
| MySQL の初期化スクリプト | mysql クライアントの既定文字セットが latin1 だった | SQL 先頭の `SET NAMES utf8mb4;` と `docker/mysql/my.cnf` |

どちらも「DB には正しい UTF-8 として化けた文字が保存される」ため、
接続やテーブルの文字セットを調べても原因に辿り着かない。
`SeedDataEncodingTest` が投入結果を検証しているので、再発すればテストが落ちる。

## イメージについて

`Dockerfile` はマルチステージで、イメージの中でソースから jar を作る。
手元のビルド成果物に依存しないため、クリーンなチェックアウトからでも同じ物ができる。

`.dockerignore` で `application-local.properties` と `application-prod.properties` を
ビルドコンテキストから除外している。これらは `.gitignore` 対象だがローカルには
実ファイルとして存在するため、除外しないと本番 RDS の資格情報が
イメージのレイヤに焼き込まれる。CI は当該ファイルを持たないので気づけない。

## このスタックで確認できないこと

- ALB / CloudFront の挙動（TLS 終端、ヘルスチェックの経路、`X-Forwarded-*`）
- SSM からのシークレット注入
- RDS のフェイルオーバー時の挙動
- CloudWatch Logs への実際の取り込み

これらを含む本番適用の手順は [runbook-security-hardening.md](runbook-security-hardening.md) を参照。
