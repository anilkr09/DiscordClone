# 1. Use an official Java image as base
FROM eclipse-temurin:17-jdk-jammy
# 2. Set the working directory in the container
WORKDIR /app

# 3. Copy the jar file from host to container
COPY /build/libs/*.jar app.jar
# 4. Set environment variables (you can set default values here)
ENV SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/discord_clone?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
ENV SPRING_DATASOURCE_USERNAME=root
ENV SPRING_DATASOURCE_PASSWORD=A@n12345
ENV SPRING_REDIS_HOST=host.docker.internal
ENV SPRING_REDIS_PORT=6379
# 4. Expose port (optional, helps with documentation)
EXPOSE 8082

# 5. Command to run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
