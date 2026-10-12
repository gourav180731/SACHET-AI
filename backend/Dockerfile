# SACHET AI backend image (Java 22 / Spring Boot 3, multi-stage).
FROM maven:3.9-eclipse-temurin-22 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:22-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && adduser --disabled-password --gecos "" sachet
WORKDIR /app
COPY --from=build /src/target/sachet-backend-*.jar /app/app.jar
RUN chown sachet:sachet /app/app.jar
USER sachet
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
