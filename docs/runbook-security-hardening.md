# 本番適用手順書（セキュリティ強化・2026-09-13 のレビュー対応）

このリポジトリの変更だけでは完了しない作業をまとめる。AWS コンソール / CLI と
GitHub の設定変更が必要な項目で、**コードをデプロイする前に読むこと**。

作業の順序には依存関係がある。上から順に実施する。

---

## 0. 事前確認：この変更はデータベースのスキーマ変更を含む

`note` テーブルに所有者列 `user_id` を追加した。**これが無い状態では新しいコードは起動後に失敗する。**

`schema.sql` はローカル開発専用（`spring.sql.init.mode` の既定を `never` に変えたため、
本番では実行されない）。本番のスキーマは手動で移行する。

### 移行前に決めること

既存の `note` 行には所有者が存在しない。`user_id` は `NOT NULL` なので、既存行をどう扱うか決める必要がある。

| 選択肢 | 内容 |
|---|---|
| A. 特定ユーザーに寄せる | 既存メモをすべて特定の1ユーザーの所有にする。実質的に単一ユーザーで使っていた場合はこれ |
| B. 破棄する | 検証用データしか無い場合。`DELETE FROM note` してから列を追加する |

**どちらを選ぶかは本番データの中身を見て判断すること。** 以下は A の手順。

```sql
-- 1. バックアップ（必須）
--    RDS のスナップショットを取ってから始めること。

-- 2. 所有者にするユーザーの id を確認する
SELECT id, username, role FROM users;

-- 3. いったら NULL 許容で列を追加する
ALTER TABLE note ADD COLUMN user_id BIGINT NULL AFTER id;

-- 4. 既存行に所有者を割り当てる（<OWNER_ID> を 2 で確認した値に置き換える）
UPDATE note SET user_id = <OWNER_ID> WHERE user_id IS NULL;

-- 5. NOT NULL 化と制約・インデックスの追加
ALTER TABLE note
  MODIFY COLUMN user_id BIGINT NOT NULL,
  ADD CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD INDEX idx_note_user_active (user_id, delete_flg, created_at);

-- 6. 確認
SHOW INDEX FROM note;
EXPLAIN SELECT * FROM note WHERE user_id = <OWNER_ID> AND delete_flg = false
        ORDER BY created_at DESC LIMIT 100;
--    type=ref / key=idx_note_user_active になっていること（ALL なら効いていない）
```

あわせて `users.role` を `NOT NULL DEFAULT 'USER'` にしておく（`schema.sql` に合わせる）。

```sql
UPDATE users SET role = 'USER' WHERE role IS NULL;
ALTER TABLE users MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
```

---

## 1. JWT 署名鍵のローテーション（最優先・必須）

公開リポジトリの履歴（コミット `c623b86`）に、当時の `jwt.secret` の値が残っている。
削除コミット `fcf0edb` は作業ツリーから消しただけで、**push 済みのブロブは誰でも取得できる**。

本番の鍵と同一である確証は無い（履歴上の値は 27 バイト = 216 bit で、jjwt は 256 bit 未満を
`WeakKeyException` で弾くため、その値のままでは起動できなかったはず）。
ただし**同一でない保証も無い**。確認と対処のコストが低いので、確認せずローテーションする。

```bash
# 新しい鍵を生成（32バイト以上）
NEW_SECRET=$(openssl rand -base64 48)

aws ssm put-parameter \
  --name /mylifeapp/prod/jwt-secret \
  --value "$NEW_SECRET" \
  --type SecureString \
  --overwrite \
  --region ap-northeast-1
```

**影響**: 既発行のトークンはすべて無効になり、全ユーザーが再ログインを求められる。
利用者の少ない時間帯に行うこと。

ローテーション後、ECS サービスを更新して新しい値を読ませる（タスクの再起動が必要）。

```bash
aws ecs update-service --cluster <CLUSTER> --service <SERVICE> \
  --force-new-deployment --region ap-northeast-1
```

起動ログに次の行が出るので、`keyFingerprint` が変わったことを確認する。
この1行があると「SSM のローテーション後に別の鍵が入った」を即座に特定できる。

