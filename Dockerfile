FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:17-jre
RUN groupadd --system --gid 10001 turn \
    && useradd --system --uid 10001 --gid turn --home-dir /nonexistent --shell /usr/sbin/nologin turn
WORKDIR /app
COPY --from=build /workspace/target/turn-api-*.jar app.jar
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
