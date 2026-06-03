name: CI

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK ${javaVersion}
        uses: actions/setup-java@v4
        with:
          java-version: '${javaVersion}'
          distribution: 'temurin'
<#if isGradle>

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: ${gradleVersion}

      - name: Compile with Gradle
        run: gradle --no-daemon compileJava

      - name: Build bootJar with Gradle
        run: gradle --no-daemon -x test bootJar

      - name: Run tests
        run: gradle --no-daemon test
<#else>
          cache: maven

      - name: Build with Maven
        run: mvn -B -ntp -DskipTests package

      - name: Run tests
        run: mvn -B -ntp test
</#if>
