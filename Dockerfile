FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY crawler-0.0.1-SNAPSHOT.jar app.jar

ENV TZ Asia/Seoul

EXPOSE 9001

ENTRYPOINT ["java", "-jar", "app.jar"]