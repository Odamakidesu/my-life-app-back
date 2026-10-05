# ローカルの Kubernetes で動かす（Docker Desktop）

docker compose と同じアプリを、Docker Desktop に入っている Kubernetes の上で動かす手順。
お金はかからず、クラウドにも何も作らない。マニフェストは `k8s/` にある。

## 全体像

```
ブラウザ ── http://localhost:8090 ──▶ front（nginx）
                                      ├─ /      → ビルド済みの画面
                                      └─ /api/  → backend（Spring Boot）──▶ mysql（StatefulSet + PVC）
```

| リソース | 中身 | compose との対応 |
|---|---|---|
| `mysql`（StatefulSet + Service） | MySQL 8.0.42。データは PVC（2Gi）に置く | `db` サービスと `db-data` ボリューム |
| `backend`（Deployment + Service） | `mylifeapp-backend:local`。0.5 vCPU / 1GiB、起動時に Flyway | `backend` サービス |
| `front`（Deployment + LoadBalancer） | `mylifeapp-front:local`。nginx が /api を backend に中継 | なし（compose では `npm start`） |
| Secret（`mysql` / `backend`） | DB パスワードと JWT 署名鍵（ローカル専用の値） | compose の既定値と同じ |

画面と API が同じオリジン（localhost:8090）になるので、CORS の設定を気にしなくてよい。

## 1. Kubernetes を有効にする（初回だけ）

1. Docker Desktop の Settings → Kubernetes → **Enable Kubernetes** をオンにする。
   クラスタの種類を選べる版では **kubeadm** を選ぶ（手元で `docker build` したイメージをそのまま使えるため）。
2. Apply & restart。左下に Kubernetes running と出れば完了。
3. 確認:

```powershell
kubectl config use-context docker-desktop
kubectl get nodes   # docker-desktop が Ready なら OK
```

## 2. イメージを作る

my-life-app-back と my-life-app-front を同じフォルダに並べてチェックアウトしている前提。

```powershell
# my-life-app-back で実行
docker compose build backend                       # mylifeapp-backend:local
docker build -t mylifeapp-front:local -f k8s/front/Dockerfile ../my-life-app-front
```

コードを変えたら、ここをやり直してから「5. 更新を反映する」を行う。

## 3. 起動する

```powershell
kubectl apply -k k8s/
kubectl get pods -n mylifeapp -w      # 3つとも READY 1/1 になるまで待つ（backend は1分ほど）
```

http://localhost:8090 を開き、`testuser` / `testpass`（管理者は `admin` / `testpass`）でログインする。

docker compose と同時に動かしてもポートは衝突しない（compose は 8081 と 13306、こちらは 8090 だけ）。

## 4. 試してみると面白い操作

```powershell
# 状態を見る
kubectl get all -n mylifeapp
kubectl logs -n mylifeapp deploy/backend -f

# Pod を消しても自動で作り直される（自己修復）
kubectl delete pod -n mylifeapp -l app=backend
kubectl get pods -n mylifeapp -w

# DB の Pod を消してもデータは残る（PVC）。ログインして作ったメモがそのまま見えるはず
kubectl delete pod -n mylifeapp mysql-0

# レプリカを増やす / 戻す（Flyway は DB ロックで直列化されるので同時起動しても安全）
kubectl scale deployment/backend -n mylifeapp --replicas=2
kubectl scale deployment/backend -n mylifeapp --replicas=1

# DB を止めると backend の readiness が落ち、Service から外れる様子が見える
kubectl scale statefulset/mysql -n mylifeapp --replicas=0
kubectl get pods -n mylifeapp       # backend が READY 0/1 になる
kubectl scale statefulset/mysql -n mylifeapp --replicas=1

# DB に直接入る
kubectl exec -it -n mylifeapp mysql-0 -- mysql -u mylifeapp -plocalonly-app-password mylifeapp
```

注意: backend を2つ以上にすると、ゴミ箱の自動削除ジョブ（毎日 3:30）も Pod ごとに動く。
削除は何度実行しても結果が同じなので害はないが、本番で複数台にするなら1台だけで動く仕組み（ShedLock 等）が要る。

## 5. 更新を反映する

イメージのタグは `:local` 固定なので、作り直しただけでは Pod は入れ替わらない。再起動させる:

```powershell
kubectl rollout restart deployment/backend -n mylifeapp
kubectl rollout restart deployment/front -n mylifeapp
```

マニフェスト（`k8s/*.yaml`）を変えたときは `kubectl apply -k k8s/` をもう一度実行する。

## 6. 片付ける

```powershell
kubectl delete -k k8s/          # Namespace ごと消える。DB のデータ（PVC）も消える
```

Kubernetes 自体を止めるには、Docker Desktop の設定でオフにする（メモリを2GB前後使っているため）。

## うまく動かないとき

| 症状 | 原因と対処 |
|---|---|
| Pod が `ErrImagePull` / `ImagePullBackOff` | イメージが無いか、クラスタから見えていない。手順2をやり直す。クラスタの種類が kind だと手元のイメージが見えないことがあるので kubeadm にする |
| backend が `CrashLoopBackOff` | `kubectl logs -n mylifeapp deploy/backend --previous` で起動ログを見る。DB 接続エラーなら mysql の Pod が READY か確認 |
| backend がずっと `0/1` | 起動中（Flyway を含め1分前後）。2分を超えるなら `kubectl describe pod` の Events を見る |
| localhost:8090 が開かない | `kubectl get svc -n mylifeapp front` の EXTERNAL-IP が localhost になっているか確認 |
