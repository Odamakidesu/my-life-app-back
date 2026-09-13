# =============================================================================
# ビルド段（ソースから jar を作る）
#
# 以前は「あらかじめ手元で ./gradlew bootJar しておく」前提だったため、
# クリーンなチェックアウトから docker build が通らず、
# 手元のビルド成果物（ローカル設定を含みうる）に依存していた。
# ここでソースからビルドすることで、イメージの中身がリポジトリの内容だけで決まる。
# =============================================================================
FROM amazoncorretto:21-alpine AS builder

WORKDIR /workspace

# 依存の解決だけを先に済ませ、ソース変更のたびに再ダウンロードしないようにする。
# gradlew は .gitattributes で LF 固定。実行ビットに依存しないよう sh 経由で呼ぶ。
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sh ./gradlew --no-daemon --version > /dev/null \
    && sh ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src src

# テストは実行しない。Testcontainers が Docker を必要とするため、
# イメージのビルド中には動かせない。テストは CI の test ジョブが担当する。
RUN sh ./gradlew --no-daemon bootJar -x test


# =============================================================================
# 実行段
# =============================================================================
# Alpine 3.18 は 2025年5月にコミュニティサポートが終了しており
# OS パッケージのセキュリティ更新が提供されないため、現行タグに追随する。
FROM amazoncorretto:21-alpine

# 非 root 実行。root のままだと、任意コード実行に至るバグが1つあるだけで
# /proc/self/environ から JWT_SECRET と DB パスワードを読まれ、
# ECS メタデータエンドポイントからタスクロールの資格情報まで取られる。
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=builder --chown=app:app \
     /workspace/build/libs/mylifeapp-0.0.1-SNAPSHOT.jar app.jar

USER app

EXPOSE 8080

# MaxRAMPercentage を指定しないと、コンテナ 1024MB に対してヒープが既定 25%（256MB）に
# 留まり、割り当てたメモリの 75% を死蔵する。70% はヒープに約 716MB を与え、
# 残り約 300MB を Metaspace / スレッドスタック / ネイティブに残す配分。
#
# ExitOnOutOfMemoryError は OOME 後にゾンビ状態で 200 を返し続けるのを防ぎ、
# ECS に確実にタスクを置換させるために必要。
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=70", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp", \
    "-jar", "app.jar"]
