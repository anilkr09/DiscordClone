        FROM eclipse-temurin:17-jre-jammy

        WORKDIR /app

        COPY build/libs/*.jar app.jar

        EXPOSE 8080


          ENTRYPOINT ["java","-Xms64m","-Xmx384m","-XX:MaxMetaspaceSize=96m","-XX:+UseSerialGC","-XX:TieredStopAtLevel=1","-Xss256k","-jar","app.jar"]