```
jwt config loaded keyLengthBytes=64 keyFingerprint=a1b2c3d4 expirationMs=86400000
```

**あわせて**: GitHub リポジトリの Settings → Code security で
Secret scanning と Push protection を有効にする。

---

## 2. AWS アクセスキーの廃止と OIDC への移行（必須）

GitHub Secrets の `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` は失効しない長命クレデンシャルで、
ECR push と ECS デプロイの権限を持つ。漏洩すれば任意のコンテナイメージを本番 Fargate に
載せられる（本番 RCE 相当）。

### 2-1. IAM に OIDC プロバイダを作る（未作成の場合）

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### 2-2. 引受ロールを作る

信頼ポリシー（`sub` をこのリポジトリの `main` ブランチに限定する。
`repo:Odamakidesu/my-life-app-back:*` のようなワイルドカードにしないこと）:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::498504718094:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:Odamakidesu/my-life-app-back:ref:refs/heads/main"
      }
    }
  }]
}
```

権限は既存のデプロイユーザーと同等（ECR push / ECS RegisterTaskDefinition / UpdateService /
`iam:PassRole` で `ecsTaskExecutionRole`）にする。

### 2-3. ワークフローを切り替える

`.github/workflows/backend.yml` の `deploy` ジョブにある TODO コメントの手順に従う。
`permissions: id-token: write` を足し、`aws-access-key-id` / `aws-secret-access-key` を
`role-to-assume` に置き換える。

### 2-4. 旧キーを無効化する

切り替え後のデプロイが1回成功したことを確認してから、IAM でアクセスキーを
**まず Inactive にし**、数日運用して問題が無ければ削除する。GitHub Secrets からも消す。

---

## 3. CloudWatch Logs の保持期間（必須）

ロググループは既定で「失効しない」。タスク定義には保持期間を書けないのでロググループ側で設定する。
1回実行すれば以後も有効。

```bash
aws logs put-retention-policy \
  --log-group-name /ecs/my-life-app-task \
  --retention-in-days 30 \
  --region ap-northeast-1
```

なお本番の JDBC ログレベルはコード側（`application.properties` の既定を `WARN` に変更）と
タスク定義の環境変数 `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_JDBC=WARN` の二重で塞いである。
それ以前に出力されたログには、ノート本文・ユーザー名・BCrypt ハッシュが含まれている可能性がある。
**保持期間を設定するだけでなく、過去ログの扱い（削除するか）も判断すること。**

---

## 4. RDS への接続を TLS 化

SSM の `/mylifeapp/prod/datasource-url` に `useSSL=false` と `allowPublicKeyRetrieval=true` が
含まれている場合、ECS と RDS の間の通信が平文になる。VPC 内に足場を得た攻撃者が
SQL とその結果（＝ユーザーのメモ本文）を傍受できる。

```bash
# 現在値を確認
aws ssm get-parameter --name /mylifeapp/prod/datasource-url \
  --with-decryption --region ap-northeast-1 --query Parameter.Value --output text
