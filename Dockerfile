# ベースイメージ（軽量なJDK）
FROM amazoncorretto:21-alpine3.18

WORKDIR /app

COPY build/libs/mylifeapp-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]