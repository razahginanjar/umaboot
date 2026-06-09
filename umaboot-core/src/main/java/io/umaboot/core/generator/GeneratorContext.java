package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;

import java.util.Objects;

/**
 * Shared options passed to all generators in a single run.
 *
 * @param basePackage         e.g. {@code com.example.app}
 * @param projectName         Maven artifactId of the generated project
 * @param projectGroup        Maven groupId of the generated project
 * @param springBootVersion   Spring Boot version to declare in the generated POM
 * @param javaVersion         Target Java version for the generated project (e.g. "17")
 * @param useLombok           if true, generate Lombok-annotated entities/DTOs
 * @param lombokVersion       optional explicit Lombok dependency version for older Spring Boot lines
 * @param architecture        {@code mvc | hexagonal | ddd}
 * @param persistence         {@code jpa | mybatis | jooq}
 * @param mybatisStyle        when persistence=mybatis: {@code xml | annotation}
 * @param useMapStruct        when persistence=jpa: use MapStruct mappers (compile-time)
 * @param openApiStyle        {@code yaml | annotation | none}
 * @param injectionStyle      bean injection style for services/controllers:
 *                            {@code constructor | lombok | autowired}
 * @param validationStyle     {@code jakarta | none | service}
 * @param dtoStyle            {@code class | record}
 * @param dtoShape            {@code separate | single}
 * @param exceptionStyle      {@code problemdetail | envelope}
 * @param ddd                 DDD-specific options (aggregate-root list, shared kernel package)
 * @param overlay             true when output mode is overlay — generators skip emitting
 *                            project-wide files (pom.xml, Application.java, application.yml,
 *                            GlobalExceptionHandler) so they don't clobber an existing project
 */
