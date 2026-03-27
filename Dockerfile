FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
"-Xms256m", \
"-Xmx512m", \
"-XX:+UseG1GC", \
"-XX:MaxGCPauseMillis=200", \
"-XX:MaxMetaspaceSize=128m", \
"-jar", \
"app.jar"]