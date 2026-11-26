# Build and run the application: docker build .
FROM gradle:8.12.1-jdk17 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
COPY config config
COPY contract contract
COPY consumer/build.gradle consumer/
COPY src src
# Split the jar into layers, so that a code change does not rebuild the dependency layer.
RUN gradle :bootJar --no-daemon \
 && cp build/libs/*.jar /tmp/app.jar \
 && java -Djarmode=tools -jar /tmp/app.jar extract --layers --destination /layers

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fs http://localhost:8080/livez || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
