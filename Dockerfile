# ---------- Stage 1: Build ----------
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy project files
COPY . .

# Give gradlew permission
RUN chmod +x gradlew

# Build jar (skip tests and PMD)
RUN ./gradlew bootJar -x test -x pmdMain -x pmdTest --no-daemon


# ---------- Stage 2: Run ----------
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
