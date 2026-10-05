# フロントエンドのイメージ。ビルドコンテキストは my-life-app-front のチェックアウト。
#
#   docker build -t mylifeapp-front:local -f k8s/front/Dockerfile ../my-life-app-front
#
# API の URL は相対パス（/api）で焼き込む。画面と同じオリジンの nginx が backend に中継する。
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ENV VITE_API_BASE_URL=/api
# 出力先は vite.config.ts の build.outDir（./build）。
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY --from=builder /app/build /usr/share/nginx/html
EXPOSE 8080