```

`useSSL=false` が含まれていたら次の形に更新する（ホスト名等は既存の値を流用）:

```
jdbc:mysql://<HOST>:3306/<DB>?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Tokyo&connectTimeout=3000&socketTimeout=30000
```

- `allowPublicKeyRetrieval=true` は削除する（TLS 有効時は不要。MITM が提示した公開鍵を
  検証なしに受け入れる挙動を許すため）
- `connectTimeout` / `socketTimeout` は必須。ドライバの既定はどちらも `0`（無制限）で、
  RDS のフェイルオーバー時に Tomcat の全スレッドが無限に滞留して全機能が停止する
- RDS パラメータグループで `require_secure_transport=ON` にし、DB 側からも平文接続を拒否する

TLS 化と同じタイミングで **RDS パスワードのローテーション**も行うのが安全
（平文接続を経由していた期間の傍受可能性を消すため）。
なお RDS 資格情報が git 履歴に入った形跡は無い（`git log -S 'rds.amazonaws.com' --all` が 0 件）ため、
リポジトリ経由の漏洩は起きていない。ローテーションは必須ではなく推奨。

---

## 5. 本番の初期管理者ユーザー

登録 API から `role` を受け取るのをやめたため、**アプリ経由では ADMIN を作れない**。
最初の1人だけ DB に直接投入する。

```sql
-- ハッシュはアプリと同じ BCrypt(cost 10)。下のコマンドで生成する。
INSERT INTO users (username, password, enabled, role)
VALUES ('<ADMIN_NAME>', '<BCRYPT_HASH>', 1, 'ADMIN');
```

ハッシュの生成（ローカルで実行。生パスワードはシェル履歴に残さないこと）:

```bash
# htpasswd が使える場合（bcrypt cost 10）
htpasswd -bnBC 10 "" '<RAW_PASSWORD>' | tr -d ':\n'
```

以後の権限付与は管理者専用 API から行う。

```
PUT /api/admin/users/{id}/role      {"role": "ADMIN"}
PUT /api/admin/users/{id}/enabled   {"enabled": false}
```

`data.sql` に入っている `admin` / `testuser`（パスワード `testpass`）は
**ローカル開発専用**で、本番では実行されない（`spring.sql.init.mode=never`）。

---

## 6. ALB のヘルスチェックを依存先まで見るものに変える

`/health` は DB の疎通を確認するようになり、失敗時に 503 を返す（以前は無条件に 200 だった）。
より詳細な `/actuator/health/readiness` も無認証で到達できるようにしてある。

ALB のターゲットグループのヘルスチェックパスを次に変更する:

```
/actuator/health/readiness
```

`readiness` には `db` を含めてあるので、RDS がダウンしたタスクは
ALB のローテーションから外れ、ECS が置換するようになる。

タスク定義にもコンテナレベルのヘルスチェックを追加済み（`startPeriod` 90秒）。

---

## 7. HTTPS 化（フロントエンドの移行が前提）

現在 `APP_API_ENDPOINT_BASE_URL` が `http://my-life-app.s3-website-...` を指している。
**S3 ウェブサイトエンドポイントは HTTPS を提供しない**ため、フロントは HTTP でしか配信できず、
同一ネットワーク上の攻撃者がレスポンスの JS を書き換えて JWT を窃取できる。

1. フロントを CloudFront + ACM 証明書の配信に移す
2. ALB を HTTPS リスナのみにし、HTTP は 301 でリダイレクト
3. タスク定義の `APP_API_ENDPOINT_BASE_URL` を `https://...` に変更
4. **そのあとで** `APP_SECURITY_REQUIRE_HTTPS=true` を環境変数に追加する

4 を先にやるとアプリが HTTP リクエストをすべてリダイレクトするため、
移行が終わるまでは `false`（既定）のままにしておくこと。

---

## 8. GitHub の branch protection

ワークフローに `needs: test` を入れたが、これだけでは PR のマージ自体は止まらない。

Settings → Branches → `main` のルールで:

- Require a pull request before merging
- Require status checks to pass before merging → **`Test` を Required に指定**

---

## 9. メトリクスの収集先

`micrometer-registry-prometheus` を追加し、`/actuator/prometheus` を公開してある
（認証必須。ALB からは到達させない）。

現状メトリクスはアプリ内に留まっているので、収集先を用意する:

- AWS Managed Prometheus (AMP) からスクレイプする、または
- CloudWatch Embedded Metric Format に切り替える

最初に見るべき指標:

| 指標 | 見る理由 |
|---|---|
| `http_server_requests_seconds` (p95/p99, ステータス別) | SLO の根拠値。まだ SLO 自体が未定義 |
| `hikaricp_connections_pending` | プール枯渇。接続10本に対し Tomcat 50スレッドの構成 |
| `jvm_memory_used_bytes{area="heap"}` | `MaxRAMPercentage=70` 適用の効果確認 |
| `jvm_gc_pause_seconds` | Full GC の頻度 |

---

## 10. 測定して SLO を決める

性能レビューで実測された数値のうち、本番環境で取り直すべきもの:

