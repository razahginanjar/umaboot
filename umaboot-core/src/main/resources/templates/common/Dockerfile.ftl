# syntax=docker/dockerfile:1.6
# Multi-stage Dockerfile for the generated Spring Boot project.
# Build stage caches Maven dependencies via BuildKit cache mounts.

FROM maven:3.9-eclipse-temurin-${javaVersion} AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM ${dockerBaseImage}
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
EXPOSE ${dockerPort?c}
ENTRYPOINT ["java","-jar","/app/app.jar"]
