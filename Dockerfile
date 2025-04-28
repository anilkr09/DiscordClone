# 1. Use an official Java image as base
FROM openjdk:17-jdk-slim

# 2. Set the working directory in the container
WORKDIR /app

# 3. Copy the jar file from host to container
COPY /build/libs/*.jar app.jar

# 4. Expose port (optional, helps with documentation)
EXPOSE 8083

# 5. Command to run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
