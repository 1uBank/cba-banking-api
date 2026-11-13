# Banking API image. Build: docker build -t banking-api .
# The same Maven version as the wrapper (.mvn/wrapper/maven-wrapper.properties).
FROM maven:3.9.15-eclipse-temurin-21-noble AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src src
COPY api api
COPY docs docs
# Tests run in CI before the image is built, not in the image build.
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/banking-api.jar extract --layers --launcher --destination /app

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