public record GeneratorContext(
        String basePackage,
        String projectName,
        String projectGroup,
        String springBootVersion,
        String javaVersion,
        boolean useLombok,
        String lombokVersion,
        String architecture,
        String persistence,
        String mybatisStyle,
        boolean useMapStruct,
        String openApiStyle,
        String injectionStyle,
        String validationStyle,
        String dtoStyle,
        String dtoShape,
        String exceptionStyle,
        UmabootConfig.AuditOptions audit,
        UmabootConfig.SoftDeleteOptions softDelete,
        UmabootConfig.DockerOptions docker,
        UmabootConfig.CiOptions ci,
        UmabootConfig.LoggingOptions logging,
        UmabootConfig.TestOptions tests,
        UmabootConfig.MigrationOptions migrations,
        String paginationStyle,
        UmabootConfig.SecurityOptions security,
        UmabootConfig.DddOptions ddd,
        boolean overlay,
        String dbDriver,
        UmabootConfig.Connection connection,
        String schemaFileSql,
        UmabootConfig.ApplicationConfigOptions applicationConfig,
        String classNameStripPrefix,
        java.util.Map<String, UmabootConfig.TableOverride> tableOverrides,
        String buildTool) {

    public GeneratorContext {
        Objects.requireNonNull(basePackage, "basePackage");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(projectGroup, "projectGroup");
        Objects.requireNonNull(springBootVersion, "springBootVersion");
        Objects.requireNonNull(javaVersion, "javaVersion");
        javaVersion = normalizeJavaVersion(javaVersion);
        lombokVersion = normalizeOptional(lombokVersion);
        if (!useLombok || !requiresExplicitLombokVersion(springBootVersion)) {
            lombokVersion = null;
        } else if (lombokVersion == null) {
            lombokVersion = defaultLombokVersion();
        }
        architecture = architecture == null ? "mvc" : architecture.toLowerCase();
        persistence = persistence == null ? "jpa" : persistence.toLowerCase();
        mybatisStyle = mybatisStyle == null ? "xml" : mybatisStyle.toLowerCase();
        openApiStyle = openApiStyle == null ? "yaml" : openApiStyle.toLowerCase();
        injectionStyle = injectionStyle == null ? "constructor" : injectionStyle.toLowerCase();
        validationStyle = validationStyle == null ? "jakarta" : validationStyle.toLowerCase();
        dtoStyle = dtoStyle == null ? "class" : dtoStyle.toLowerCase();
        dtoShape = dtoShape == null ? "separate" : dtoShape.toLowerCase();
        exceptionStyle = exceptionStyle == null ? "problemdetail" : exceptionStyle.toLowerCase();
        if (!"constructor".equals(injectionStyle) && !"lombok".equals(injectionStyle) && !"autowired".equals(injectionStyle)) {
            throw new IllegalArgumentException(
                    "injectionStyle must be 'constructor', 'lombok', or 'autowired' (got: " + injectionStyle + ")");
        }
        if ("lombok".equals(injectionStyle) && !useLombok) {
            throw new IllegalArgumentException("injectionStyle=lombok requires useLombok=true");
        }
        ddd = ddd == null ? UmabootConfig.DddOptions.defaults() : ddd;
        audit = audit == null ? UmabootConfig.AuditOptions.defaults() : audit;
        softDelete = softDelete == null ? UmabootConfig.SoftDeleteOptions.defaults() : softDelete;
        docker = docker == null ? UmabootConfig.DockerOptions.defaults() : docker;
        ci = ci == null ? UmabootConfig.CiOptions.defaults() : ci;
        logging = logging == null ? UmabootConfig.LoggingOptions.defaults() : logging;
        tests = tests == null ? UmabootConfig.TestOptions.defaults() : tests;
        migrations = migrations == null ? UmabootConfig.MigrationOptions.defaults() : migrations;
        paginationStyle = paginationStyle == null ? "offset" : paginationStyle.toLowerCase();
        if (!"offset".equals(paginationStyle) && !"cursor".equals(paginationStyle)) {
            throw new IllegalArgumentException(
                    "paginationStyle must be 'offset' or 'cursor' (got: " + paginationStyle + ")");
        }
        security = security == null ? UmabootConfig.SecurityOptions.defaults() : security;
        dbDriver = dbDriver == null ? "postgres" : dbDriver.toLowerCase();
        // Accept "postgres" / "postgresql" interchangeably; canonicalize to "postgresql"
        // for downstream switches. MariaDB and MySQL share parser logic but have distinct
        // JDBC driver coordinates and JDBC URL prefixes.
        if ("postgres".equals(dbDriver)) dbDriver = "postgresql";
        applicationConfig = applicationConfig == null
                ? UmabootConfig.ApplicationConfigOptions.defaults()
                : applicationConfig;
        schemaFileSql = schemaFileSql == null ? null : schemaFileSql;
        classNameStripPrefix = classNameStripPrefix == null ? "" : classNameStripPrefix;
        tableOverrides = tableOverrides == null ? java.util.Map.of() : java.util.Map.copyOf(tableOverrides);
        // Build tool defaults to maven for backwards-compat with older test fixtures
        // and existing umaboot.yamls. Validation lives in UmabootConfig — by the time
        // we reach this ctor, buildTool is either "maven" or "gradle".
        buildTool = (buildTool == null || buildTool.isBlank()) ? "maven" : buildTool.toLowerCase();
    }

    public GeneratorContext(
            String basePackage,
            String projectName,
            String projectGroup,
            String springBootVersion,
            String javaVersion,
            boolean useLombok,
            String architecture,
            String persistence,
            String mybatisStyle,
            boolean useMapStruct,
            String openApiStyle,
            String injectionStyle,
            String validationStyle,
            String dtoStyle,
            String dtoShape,
            String exceptionStyle,
            UmabootConfig.AuditOptions audit,
            UmabootConfig.SoftDeleteOptions softDelete,
            UmabootConfig.DockerOptions docker,
            UmabootConfig.CiOptions ci,
            UmabootConfig.LoggingOptions logging,
            UmabootConfig.TestOptions tests,
            UmabootConfig.MigrationOptions migrations,
            String paginationStyle,
            UmabootConfig.SecurityOptions security,
            UmabootConfig.DddOptions ddd,
            boolean overlay,
            String dbDriver,
            UmabootConfig.Connection connection,
            String schemaFileSql,
            UmabootConfig.ApplicationConfigOptions applicationConfig,
            String classNameStripPrefix,
            java.util.Map<String, UmabootConfig.TableOverride> tableOverrides,
            String buildTool) {
        this(basePackage, projectName, projectGroup, springBootVersion, javaVersion,
                useLombok, null, architecture, persistence, mybatisStyle, useMapStruct,
                openApiStyle, injectionStyle, validationStyle, dtoStyle, dtoShape,
                exceptionStyle, audit, softDelete, docker, ci, logging, tests,
                migrations, paginationStyle, security, ddd, overlay, dbDriver,
                connection, schemaFileSql, applicationConfig, classNameStripPrefix,
                tableOverrides, buildTool);
    }

    public GeneratorContext(
            String basePackage,
            String projectName,
            String projectGroup,
            String springBootVersion,
            String javaVersion,
            boolean useLombok,
            String architecture,
            String persistence,
            String mybatisStyle,
            boolean useMapStruct,
            String openApiStyle,
            String injectionStyle,
            String validationStyle,
            String dtoStyle,
            String dtoShape,
            String exceptionStyle,
            UmabootConfig.AuditOptions audit,
            UmabootConfig.SoftDeleteOptions softDelete,
            UmabootConfig.DockerOptions docker,
            UmabootConfig.CiOptions ci,
            UmabootConfig.LoggingOptions logging,
            UmabootConfig.TestOptions tests,
            String paginationStyle,
            UmabootConfig.SecurityOptions security,
            UmabootConfig.DddOptions ddd,
            boolean overlay,
            String dbDriver,
            UmabootConfig.Connection connection,
            UmabootConfig.ApplicationConfigOptions applicationConfig,
            String classNameStripPrefix,
            java.util.Map<String, UmabootConfig.TableOverride> tableOverrides,
            String buildTool) {
        this(basePackage, projectName, projectGroup, springBootVersion, javaVersion,
                useLombok, null, architecture, persistence, mybatisStyle, useMapStruct,
                openApiStyle, injectionStyle, validationStyle, dtoStyle, dtoShape,
                exceptionStyle, audit, softDelete, docker, ci, logging, tests,
                null, paginationStyle, security, ddd, overlay, dbDriver, connection,
                null, applicationConfig, classNameStripPrefix, tableOverrides, buildTool);
    }

    /** Returns the per-table override for {@code tableName}, or empty if none configured. */
    public java.util.Optional<UmabootConfig.TableOverride> tableOverride(String tableName) {
        return java.util.Optional.ofNullable(tableOverrides.get(tableName));
    }

    /** Defaults: MVC + JPA + Spring Boot 3.3.5 + Java 17 + Lombok, standalone mode. */
    public static GeneratorContext defaults(String basePackage, String projectName) {
        return new GeneratorContext(
                basePackage, projectName, "com.example",
                "3.3.5", "17", true,
                "mvc", "jpa", "xml", false, "yaml", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                "offset",
                UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                false,
                "postgres",
                null,
                null,
                "",
                null,
                "maven");
    }

    public String basePackagePath() {
        return basePackage.replace('.', '/');
    }

    public boolean isMyBatis() { return "mybatis".equalsIgnoreCase(persistence); }
    public boolean isJpa() { return "jpa".equalsIgnoreCase(persistence); }
    public boolean isJooq() { return "jooq".equalsIgnoreCase(persistence); }

    public boolean isHexagonal() { return "hexagonal".equalsIgnoreCase(architecture); }
    public boolean isDdd() { return "ddd".equalsIgnoreCase(architecture); }
    public boolean isMvc() { return "mvc".equalsIgnoreCase(architecture); }

    public boolean myBatisXml() { return isMyBatis() && "xml".equalsIgnoreCase(mybatisStyle); }
    public boolean myBatisAnnotation() { return isMyBatis() && "annotation".equalsIgnoreCase(mybatisStyle); }

    public boolean isOpenApiYaml() { return "yaml".equalsIgnoreCase(openApiStyle); }
    public boolean isOpenApiAnnotation() { return "annotation".equalsIgnoreCase(openApiStyle); }
    public boolean isOpenApiEnabled() { return !"none".equalsIgnoreCase(openApiStyle); }

    public boolean isInjectionConstructor() { return "constructor".equalsIgnoreCase(injectionStyle); }
    public boolean isInjectionLombok() { return "lombok".equalsIgnoreCase(injectionStyle); }
    public boolean isInjectionAutowired() { return "autowired".equalsIgnoreCase(injectionStyle); }

    public boolean isValidationJakarta() { return "jakarta".equalsIgnoreCase(validationStyle); }
    public boolean isValidationNone() { return "none".equalsIgnoreCase(validationStyle); }
    public boolean isValidationService() { return "service".equalsIgnoreCase(validationStyle); }

    public boolean isDtoClass() { return "class".equalsIgnoreCase(dtoStyle); }
    public boolean isDtoRecord() { return "record".equalsIgnoreCase(dtoStyle); }
    public boolean isDtoSeparate() { return "separate".equalsIgnoreCase(dtoShape); }
    public boolean isDtoSingle() { return "single".equalsIgnoreCase(dtoShape); }

    public boolean isExceptionProblemDetail() { return "problemdetail".equalsIgnoreCase(exceptionStyle); }
    public boolean isExceptionEnvelope() { return "envelope".equalsIgnoreCase(exceptionStyle); }

    public boolean isDbMysql() { return "mysql".equalsIgnoreCase(dbDriver); }
    public boolean isDbMariadb() { return "mariadb".equalsIgnoreCase(dbDriver); }
    public boolean isDbSqlserver() { return "sqlserver".equalsIgnoreCase(dbDriver); }
    public boolean isDbSqlite() { return "sqlite".equalsIgnoreCase(dbDriver); }
    /** True for any MySQL-family engine (MySQL or MariaDB). DDL parser routing + Testcontainers
     *  lookups treat them together; only the JDBC URL / driver coords / pom artifact differ. */
    public boolean isDbMysqlFamily() { return isDbMysql() || isDbMariadb(); }
    public boolean isDbPostgres() { return !isDbMysqlFamily() && !isDbSqlserver() && !isDbSqlite(); }

    /** Build tool: maven (status quo) or gradle. */
    public boolean isMaven()  { return "maven".equalsIgnoreCase(buildTool); }
    public boolean isGradle() { return "gradle".equalsIgnoreCase(buildTool); }

    public boolean isMigrationFlyway() { return migrations != null && migrations.isFlyway(); }
    public boolean isMigrationNone() { return migrations == null || migrations.isNone(); }

    /**
     * springdoc changed artifact/version lines across Spring Boot generations.
     * Keep this centralized so Maven and Gradle templates cannot drift.
     */
    public String springdocOpenApiArtifactId() {
        if (isSpringBoot2()) return "springdoc-openapi-ui";
        return "springdoc-openapi-starter-webmvc-ui";
    }

    public String springdocOpenApiVersion() {
        int major = springBootMajor();
        if (major <= 2) return "1.8.0";
        if (major >= 4) return "3.0.3";

        return switch (springBootMinor()) {
            case 0 -> "2.1.0";
            case 1 -> "2.2.0";
            case 2 -> "2.5.0";
            case 3 -> "2.6.0";
            default -> "2.8.17";
        };
    }

    public String flywayDatabaseModule() {
        if (isDbPostgres()) return "flyway-database-postgresql";
        if (isDbMysqlFamily()) return "flyway-mysql";
        if (isDbSqlserver()) return "flyway-sqlserver";
        return "";
    }

    public boolean renderFlywayDatabaseModule() {
        if (flywayDatabaseModule().isEmpty()) return false;
        if (springBootMajor() >= 3) return true;
        return isDbMysqlFamily() || isDbSqlserver();
    }

    public String gradleVersion() {
        return isSpringBoot2() ? "7.6.4" : "8.11";
    }

    public String jooqVersion() {
        if (isSpringBoot2()) return "3.14.16";
        return "3.19.15";
    }

    public String jooqGradlePluginVersion() {
        if (isSpringBoot2()) return "5.2.2";
        return "9.0";
    }

    public String jooqCodegenDriverGroupId() {
        if (isDbMariadb()) return "org.mariadb.jdbc";
        if (isDbMysql()) return "com.mysql";
        if (isDbSqlserver()) return "com.microsoft.sqlserver";
        if (isDbSqlite()) return "org.xerial";
        return "org.postgresql";
    }

    public String jooqCodegenDriverArtifactId() {
        if (isDbMariadb()) return "mariadb-java-client";
        if (isDbMysql()) return "mysql-connector-j";
        if (isDbSqlserver()) return "mssql-jdbc";
        if (isDbSqlite()) return "sqlite-jdbc";
        return "postgresql";
    }

    public String jooqCodegenDriverVersion() {
        if (isSpringBoot2()) {
            if (isDbMariadb()) return "3.1.4";
            if (isDbMysql()) return "8.0.33";
            if (isDbSqlserver()) return "10.2.3.jre8";
            if (isDbSqlite()) return "3.36.0.3";
            return "42.3.8";
        }
        if (isDbMariadb()) return "3.4.1";
        if (isDbMysql()) return "8.4.0";
        if (isDbSqlserver()) return "12.6.4.jre11";
        if (isDbSqlite()) return "3.47.0.0";
        return "42.7.4";
    }

    public String jooqCodegenDriverCoordinate() {
        return jooqCodegenDriverGroupId()
                + ":" + jooqCodegenDriverArtifactId()
                + ":" + jooqCodegenDriverVersion();
    }

    /**
     * Effective JDBC URL written into the generated {@code application.yml/.properties}
     * as the default value of {@code spring.datasource.url}. Comes from the
     * loaded {@link UmabootConfig.Connection#url()} when available, otherwise
     * falls back to a localhost URL so tests that don't bind a connection
     * still render a syntactically valid yaml.
     */
    public String jdbcUrl() {
        if (connection != null && connection.url() != null && !connection.url().isBlank()) {
            return connection.url();
        }
        if (isDbMariadb())   return "jdbc:mariadb://localhost:3306/" + projectName;
        if (isDbMysql())     return "jdbc:mysql://localhost:3306/" + projectName;
        if (isDbSqlserver()) return "jdbc:sqlserver://localhost:1433;databaseName=" + projectName + ";encrypt=false;trustServerCertificate=true";
        if (isDbSqlite())    return "jdbc:sqlite:./" + projectName + ".db";
        return "jdbc:postgresql://localhost:5432/" + projectName;
    }

    /** JDBC username for the generated app — from connection block, or engine default. */
    public String jdbcUsername() {
        if (connection != null && connection.username() != null && !connection.username().isEmpty()) {
            return connection.username();
        }
        // SQLite is file-based — no auth — empty string is the right default.
        if (isDbSqlite())    return "";
        if (isDbSqlserver()) return "sa";
        return isDbMysqlFamily() ? "root" : "postgres";
    }

    /** JDBC password for the generated app — from connection block, or engine default. */
    public String jdbcPassword() {
        if (connection != null && connection.password() != null && !connection.password().isEmpty()) {
            return connection.password();
        }
        if (isDbSqlite())    return "";
        if (isDbSqlserver()) return "Your_password123";
        return isDbMysqlFamily() ? "root" : "postgres";
    }

    /** Driver class name, derived from {@link #dbDriver}. */
    public String jdbcDriverClass() {
        if (isDbMariadb())   return "org.mariadb.jdbc.Driver";
        if (isDbMysql())     return "com.mysql.cj.jdbc.Driver";
        if (isDbSqlserver()) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (isDbSqlite())    return "org.sqlite.JDBC";
        return "org.postgresql.Driver";
    }

    public boolean isApplicationConfigYaml() {
        return applicationConfig != null && applicationConfig.isYaml();
    }

    public boolean isApplicationConfigProperties() {
        return applicationConfig != null && applicationConfig.isProperties();
    }

    /** File name of the generated application config — e.g. "application.yml" or "application.properties". */
    public String applicationConfigFileName() {
        return isApplicationConfigProperties() ? "application.properties" : "application.yml";
    }

    public boolean isPaginationOffset() { return "offset".equalsIgnoreCase(paginationStyle); }
    public boolean isPaginationCursor() { return "cursor".equalsIgnoreCase(paginationStyle); }

    /** Major version of Spring Boot (parsed from {@link #springBootVersion}). */
    public int springBootMajor() {
        if (springBootVersion == null || springBootVersion.isEmpty()) return 3;
        int dot = springBootVersion.indexOf('.');
        String head = dot < 0 ? springBootVersion : springBootVersion.substring(0, dot);
        try { return Integer.parseInt(head); } catch (NumberFormatException ex) { return 3; }
    }
    public boolean isSpringBoot2() { return springBootMajor() == 2; }
    public boolean isSpringBoot3() { return springBootMajor() == 3; }

    public boolean requiresLombokVersion() {
        return useLombok && requiresExplicitLombokVersion(springBootVersion);
    }

    public String logstashLogbackEncoderVersion() {
        return isSpringBoot2() ? "7.3" : "7.4";
    }

    public int javaMajor() {
        if (javaVersion == null || javaVersion.isEmpty()) return 17;
        String v = javaVersion.trim();
        if ("1.8".equals(v)) return 8;
        int dot = v.indexOf('.');
        String head = dot < 0 ? v : v.substring(0, dot);
        try { return Integer.parseInt(head); } catch (NumberFormatException ex) { return 17; }
    }

    public boolean javaSupportsStringIsBlank() { return javaMajor() >= 11; }
    public boolean javaSupportsListOf() { return javaMajor() >= 9; }
    public boolean javaSupportsListCopyOf() { return javaMajor() >= 10; }
    public boolean javaSupportsStreamToList() { return javaMajor() >= 16; }

    public int springBootMinor() {
        if (springBootVersion == null || springBootVersion.isEmpty()) return 3;
        String[] parts = springBootVersion.split("[.\\-]");
        if (parts.length < 2) return 0;
        try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { return 3; }
    }

    public boolean isSecurityNone() { return security != null && security.isNone(); }
    public boolean isSecurityBasic() { return security != null && security.isBasic(); }
    public boolean isSecurityJwt() { return security != null && security.isJwt(); }
    public boolean isSecurityEnabled() { return security != null && security.isEnabled(); }

    /**
     * Returns {@code "jakarta"} for Spring Boot 3.x and {@code "javax"} for
     * Spring Boot 2.x. Used by templates as {@code ${eeNamespace}.persistence.*}
     * etc. so a single template renders correctly for both Jakarta EE and
     * the legacy Java EE namespace.
     */
    public String eeNamespace() {
        return isSpringBoot2() ? "javax" : "jakarta";
    }

    /** Legacy helper for templates that still use the boolean flag. */
    public boolean generateOpenApi() { return isOpenApiEnabled(); }

    private static boolean requiresExplicitLombokVersion(String springBootVersion) {
        int major = parseSpringBootMajor(springBootVersion);
        int minor = parseSpringBootMinor(springBootVersion);
        return major < 3 || (major == 3 && minor < 5);
    }

    private static int parseSpringBootMajor(String springBootVersion) {
        if (springBootVersion == null || springBootVersion.isEmpty()) return 3;
        int dot = springBootVersion.indexOf('.');
        String head = dot < 0 ? springBootVersion : springBootVersion.substring(0, dot);
        try { return Integer.parseInt(head); } catch (NumberFormatException ex) { return 3; }
    }

    private static int parseSpringBootMinor(String springBootVersion) {
        if (springBootVersion == null || springBootVersion.isEmpty()) return 0;
        String[] parts = springBootVersion.split("[.\\-]");
        if (parts.length < 2) return 0;
        try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { return 0; }
    }

    private static String defaultLombokVersion() {
        return "1.18.30";
    }

    private static String normalizeJavaVersion(String version) {
        String trimmed = version == null ? "" : version.trim();
        if ("1.8".equals(trimmed)) return "8";
        return trimmed.isEmpty() ? "17" : trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
