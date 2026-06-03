<#if isGradle>
image: gradle:${gradleVersion}-jdk${javaVersion}

variables:
  GRADLE_USER_HOME: "$CI_PROJECT_DIR/.gradle"

cache:
  paths:
    - .gradle/caches/
    - .gradle/wrapper/

stages:
  - compile
  - build
  - test

compile:
  stage: compile
  script:
    - gradle --no-daemon compileJava

build:
  stage: build
  script:
    - gradle --no-daemon -x test bootJar
  artifacts:
    paths:
      - build/libs/*.jar
    expire_in: 1 week

test:
  stage: test
  script:
    - gradle --no-daemon test
<#else>
image: maven:3.9-eclipse-temurin-${javaVersion}

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
  MAVEN_CLI_OPTS: "--batch-mode --no-transfer-progress"

cache:
  paths:
    - .m2/repository/

stages:
  - build
  - test

build:
  stage: build
  script:
    - mvn $MAVEN_CLI_OPTS -DskipTests package
  artifacts:
    paths:
      - target/*.jar
    expire_in: 1 week

test:
  stage: test
  script:
    - mvn $MAVEN_CLI_OPTS test
</#if>