1. **`/api/auth/login` の CPU 飽和点** — BCrypt は 1コアあたり実測 53〜57ms。
   0.5 vCPU では理論上限が約 9 req/s。本番と同じ Fargate 上で 1 → 5 → 10 → 20 rps と
   段階負荷をかけ、`CPUUtilization` と p95 の折れ点を特定する。
   これがログイン経路のスループット上限であり、SLO の根拠値になる。
   （レート制限を入れたので、測定時は `APP_RATE_LIMIT_ENABLED=false` にするか上限値を上げる）
2. **実効ヒープ上限** — 稼働中コンテナで `jcmd 1 VM.flags | grep MaxHeapSize` を確認し、
   1024MB に対して約 716MB になっていること（変更前は 256MB）。
3. **`/api/notes` のレイテンシの行数依存** — 測定済み。下記参照。

### 測定済み: 一覧クエリの索引利用（note 5万行, MySQL 8.0.42）

`user_id` 条件と複合インデックス `idx_note_user_active (user_id, delete_flg, created_at)`、
およびページネーションを入れた効果を実測した。

| クエリ | 実行計画 | 走査行数 | 実測時間 |
|---|---|---|---|
| 修正前: `WHERE delete_flg = false`（所有者条件なし・LIMIT なし） | `type=ALL` / `key=NULL` | 45,010 | 16.5 ms |
| 修正後: `WHERE user_id=? AND delete_flg=false ORDER BY created_at DESC LIMIT 100` | `type=ref` / `key=idx_note_user_active` / **Backward index scan** | 100 | **0.21 ms** |

`ORDER BY created_at DESC` はインデックスを逆方向に辿って解決されるため、
**filesort が発生しない**。これが列順を `(user_id, delete_flg, created_at)` にした理由。

注意点が1つある。深いページは OFFSET の分だけ読み飛ばすコストが残る。

| ページ | 走査行数 | 実測時間 |
|---|---|---|
| `LIMIT 100 OFFSET 0` | 100 | 0.21 ms |
| `LIMIT 100 OFFSET 10000`（101ページ目） | 10,100 | 8.6 ms |

1ユーザーあたり数万件に達する見込みが出てきたら、OFFSET ではなく
`WHERE (created_at, id) < (?, ?)` 形式のキーセットページネーションへ移すこと。
現在の想定データ量では問題にならない。

なお行数が少ないうちは `EXPLAIN` が `type=ALL` を返す。
これは異常ではなく、小さなテーブルでは全走査の方が速いとオプティマイザが判断するため。
索引設計の検証は、上記のように十分な行数を入れてから行う必要がある。

---

## 付録：確認用のログ・クエリ

構造化ログ（`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`）を有効にしたので、
CloudWatch Logs Insights でフィールド検索ができる。

```
# 認証失敗の急増（鍵不一致・攻撃の両方を捉える）
fields @timestamp, reason, path
| filter @message like /jwt_rejected|auth_failure/
| stats count(*) as failures by bin(5m), reason
# 閾値: 単一 reason が 5分間で 100 件超

# 鍵不一致の即時検知（1件でも出たら異常）
fields @timestamp, reason
| filter reason = "BAD_SIGNATURE"
| stats count(*) by bin(1m)

# 本番で DEBUG/TRACE が有効になっていないかの継続監視（設定ミスの再発検知）
fields @timestamp, @message
| filter level = "DEBUG" or level = "TRACE"
| stats count(*) as debugLines by bin(1h)
# 閾値: 1時間で 1 件以上でアラート

# レート制限の発動状況
fields @timestamp, client, path
| filter @message like /rate_limited/
| stats count(*) by bin(5m), client
```

ログレベルの規約:

| レベル | 用途 | 例 |
|---|---|---|
| ERROR | 人が起きて対応する。アラート対象 | 未捕捉例外、ヘルスチェックの DB 失敗 |
| WARN | 異常だが自動回復 / 想定内の失敗 | 認証失敗、JWT 検証失敗、403 拒否、レート制限 |
| INFO | 業務上の状態遷移 | ログイン成功、権限変更、起動時の実効設定 |
| DEBUG | ローカルのみ。本番で有効化しない | SQL、バインド値 |

**認証失敗を ERROR にしないこと。** 総当たり攻撃で大量発生するとアラート疲れを起こし、
本物の ERROR が埋もれる。WARN に置き、「単位時間あたりの失敗率」でアラートを組む。
