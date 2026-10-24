FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SERVER_PORT=8081
COPY target/event-service-1.0.0.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
