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
          cache: maven

      - name: Build with Maven
        run: mvn -B -ntp -DskipTests package

      - name: Run tests
        run: mvn -B -ntp test
