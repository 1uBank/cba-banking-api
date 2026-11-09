# Banking API image. Build: docker build -t banking-api .
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar \
 && java -Djarmode=tools -jar build/libs/banking-api-*.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:21.0.12.1_1-jre-noble
RUN groupadd --system banking-api && useradd --system --gid banking-api --no-create-home banking-api
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER banking-api
# 8080: the API. 9001: health (/actuator/health/liveness, /actuator/health/readiness) and metrics (/actuator/prometheus).
EXPOSE 8080 9001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
