# syntax=docker/dockerfile:1

# Build context must be the repo root (multi-module Maven reactor), e.g. from
# the repo root: docker build -f api-gateway/Dockerfile -t ventry/api-gateway .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Every module's pom.xml must be present for the root aggregator POM to parse
# (Maven resolves the full <modules> list before -pl/-am filtering is applied),
# even though only api-gateway itself is actually compiled below.
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY event-service/pom.xml event-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY qr-ticket-service/pom.xml qr-ticket-service/pom.xml

RUN mvn -B -pl api-gateway -am -DskipTests dependency:go-offline

COPY api-gateway/src api-gateway/src
RUN mvn -B -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd -r ventry && useradd -r -g ventry ventry
WORKDIR /app
COPY --from=build --chown=ventry:ventry /workspace/api-gateway/target/*.jar app.jar
USER ventry
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
