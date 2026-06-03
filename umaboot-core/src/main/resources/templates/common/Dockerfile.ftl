# syntax=docker/dockerfile:1.6
# Multi-stage Dockerfile for the generated Spring Boot project.
<#if isGradle>
# Build stage caches Gradle dependencies via BuildKit cache mounts.
# The Gradle build image supplies Gradle, so no wrapper files are required.

FROM gradle:${gradleVersion}-jdk${javaVersion} AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon compileJava
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon -x test bootJar
<#else>
# Build stage caches Maven dependencies via BuildKit cache mounts.

FROM maven:3.9-eclipse-temurin-${javaVersion} AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package
</#if>

FROM ${dockerBaseImage}
WORKDIR /app
<#if isGradle>
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
<#else>
COPY --from=build /workspace/target/*.jar /app/app.jar
</#if>
EXPOSE ${dockerPort?c}
ENTRYPOINT ["java","-jar","/app/app.jar"]
