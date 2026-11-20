# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Run unit tests as part of the image build; tests do not require live DB/Redis.
RUN mvn -B clean verify \
    && test -f /app/target/redis-token-management-0.0.1-SNAPSHOT.jar

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/redis-token-management-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
