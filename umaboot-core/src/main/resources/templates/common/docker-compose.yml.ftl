# Generated docker-compose for local development.
# Override the env vars below (e.g. via .env or your shell) before `docker compose up`.

services:
  app:
    build: .
    image: ${projectName}:latest
    container_name: ${projectName}
    ports:
      - "${dockerPort?c}:${dockerPort?c}"
    depends_on:
      db:
        condition: service_healthy
<#if dbIsMysql>
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/${r"${DB_NAME:-app}"}?useSSL=false&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: ${r"${DB_USER:-app}"}
      SPRING_DATASOURCE_PASSWORD: ${r"${DB_PASSWORD:-app}"}

  db:
    image: mysql:8.0
    container_name: ${projectName}-db
    environment:
      MYSQL_DATABASE: ${r"${DB_NAME:-app}"}
      MYSQL_USER: ${r"${DB_USER:-app}"}
      MYSQL_PASSWORD: ${r"${DB_PASSWORD:-app}"}
      MYSQL_ROOT_PASSWORD: ${r"${DB_ROOT_PASSWORD:-rootpw}"}
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 5s
      retries: 10
    volumes:
      - db-data:/var/lib/mysql
<#else>
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${r"${DB_NAME:-app}"}
      SPRING_DATASOURCE_USERNAME: ${r"${DB_USER:-app}"}
      SPRING_DATASOURCE_PASSWORD: ${r"${DB_PASSWORD:-app}"}

  db:
    image: postgres:16-alpine
    container_name: ${projectName}-db
    environment:
      POSTGRES_DB: ${r"${DB_NAME:-app}"}
      POSTGRES_USER: ${r"${DB_USER:-app}"}
      POSTGRES_PASSWORD: ${r"${DB_PASSWORD:-app}"}
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${r"${DB_USER:-app}"}"]
      interval: 5s
      timeout: 5s
      retries: 10
    volumes:
      - db-data:/var/lib/postgresql/data
</#if>

volumes:
  db-data:
