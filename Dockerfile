# ベースイメージ（軽量なJDK）
FROM amazoncorretto:21-alpine3.18

WORKDIR /app

COPY build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]