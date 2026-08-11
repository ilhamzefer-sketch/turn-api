FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B clean package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S turn && adduser -S turn -G turn
WORKDIR /app
COPY --from=build /workspace/target/turn-api-*.jar app.jar
USER turn:turn
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
