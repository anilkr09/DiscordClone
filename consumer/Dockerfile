# Build from the repository root: docker build -f consumer/Dockerfile .
FROM gradle:8.12.1-jdk17 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
COPY contract contract
COPY consumer consumer
RUN gradle :consumer:bootJar --no-daemon

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/consumer/build/libs/consumer.jar app.jar
USER app
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fs http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
