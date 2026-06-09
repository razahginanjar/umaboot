package io.umaboot.core.config;

import java.util.Objects;

/**
 * Top-level {@code umaboot.yaml} model.
 *
 * <p>Supports three architectures, three persistence backends, per-backend tuning,
 * table filtering, DDD aggregate-root overrides, and an output mode for
 * standalone vs overlay generation.</p>
 */
public record UmabootConfig(Connection connection, Generation generation) {

    public UmabootConfig {
        Objects.requireNonNull(generation, "generation");
        // The schema source is exactly one of:
        //   * connection: live JDBC introspection (default for v0.x — backwards compatible)
        //   * generation.schemaFile: parse a checked-in .sql file with JSqlParser (v1.0+)
        // Reject both-set or neither-set at config-load — clearer than failing later
        // with an "introspection target empty" or "no schemaFile" mid-pipeline.
        boolean hasConnection = connection != null;
        boolean hasSchemaFile = generation.schemaFile() != null && !generation.schemaFile().isBlank();
        if (!hasConnection && !hasSchemaFile) {
            throw new IllegalArgumentException(
                    "Schema source missing: set either `connection` (live database) "
                            + "or `generation.schemaFile` (path to a .sql DDL file).");
        }
        if (hasConnection && hasSchemaFile) {
            throw new IllegalArgumentException(
                    "Schema source ambiguous: set either `connection` or "
                            + "`generation.schemaFile`, not both. "
                            + "Comment out the one you don't want to use.");
        }
        // jOOQ's generated codegen plugin needs a live JDBC connection at `mvn compile`
        // time to produce ${basePackage}.jooq.Tables. SqlFile mode skips that, so the
        // generated adapter wouldn't compile. Reject the combination clearly.
        if (hasSchemaFile && "jooq".equalsIgnoreCase(generation.persistence())) {
            throw new IllegalArgumentException(
                    "persistence: jooq requires a live `connection:` — the generated "
                            + "jooq-codegen-maven plugin needs JDBC access at `mvn compile` "
                            + "time to introspect the schema. Use `persistence: jpa` or "
                            + "`persistence: mybatis` with `schemaFile:` instead.");
        }
    }

    /** True when the schema source is a parsed .sql file rather than a live connection. */
    public boolean isSchemaFileMode() {
        return generation.schemaFile() != null && !generation.schemaFile().isBlank();
    }

    public record Connection(
            String mode,        // "host" | "url"
            String type,        // "postgresql" | "mysql"
            String host,        // host mode: everything after jdbc:<type>://, before /<database>
            String params,      // host mode: extra JDBC query string, no leading "?"
            String url,         // url mode: full JDBC URL. In host mode, computed from the parts.
            String database,    // database / catalog (engine-specific). May be empty.
            String schema,      // postgres schema (e.g. "public"). Ignored by mysql at introspect time.
            String username,
            String password,
            String driver       // legacy alias for `type`. Always equals type after canonicalization.
    ) {
        public Connection {
            mode = (mode == null || mode.isBlank()) ? "url" : mode.toLowerCase();
            if (!"host".equals(mode) && !"url".equals(mode)) {
                throw new IllegalArgumentException(
                        "connection.mode must be 'host' or 'url' (got: " + mode + ")");
            }

            // Canonicalize type: explicit type wins; otherwise derive from URL prefix; default postgresql.
            if (type == null || type.isBlank()) {
                type = (url != null) ? deriveDriver(url) : "postgresql";
            }
            type = type.toLowerCase();
            // Accept legacy "postgres" as an alias for "postgresql".
            if ("postgres".equals(type)) type = "postgresql";

            host     = host == null ? "" : host;
            params   = params == null ? "" : params;
            database = database == null ? "" : database;
            // SQL Server's default schema is `dbo`; Postgres uses `public`.
            // (mysql/mariadb don't really use this field, but default to `public` for parity.)
            schema   = schema == null
                    ? ("sqlserver".equals(type) ? "dbo" : "public")
                    : schema;
            username = username == null ? "" : username;
            password = password == null ? "" : password;

            // Reject leading "?" in params — URL composition prepends one and we'd
            // produce "??...". The user wanted a hard error rather than silent stripping.
            if ("host".equals(mode) && params.startsWith("?")) {
                throw new IllegalArgumentException(
                        "connection.params must not start with '?' — the program prepends it automatically");
            }

            // Compose the URL when in host mode; require host + database to be present.
            // SQLite is the exception: it has no host/port concept — the database field
            // carries the file path (or :memory:) and the URL is jdbc:sqlite:<path>.
            if ("host".equals(mode)) {
                if (!"sqlite".equals(type) && host.isBlank()) {
                    throw new IllegalArgumentException("connection.host is required when mode=host");
                }
                if (database.isBlank() && !"sqlite".equals(type)) {
                    throw new IllegalArgumentException("connection.database is required when mode=host");
                }
                url = composeUrl(type, host, database, params);
            } else {
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException("connection.url is required when mode=url");
                }
                // In url mode, fill in database from the URL path if the user didn't provide one.
                if (database.isBlank()) {
                    database = parseDatabaseFromUrl(url);
                }
            }

            // driver is a legacy alias kept for any callers still reading it (GenerationPipeline,
            // CLI commands, etc.). Always equals type after canonicalization.
            driver = type;
        }

        /**
         * What to pass to {@code introspector.introspect(...)} — the engine-specific
         * "where to look for tables" identifier.
         *
         * <ul>
         *   <li><b>mysql</b>: the database name (catalog filter). Falls back to
         *       {@link #schema()} when {@code database} is empty — that preserves
         *       the legacy "I typed the DB name in the schema field" workaround.</li>
         *   <li><b>postgresql</b>: the schema name (e.g. {@code public}).</li>
         * </ul>
         */
        public String introspectionTarget() {
            if ("mysql".equals(type) || "mariadb".equals(type)) {
                return database.isBlank() ? schema : database;
            }
            // sqlserver and postgresql both use schema-based introspection.
            // SQL Server's default schema is `dbo` — fall back to that if blank.
            if ("sqlserver".equals(type) && schema.isBlank()) {
                return "dbo";
            }
            // SQLite has no schema concept beyond the `main` ATTACH alias. JDBC
            // metadata calls happily accept null for both catalog and schema, so
            // returning an empty string is the right "ignore" sentinel here —
            // SqliteIntrospector explicitly passes null/null to the metadata API.
            if ("sqlite".equals(type)) {
                return "";
            }
            return schema;
        }

        /**
         * {@code jdbc:<type>://<host>/<database>[?<params>]}.
         * Always single '?'; never '/?'. Empty params produces no '?'.
         *
         * <p>SQL Server's JDBC URL syntax differs: parameters use {@code ;name=value}
         * pairs and the database goes in {@code ;databaseName=foo} rather than the
         * URL path. Example: {@code jdbc:sqlserver://host:1433;databaseName=foo;encrypt=false}.</p>
         */
        public static String composeUrl(String type, String host, String database, String params) {
            if ("sqlserver".equals(type)) {
                String base = "jdbc:sqlserver://" + host + ";databaseName=" + database;
                return (params == null || params.isBlank()) ? base : base + ";" + params;
            }
            if ("sqlite".equals(type)) {
                // SQLite has no host/port — the "database" field carries either a file path
                // (e.g. "./shop.db") or the literal ":memory:" sentinel.
                String path = (database == null || database.isBlank()) ? ":memory:" : database;
                return "jdbc:sqlite:" + path;
            }
            String base = "jdbc:" + type + "://" + host + "/" + database;
            return (params == null || params.isBlank()) ? base : base + "?" + params;
        }

        /**
         * Best-effort extraction of the database name from a JDBC URL.
         * Returns "" if not detectable.
         *
         * <p>Handles the SQL Server {@code ;databaseName=} idiom in addition to
         * the path-style of postgres/mysql/mariadb URLs.</p>
         */
        public static String parseDatabaseFromUrl(String url) {
            if (url == null) return "";
            if (url.startsWith("jdbc:sqlserver:")) {
                String lower = url.toLowerCase();
                int idx = lower.indexOf("databasename=");
                if (idx < 0) return "";
                int valStart = idx + "databasename=".length();
                int valEnd = url.indexOf(';', valStart);
                return valEnd < 0 ? url.substring(valStart) : url.substring(valStart, valEnd);
            }
            if (url.startsWith("jdbc:sqlite:")) {
                // SQLite URLs are jdbc:sqlite:<path> or jdbc:sqlite::memory: — the
                // "database" surfaces as the file path or the :memory: sentinel.
                return url.substring("jdbc:sqlite:".length());
            }
            int afterScheme = url.indexOf("://");
            if (afterScheme < 0) return "";
            int pathStart = url.indexOf('/', afterScheme + 3);
            if (pathStart < 0) return "";
            int qmark = url.indexOf('?', pathStart);
            String path = (qmark < 0) ? url.substring(pathStart + 1) : url.substring(pathStart + 1, qmark);
            // Strip trailing slash if present.
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return path;
        }

        private static String deriveDriver(String url) {
            if (url == null) return "postgresql";
            if (url.startsWith("jdbc:mariadb:")) return "mariadb";
            if (url.startsWith("jdbc:mysql:")) return "mysql";
            if (url.startsWith("jdbc:sqlserver:")) return "sqlserver";
            if (url.startsWith("jdbc:sqlite:")) return "sqlite";
            return "postgresql";
        }
    }

    public record Generation(
            String architecture,
            String persistence,
            String basePackage,
            String projectName,
            String projectGroup,
            String springBootVersion,
            String javaVersion,
            boolean useLombok,
            String lombokVersion,
            OpenApiOptions openapi,
            InjectionOptions injection,
            ValidationOptions validation,
            DtoOptions dto,
            ExceptionOptions exception,
            AuditOptions audit,
            SoftDeleteOptions softDelete,
            DockerOptions docker,
            CiOptions ci,
            LoggingOptions logging,
            TestOptions tests,
            MigrationOptions migrations,
            PaginationOptions pagination,
            SecurityOptions security,
            String outputDir,
            JpaOptions jpa,
            MyBatisOptions mybatis,
            TableFilterOptions tables,
            DddOptions ddd,
            OutputOptions output,
            ApplicationConfigOptions applicationConfig,
            String schemaFile,
            String schemaDialect,
            String buildTool) {

        public Generation {
            architecture = architecture == null ? "mvc" : architecture.toLowerCase();
            persistence = persistence == null ? "jpa" : persistence.toLowerCase();
            Objects.requireNonNull(basePackage, "basePackage");
            Objects.requireNonNull(projectName, "projectName");
            projectGroup = projectGroup == null ? "com.example" : projectGroup;
            javaVersion = javaVersion == null ? "17" : normalizeJavaVersion(javaVersion);
            // Smart Spring Boot default keyed off the Java version:
            // Java 8/11 -> 2.7.18 (last 2.x line). Java 17+ -> 3.3.5.
            if (springBootVersion == null) {
                springBootVersion = ("8".equals(javaVersion) || "11".equals(javaVersion))
                        ? "2.7.18"
                        : "3.3.5";
            }
            lombokVersion = normalizeOptional(lombokVersion);
            if (!useLombok || !requiresExplicitLombokVersion(springBootVersion)) {
                lombokVersion = null;
            } else if (lombokVersion == null) {
                lombokVersion = defaultLombokVersion();
            }
            jpa = jpa == null ? new JpaOptions(false) : jpa;
            mybatis = mybatis == null ? new MyBatisOptions("xml") : mybatis;
            tables = tables == null ? TableFilterOptions.allowAll() : tables;
            ddd = ddd == null ? DddOptions.defaults() : ddd;
            output = output == null ? OutputOptions.defaults() : output;
            applicationConfig = applicationConfig == null ? ApplicationConfigOptions.defaults() : applicationConfig;
            schemaFile = (schemaFile != null && schemaFile.isBlank()) ? null : schemaFile;
            schemaDialect = schemaFile == null ? null : canonicalSchemaDialect(schemaDialect);
            // Build tool: maven (status quo) or gradle. Default to maven for backwards
            // compatibility — every existing umaboot.yaml with no buildTool key picks Maven.
            buildTool = buildTool == null ? "maven" : buildTool.toLowerCase();
            if (!"maven".equals(buildTool) && !"gradle".equals(buildTool)) {
                throw new IllegalArgumentException(
                        "buildTool must be 'maven' or 'gradle' (got: " + buildTool + ")");
            }
            openapi = openapi == null ? OpenApiOptions.defaults() : openapi;
            injection = injection == null ? InjectionOptions.defaults() : injection;
            validation = validation == null ? ValidationOptions.defaults() : validation;
            dto = dto == null ? DtoOptions.defaults() : dto;
            exception = exception == null ? ExceptionOptions.defaults() : exception;
            audit = audit == null ? AuditOptions.defaults() : audit;
            softDelete = softDelete == null ? SoftDeleteOptions.defaults() : softDelete;
            docker = docker == null ? DockerOptions.defaults() : docker;
            ci = ci == null ? CiOptions.defaults() : ci;
            logging = logging == null ? LoggingOptions.defaults() : logging;
            tests = tests == null ? TestOptions.defaults() : tests;
            migrations = migrations == null ? MigrationOptions.defaults() : migrations;
            pagination = pagination == null ? PaginationOptions.defaults() : pagination;
            security = security == null ? SecurityOptions.defaults() : security;
            // Cross-field validation: JWT mode requires a non-empty secret to sign tokens.
            if (security.isJwt() && (security.jwt().secret() == null || security.jwt().secret().isEmpty())) {
                throw new IllegalArgumentException(
                        "security.style: jwt requires security.jwt.secret to be set "
                                + "(at least 32 characters recommended for HS256). "
                                + "Externalize via the SPRING_SECURITY_JWT_SECRET env var before deploying.");
            }
            // Cross-field validation: @RequiredArgsConstructor only works with Lombok on the classpath.
            if (injection.isLombok() && !useLombok) {
                throw new IllegalArgumentException(
                        "injection.style: lombok requires useLombok: true "
                                + "(generated @RequiredArgsConstructor needs the Lombok dependency)");
            }
            // Cross-field validation: Java version vs Spring Boot major.
            int sbMajor = parseSpringBootMajor(springBootVersion);
            if (("8".equals(javaVersion) || "11".equals(javaVersion)) && sbMajor != 2) {
                throw new IllegalArgumentException(
                        "javaVersion: " + javaVersion + " requires Spring Boot 2.x "
                                + "(got springBootVersion: " + springBootVersion + "). "
                                + "Spring Boot 3.x has a Java 17 baseline.");
            }
            if (("17".equals(javaVersion) || "21".equals(javaVersion)) && sbMajor < 2) {
                throw new IllegalArgumentException(
                        "Spring Boot < 2.x is not supported (got: " + springBootVersion + ")");
            }
            // Phase L: Spring Boot 2.x has hard limitations (Java 8 baseline, no
            // ProblemDetail, no records). Reject incompatible options up front.
            if (sbMajor == 2) {
                if (dto.isRecord()) {
                    throw new IllegalArgumentException(
                            "dto.style: record requires Java 14+; Spring Boot 2.x typically targets "
                                    + "Java 8/11. Use dto.style: class instead.");
                }
                if (exception.isProblemDetail()) {
                    throw new IllegalArgumentException(
                            "exception.style: problemdetail requires Spring Boot 3.x. "
                                    + "Use exception.style: envelope on Spring Boot 2.x.");
                }
            }
            // Cursor pagination is currently MVC + JPA only.
            if (pagination.isCursor()) {
                if (!"mvc".equals(architecture)) {
                    throw new IllegalArgumentException(
                            "pagination.style: cursor is currently supported only with architecture: mvc "
                                    + "(got: " + architecture + ")");
                }
                if (!"jpa".equals(persistence)) {
                    throw new IllegalArgumentException(
                            "pagination.style: cursor is currently supported only with persistence: jpa "
                                    + "(got: " + persistence + ")");
                }
            }
            // outputDir defaults depend on mode: standalone -> ./generated, overlay -> "."
            if (outputDir == null || outputDir.isEmpty()) {
                outputDir = output.isOverlay() ? "." : "./generated";
            }
        }

        public Generation(
                String architecture,
                String persistence,
                String basePackage,
                String projectName,
                String projectGroup,
                String springBootVersion,
                String javaVersion,
                boolean useLombok,
                OpenApiOptions openapi,
                InjectionOptions injection,
                ValidationOptions validation,
                DtoOptions dto,
                ExceptionOptions exception,
                AuditOptions audit,
                SoftDeleteOptions softDelete,
                DockerOptions docker,
                CiOptions ci,
                LoggingOptions logging,
                TestOptions tests,
                MigrationOptions migrations,
                PaginationOptions pagination,
                SecurityOptions security,
                String outputDir,
                JpaOptions jpa,
                MyBatisOptions mybatis,
                TableFilterOptions tables,
                DddOptions ddd,
                OutputOptions output,
                ApplicationConfigOptions applicationConfig,
                String schemaFile,
                String schemaDialect,
                String buildTool) {
            this(architecture, persistence, basePackage, projectName, projectGroup,
                    springBootVersion, javaVersion, useLombok, null, openapi, injection,
                    validation, dto, exception, audit, softDelete, docker, ci,
                    logging, tests, migrations, pagination, security, outputDir, jpa,
                    mybatis, tables, ddd, output, applicationConfig, schemaFile,
                    schemaDialect, buildTool);
        }

        public Generation(
                String architecture,
                String persistence,
                String basePackage,
                String projectName,
                String projectGroup,
                String springBootVersion,
                String javaVersion,
                boolean useLombok,
                OpenApiOptions openapi,
                InjectionOptions injection,
                ValidationOptions validation,
                DtoOptions dto,
                ExceptionOptions exception,
                AuditOptions audit,
                SoftDeleteOptions softDelete,
                DockerOptions docker,
                CiOptions ci,
                LoggingOptions logging,
                TestOptions tests,
                PaginationOptions pagination,
                SecurityOptions security,
                String outputDir,
                JpaOptions jpa,
                MyBatisOptions mybatis,
                TableFilterOptions tables,
                DddOptions ddd,
                OutputOptions output,
                ApplicationConfigOptions applicationConfig,
                String schemaFile,
                String buildTool) {
            this(architecture, persistence, basePackage, projectName, projectGroup,
                    springBootVersion, javaVersion, useLombok, null, openapi, injection,
                    validation, dto, exception, audit, softDelete, docker, ci,
                    logging, tests, null, pagination, security, outputDir, jpa, mybatis,
                    tables, ddd, output, applicationConfig, schemaFile, null, buildTool);
        }

        /** Parses a {@code springBootVersion} string into its major integer (2 or 3). */
        public int springBootMajor() {
            return parseSpringBootMajor(springBootVersion);
        }

        public boolean isSpringBoot2() { return springBootMajor() == 2; }
        public boolean isSpringBoot3() { return springBootMajor() == 3; }

        public boolean requiresLombokVersion() {
            return useLombok && requiresExplicitLombokVersion(springBootVersion);
        }

        private static int parseSpringBootMajor(String version) {
            if (version == null || version.isEmpty()) return 3;
            int dot = version.indexOf('.');
            String head = dot < 0 ? version : version.substring(0, dot);
            try {
                return Integer.parseInt(head);
            } catch (NumberFormatException ex) {
                return 3;
            }
        }

        private static int parseSpringBootMinor(String version) {
            if (version == null || version.isEmpty()) return 0;
            String[] parts = version.split("[.\\-]");
            if (parts.length < 2) return 0;
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private static boolean requiresExplicitLombokVersion(String springBootVersion) {
            int major = parseSpringBootMajor(springBootVersion);
            int minor = parseSpringBootMinor(springBootVersion);
            return major < 3 || (major == 3 && minor < 5);
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

        private static String canonicalSchemaDialect(String schemaDialect) {
            String dialect = (schemaDialect == null || schemaDialect.isBlank())
                    ? "postgresql"
                    : schemaDialect.toLowerCase();
            if ("postgres".equals(dialect)) {
                dialect = "postgresql";
            }
            if (!"postgresql".equals(dialect)
                    && !"mysql".equals(dialect)
                    && !"mariadb".equals(dialect)
                    && !"sqlserver".equals(dialect)
                    && !"sqlite".equals(dialect)) {
                throw new IllegalArgumentException(
                        "schemaDialect must be one of postgresql, mysql, mariadb, sqlserver, or sqlite "
                                + "(got: " + schemaDialect + ")");
            }
            return dialect;
        }

        /** Convenience: legacy callers that only care if any OpenAPI is emitted. */
        public boolean generateOpenApi() {
            return openapi != null && openapi.isEnabled();
        }
    }

    public record JpaOptions(boolean useMapStruct) {}

    public record MyBatisOptions(String style) {
        public MyBatisOptions {
            style = style == null ? "xml" : style.toLowerCase();
            if (!"xml".equals(style) && !"annotation".equals(style)) {
                throw new IllegalArgumentException(
                        "mybatis.style must be 'xml' or 'annotation' (got: " + style + ")");
            }
        }
        public boolean isXml() { return "xml".equals(style); }
        public boolean isAnnotation() { return "annotation".equals(style); }
    }

    /**
     * Inclusion / exclusion lists, applied <em>after</em> introspection so
     * filtered tables and relationships pointing to them are dropped.
     */
    public record TableFilterOptions(
            java.util.List<String> include,
            java.util.List<String> exclude,
            String classNameStripPrefix,
            java.util.Map<String, TableOverride> overrides) {
        public TableFilterOptions {
            include = include == null ? java.util.List.of() : java.util.List.copyOf(include);
            exclude = exclude == null ? java.util.List.of() : java.util.List.copyOf(exclude);
            classNameStripPrefix = classNameStripPrefix == null ? "" : classNameStripPrefix;
            overrides = overrides == null ? java.util.Map.of() : java.util.Map.copyOf(overrides);
        }

        /** Convenience for callers that don't care about overrides (3-arg). */
        public TableFilterOptions(java.util.List<String> include, java.util.List<String> exclude, String classNameStripPrefix) {
            this(include, exclude, classNameStripPrefix, java.util.Map.of());
        }

        /** Convenience for callers that don't care about prefix or overrides (2-arg). */
        public TableFilterOptions(java.util.List<String> include, java.util.List<String> exclude) {
            this(include, exclude, "", java.util.Map.of());
        }

        public static TableFilterOptions allowAll() {
            return new TableFilterOptions(java.util.List.of(), java.util.List.of(), "", java.util.Map.of());
        }

        /** Returns the override for {@code tableName}, or empty if none configured. */
        public java.util.Optional<TableOverride> overrideFor(String tableName) {
            return java.util.Optional.ofNullable(overrides.get(tableName));
        }
    }

    /**
     * Per-table customization: an explicit class-name override (taking precedence
     * over the default singularize+PascalCase derivation, and over the global
     * {@code classNameStripPrefix}) and a per-column Java-type override map.
     *
     * <p>Both fields are optional. An entry with no className and no column
     * overrides should be considered empty and is omitted from the saved YAML
     * to keep the file clean.</p>
     */
    public record TableOverride(String className, java.util.Map<String, ColumnOverride> columns) {
        public TableOverride {
            className = className == null ? "" : className;
            columns = columns == null ? java.util.Map.of() : java.util.Map.copyOf(columns);
        }

        public static TableOverride empty() {
            return new TableOverride("", java.util.Map.of());
        }

        /** True if the override has no effect — used by YAML I/O to skip writing it. */
        public boolean isEmpty() {
            return (className == null || className.isEmpty()) && (columns == null || columns.isEmpty());
        }
    }

    /**
     * Per-column override. Currently a single field — {@code javaType} — but
     * defined as a record so future fields (e.g. column comment, default value
     * literal, validation hints) can be added without breaking call sites.
     *
     * <p>{@code javaType} should be a Java type string the user picked from the
     * panel's curated dropdown — primitives (e.g. {@code int}), java.lang.*
     * short names (e.g. {@code String}), or fully-qualified names with optional
     * generics (e.g. {@code java.math.BigDecimal},
     * {@code java.util.Map<String,Object>}).</p>
     */
    public record ColumnOverride(String javaType) {
        public ColumnOverride {
            javaType = javaType == null ? "" : javaType;
        }

        public boolean isEmpty() {
            return javaType == null || javaType.isEmpty();
        }
    }

    /**
     * DDD-specific options. By default every non-junction table is treated
     * as an aggregate root.
     */
    public record DddOptions(
            java.util.List<String> aggregateRoots,
            java.util.List<String> nonRoots,
            java.util.Map<String, String> belongsTo,
            String sharedKernelPackage) {
        public DddOptions {
            aggregateRoots = aggregateRoots == null ? java.util.List.of() : java.util.List.copyOf(aggregateRoots);
            nonRoots = nonRoots == null ? java.util.List.of() : java.util.List.copyOf(nonRoots);
            belongsTo = belongsTo == null ? java.util.Map.of() : java.util.Map.copyOf(belongsTo);
            sharedKernelPackage = sharedKernelPackage == null ? "shared" : sharedKernelPackage;
        }

        public static DddOptions defaults() {
            return new DddOptions(java.util.List.of(), java.util.List.of(), java.util.Map.of(), "shared");
        }

        public boolean isAggregateRoot(String tableName) {
            String lower = tableName.toLowerCase(java.util.Locale.ROOT);
            if (nonRoots.stream().anyMatch(n -> n.equalsIgnoreCase(lower))) return false;
            if (aggregateRoots.isEmpty()) return true;
            return aggregateRoots.stream().anyMatch(a -> a.equalsIgnoreCase(lower));
        }

        public java.util.Optional<String> parentOf(String tableName) {
            for (var entry : belongsTo.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(tableName)) {
                    return java.util.Optional.of(entry.getValue());
                }
            }
            return java.util.Optional.empty();
        }
    }

    /**
     * Output-mode options.
     *
     * <ul>
     *   <li>{@code standalone} (default) — emits a complete runnable project including
     *       {@code pom.xml}, {@code Application.java}, {@code application.yml}, and
     *       {@code GlobalExceptionHandler.java}. Suitable for greenfield use.
     *       Default {@code outputDir} is {@code ./generated}. {@code existingPolicy}
     *       controls what happens when the target already looks like a project.</li>
     *   <li>{@code overlay} — emits only per-table source files into an existing Spring Boot
     *       project. Skips {@code pom.xml}, {@code Application.java}, {@code application.yml},
     *       and {@code GlobalExceptionHandler.java} so they don't clobber the user's own.
     *       Default {@code outputDir} is {@code .} (the project root).</li>
     * </ul>
     */
    public record OutputOptions(String mode, String existingPolicy) {
        public OutputOptions(String mode) {
            this(mode, "warn");
        }

        public OutputOptions {
            mode = mode == null ? "standalone" : mode.toLowerCase();
            if (!"standalone".equals(mode) && !"overlay".equals(mode)) {
                throw new IllegalArgumentException(
                        "output.mode must be 'standalone' or 'overlay' (got: " + mode + ")");
            }
            existingPolicy = existingPolicy == null ? "warn" : existingPolicy.toLowerCase();
            if (!"warn".equals(existingPolicy)
                    && !"overwrite".equals(existingPolicy)
                    && !"clean".equals(existingPolicy)
                    && !"fail".equals(existingPolicy)) {
                throw new IllegalArgumentException(
                        "output.existingPolicy must be 'warn', 'overwrite', 'clean', or 'fail' (got: "
                                + existingPolicy + ")");
            }
        }

        public static OutputOptions defaults() {
            return new OutputOptions("standalone", "warn");
        }

        public boolean isStandalone() { return "standalone".equals(mode); }
        public boolean isOverlay() { return "overlay".equals(mode); }
        public boolean isExistingPolicyWarn() { return "warn".equals(existingPolicy); }
        public boolean isExistingPolicyOverwrite() { return "overwrite".equals(existingPolicy); }
        public boolean isExistingPolicyClean() { return "clean".equals(existingPolicy); }
        public boolean isExistingPolicyFail() { return "fail".equals(existingPolicy); }
    }

    /**
     * OpenAPI emission style.
     *
     * <ul>
     *   <li>{@code yaml} (default) — emits a static {@code src/main/resources/openapi.yaml}
     *       generated by Umaboot's introspection of the schema. No runtime dependencies.</li>
     *   <li>{@code annotation} — does NOT emit the static yaml. Instead emits an
     *       {@code OpenApiConfig} class with springdoc annotations and adds
     *       {@code @Tag}/{@code @Operation} annotations to the generated controllers.
     *       Adds the Spring Boot-compatible springdoc WebMVC UI dependency to
     *       the project build file (standalone mode).
     *       At runtime, springdoc serves the spec at {@code /v3/api-docs} and Swagger UI at
     *       {@code /swagger-ui.html}.</li>
     *   <li>{@code none} — neither file nor annotations. Equivalent to the legacy
     *       {@code generateOpenApi: false}.</li>
     * </ul>
     */
    public record OpenApiOptions(String style) {
        public OpenApiOptions {
            style = style == null ? "yaml" : style.toLowerCase();
            if (!"yaml".equals(style) && !"annotation".equals(style) && !"none".equals(style)) {
                throw new IllegalArgumentException(
                        "openapi.style must be 'yaml', 'annotation', or 'none' (got: " + style + ")");
            }
        }

        public static OpenApiOptions defaults() {
            return new OpenApiOptions("yaml");
        }

        public boolean isYaml() { return "yaml".equals(style); }
        public boolean isAnnotation() { return "annotation".equals(style); }
        public boolean isEnabled() { return !"none".equals(style); }
    }

    /**
     * Format of the generated application configuration file.
     *
     * <ul>
     *   <li>{@code yaml} (default) — emits {@code src/main/resources/application.yml}.
     *       Indented mapping syntax. The Spring Boot ecosystem default.</li>
     *   <li>{@code properties} — emits {@code src/main/resources/application.properties}
     *       in dotted-key form. Same content, different format. Some teams prefer it
     *       for grep-ability or because their tooling expects it.</li>
     * </ul>
     *
     * <p>Both formats are read identically by Spring Boot; the choice is purely about
     * what file the generated project ships with. Overlay mode is unaffected — the
     * existing {@link ApplicationConfigMerger} already handles either format when
     * appending Umaboot-required entries.</p>
     */
    public record ApplicationConfigOptions(String format) {
        public ApplicationConfigOptions {
            format = format == null ? "yaml" : format.toLowerCase();
            if (!"yaml".equals(format) && !"properties".equals(format)) {
                throw new IllegalArgumentException(
                        "applicationConfig.format must be 'yaml' or 'properties' (got: " + format + ")");
            }
        }

        public static ApplicationConfigOptions defaults() {
            return new ApplicationConfigOptions("yaml");
        }

        public boolean isYaml() { return "yaml".equals(format); }
        public boolean isProperties() { return "properties".equals(format); }
    }

    /**
     * Bean injection style for generated services and controllers.
     *
     * <ul>
     *   <li>{@code constructor} (default) — explicit constructor with {@code private final}
     *       fields. No annotations. The modern Spring best practice; works without Lombok.</li>
     *   <li>{@code lombok} — class is annotated {@code @RequiredArgsConstructor}; fields stay
     *       {@code private final}; no explicit constructor. Requires {@code useLombok: true}
     *       (validated at config-load time).</li>
     *   <li>{@code autowired} — fields are annotated {@code @Autowired} (field injection).
     *       Fields are NOT {@code final}. No constructor. Legacy style; included for teams
     *       that have an existing codebase using it.</li>
     * </ul>
     */
    public record InjectionOptions(String style) {
        public InjectionOptions {
            style = style == null ? "constructor" : style.toLowerCase();
            if (!"constructor".equals(style) && !"lombok".equals(style) && !"autowired".equals(style)) {
                throw new IllegalArgumentException(
                        "injection.style must be 'constructor', 'lombok', or 'autowired' (got: " + style + ")");
            }
        }

        public static InjectionOptions defaults() {
            return new InjectionOptions("constructor");
        }

        public boolean isConstructor() { return "constructor".equals(style); }
        public boolean isLombok() { return "lombok".equals(style); }
        public boolean isAutowired() { return "autowired".equals(style); }
    }

    /**
     * Validation strategy for request DTOs.
     *
     * <ul>
     *   <li>{@code jakarta} (default) — Jakarta Bean Validation annotations
     *       ({@code @NotNull}, {@code @NotBlank}, {@code @Size}) on Request DTO fields,
     *       plus {@code @Valid} on controller method parameters so Spring auto-validates.</li>
     *   <li>{@code none} — no annotations, no {@code @Valid}. Trust your callers.</li>
     *   <li>{@code service} — DTOs stay annotation-free; controllers don't use {@code @Valid};
     *       instead the generated {@code Service} layer is expected to perform domain validation.
     *       (Generator emits a TODO comment in service stubs reminding you to add the checks.)</li>
     * </ul>
     */
    public record ValidationOptions(String style) {
        public ValidationOptions {
            style = style == null ? "jakarta" : style.toLowerCase();
            if (!"jakarta".equals(style) && !"none".equals(style) && !"service".equals(style)) {
                throw new IllegalArgumentException(
                        "validation.style must be 'jakarta', 'none', or 'service' (got: " + style + ")");
            }
        }

        public static ValidationOptions defaults() {
            return new ValidationOptions("jakarta");
        }

        public boolean isJakarta() { return "jakarta".equals(style); }
        public boolean isNone() { return "none".equals(style); }
        public boolean isService() { return "service".equals(style); }
    }

    /**
     * DTO emission style.
     *
     * <ul>
     *   <li>{@code style: class} (default) — Java classes with getters/setters (or Lombok
     *       {@code @Data} when enabled).</li>
     *   <li>{@code style: record} — Java records (immutable, automatic accessors,
     *       {@code equals}/{@code hashCode}/{@code toString}). Requires Java 17+.</li>
     *   <li>{@code shape: separate} (default) — distinct {@code RequestDTO} and
     *       {@code ResponseDTO} per entity. Cleanest for non-trivial APIs.</li>
     *   <li>{@code shape: single} — one {@code Dto} per entity used for both directions.
     *       Quick and pragmatic; fine for simple CRUD apps.</li>
     * </ul>
     */
    public record DtoOptions(String style, String shape) {
        public DtoOptions {
            style = style == null ? "class" : style.toLowerCase();
            shape = shape == null ? "separate" : shape.toLowerCase();
            if (!"class".equals(style) && !"record".equals(style)) {
                throw new IllegalArgumentException(
                        "dto.style must be 'class' or 'record' (got: " + style + ")");
            }
            if (!"separate".equals(shape) && !"single".equals(shape)) {
                throw new IllegalArgumentException(
                        "dto.shape must be 'separate' or 'single' (got: " + shape + ")");
            }
        }

        public static DtoOptions defaults() {
            return new DtoOptions("class", "separate");
        }

        public boolean isClass() { return "class".equals(style); }
        public boolean isRecord() { return "record".equals(style); }
        public boolean isSeparate() { return "separate".equals(shape); }
        public boolean isSingle() { return "single".equals(shape); }
    }

    /**
     * Exception-handler response shape.
     *
     * <ul>
     *   <li>{@code problemdetail} (default) — Spring 6's {@link org.springframework.http.ProblemDetail}
     *       (RFC 7807). The exception handler returns {@code ProblemDetail} directly.
     *       Standard, simple, no extra classes generated.</li>
     *   <li>{@code envelope} — emits a small {@code ApiError} wrapper class with a stable
     *       JSON shape {@code &#123; code, message, details, timestamp, path &#125;} and the
     *       handler returns {@code ResponseEntity<ApiError>}. Useful when frontends expect
     *       a consistent envelope across success and error responses.</li>
     * </ul>
     */
    public record ExceptionOptions(String style) {
        public ExceptionOptions {
            style = style == null ? "problemdetail" : style.toLowerCase();
            if (!"problemdetail".equals(style) && !"envelope".equals(style)) {
                throw new IllegalArgumentException(
                        "exception.style must be 'problemdetail' or 'envelope' (got: " + style + ")");
            }
        }

        public static ExceptionOptions defaults() {
            return new ExceptionOptions("problemdetail");
        }

        public boolean isProblemDetail() { return "problemdetail".equals(style); }
        public boolean isEnvelope() { return "envelope".equals(style); }
    }

    /**
     * Audit-field auto-detection. When {@code enabled: true} (default), the generator
     * inspects each table for columns matching the configured names. JPA projects
     * use a generated {@code Auditable} {@code @MappedSuperclass} with
     * {@code @EnableJpaAuditing}; MyBatis/jOOQ projects use generated application
     * service code to fill audit values before persistence.
     *
     * <p>Detection is case-insensitive. Audit columns are read-only API input:
     * generated request DTOs omit them while response DTOs include them.</p>
     */
    public record AuditOptions(
            boolean enabled,
            String createdAt,
            String updatedAt,
            String createdBy,
            String updatedBy) {

        public AuditOptions {
            createdAt = createdAt == null ? "created_at" : createdAt;
            updatedAt = updatedAt == null ? "updated_at" : updatedAt;
            createdBy = createdBy == null ? "created_by" : createdBy;
            updatedBy = updatedBy == null ? "updated_by" : updatedBy;
        }

        public static AuditOptions defaults() {
            return new AuditOptions(true, "created_at", "updated_at", "created_by", "updated_by");
        }
    }

    /**
     * Soft-delete auto-detection. When {@code enabled: true} (default), the generator
     * looks for a {@code deleted_at} timestamp or {@code is_deleted} boolean column
     * (case-insensitive). When found on a JPA entity, the entity is annotated
     * {@code @SQLDelete} (so {@code .delete()} performs an UPDATE) and {@code @Where}
     * (so reads automatically filter out soft-deleted rows).
     *
     * <p>Currently JPA-only; for MyBatis/jOOQ the column stays on the entity but
     * generated mapper queries do not yet honor it.</p>
     */
    public record SoftDeleteOptions(boolean enabled, String column) {
        public SoftDeleteOptions {
            // null means "auto-detect any of: deleted_at, is_deleted, deleted, deletedAt, isDeleted"
        }

        public static SoftDeleteOptions defaults() {
            return new SoftDeleteOptions(true, null);
        }
    }

    /**
     * Docker scaffolding for the generated project.
     *
     * <ul>
     *   <li>{@code enabled: false} (default) — no Docker files emitted.</li>
     *   <li>{@code enabled: true} — emits a multi-stage {@code Dockerfile} and a
     *       {@code docker-compose.yml} that runs the generated app alongside a
     *       database matching the introspected JDBC URL (postgres or mysql).</li>
     * </ul>
     */
    public record DockerOptions(boolean enabled, String baseImage, int port) {
        public DockerOptions {
            baseImage = baseImage == null ? "eclipse-temurin:17-jre-alpine" : baseImage;
            if (port <= 0) port = 8080;
        }

        public static DockerOptions defaults() {
            return new DockerOptions(false, "eclipse-temurin:17-jre-alpine", 8080);
        }
    }

    /**
     * CI pipeline scaffolding.
     *
     * <ul>
     *   <li>{@code style: none} (default) — no CI files.</li>
     *   <li>{@code style: github} — emits {@code .github/workflows/ci.yml} with
     *       a Maven build + test job on push and pull request.</li>
     *   <li>{@code style: gitlab} — emits {@code .gitlab-ci.yml} with the
     *       equivalent stages.</li>
     * </ul>
     */
    public record CiOptions(String style) {
        public CiOptions {
            style = style == null ? "none" : style.toLowerCase();
            if (!"none".equals(style) && !"github".equals(style) && !"gitlab".equals(style)) {
                throw new IllegalArgumentException(
                        "ci.style must be 'none', 'github', or 'gitlab' (got: " + style + ")");
            }
        }

        public static CiOptions defaults() { return new CiOptions("none"); }
        public boolean isNone() { return "none".equals(style); }
        public boolean isGithub() { return "github".equals(style); }
        public boolean isGitlab() { return "gitlab".equals(style); }
    }

    /**
     * Application logging style for the generated project.
     *
     * <ul>
     *   <li>{@code style: plain} (default) — Spring Boot's default logback config
     *       (text logs to console). No extra files emitted.</li>
     *   <li>{@code style: json} — emits {@code logback-spring.xml} that uses
     *       {@code logstash-logback-encoder} to write JSON-formatted log lines,
     *       and adds the encoder dependency to the generated pom.</li>
     *   <li>{@code correlationId: true} — emits a Spring servlet filter that
     *       puts an {@code X-Correlation-Id} header into the SLF4J MDC for the
     *       lifetime of each request. Works with either logging style.</li>
     * </ul>
     */
    public record LoggingOptions(String style, boolean correlationId) {
        public LoggingOptions {
            style = style == null ? "plain" : style.toLowerCase();
            if (!"plain".equals(style) && !"json".equals(style)) {
                throw new IllegalArgumentException(
                        "logging.style must be 'plain' or 'json' (got: " + style + ")");
            }
        }

        public static LoggingOptions defaults() { return new LoggingOptions("plain", false); }
        public boolean isPlain() { return "plain".equals(style); }
        public boolean isJson() { return "json".equals(style); }
    }

    /**
     * Integration-test scaffolding for the generated project.
     *
     * <ul>
     *   <li>{@code enabled: false} (default) — no test classes emitted.</li>
     *   <li>{@code enabled: true} — emits a shared {@code AbstractIntegrationTest}
     *       base class that spins up a Testcontainer matching the configured driver
     *       (postgres or mysql), plus one {@code &lt;Entity&gt;IntegrationTest} per
     *       non-junction table containing smoke-test cases:
     *       {@code contextLoads}, {@code list_returnsOk}, and
     *       {@code getById_unknown_returns404}. Adds {@code testcontainers},
     *       {@code testcontainers-junit-jupiter}, and the matching jdbc-driver
     *       Testcontainers module to the generated pom in test scope.</li>
     * </ul>
     */
    public record TestOptions(boolean enabled) {
        public static TestOptions defaults() { return new TestOptions(false); }
    }

    /**
     * Database migration scaffolding for the generated project.
     *
     * <ul>
     *   <li>{@code style: none} (default) — no migration dependency or files.</li>
     *   <li>{@code style: flyway} — adds Flyway dependencies and emits
     *       {@code src/main/resources/db/migration/V1__init_schema.sql}. In
     *       schema-file mode the migration is the user's SQL file; in live-DB mode
     *       Umaboot renders a best-effort initial DDL from the introspected model.</li>
     * </ul>
     */
    public record MigrationOptions(String style) {
        public MigrationOptions {
            style = style == null ? "none" : style.toLowerCase();
            if (!"none".equals(style) && !"flyway".equals(style)) {
                throw new IllegalArgumentException(
                        "migrations.style must be 'none' or 'flyway' (got: " + style + ")");
            }
        }

        public static MigrationOptions defaults() { return new MigrationOptions("none"); }
        public boolean isNone() { return "none".equals(style); }
        public boolean isFlyway() { return "flyway".equals(style); }
    }

    /**
     * Pagination style for list endpoints.
     *
     * <ul>
     *   <li>{@code offset} (default) — Spring's standard offset/limit pagination via
     *       {@code Pageable} + {@code Page<T>}, returned wrapped in {@code PageResponse<T>}.
     *       Works with every architecture and persistence backend.</li>
     *   <li>{@code cursor} — keyset/cursor pagination via opaque base64 cursors over the
     *       entity's simple primary key. Returns a {@code CursorPage<T>} with
     *       {@code nextCursor}/{@code hasMore} for forward iteration.
     *       <br><b>Currently supported on MVC + JPA only.</b> Configurations with other
     *       architectures or persistence backends fail fast at config-load. Tables with
     *       composite primary keys fall back to offset for that table specifically.</li>
     * </ul>
     */
    public record PaginationOptions(String style) {
        public PaginationOptions {
            style = style == null ? "offset" : style.toLowerCase();
            if (!"offset".equals(style) && !"cursor".equals(style)) {
                throw new IllegalArgumentException(
                        "pagination.style must be 'offset' or 'cursor' (got: " + style + ")");
            }
        }

        public static PaginationOptions defaults() { return new PaginationOptions("offset"); }
        public boolean isOffset() { return "offset".equals(style); }
        public boolean isCursor() { return "cursor".equals(style); }
    }

    /**
     * Spring Security scaffolding for the generated project.
     *
     * <ul>
     *   <li>{@code style: none} (default) — no Spring Security on the classpath.
     *       All endpoints publicly accessible. Existing v1.x behavior.</li>
     *   <li>{@code style: basic} — HTTP Basic authentication backed by an
     *       in-memory {@link org.springframework.security.core.userdetails.UserDetailsService}.
     *       All {@code /api/**} routes require authentication. Useful for dev /
     *       internal admin tools.</li>
     *   <li>{@code style: jwt} — JWT bearer authentication (HS256). Generates an
     *       {@code AuthController} exposing {@code POST /api/auth/login} that
     *       returns a token, plus a Spring filter that validates the
     *       {@code Authorization: Bearer ...} header on subsequent requests.
     *       In-memory users issue the tokens; swap in a database-backed
     *       {@code UserDetailsService} when ready.</li>
     * </ul>
     *
     * <p>The {@code users} list seeds the in-memory authentication manager.
     * Passwords are encoded with BCrypt at startup. Roles are stored as plain
     * strings (e.g. {@code USER}, {@code ADMIN}) — the generator prefixes
     * them with {@code ROLE_} as Spring expects.</p>
     *
     * <p>The {@code jwt.secret} field is required for {@code style: jwt} but
     * the generated config emits a TODO comment urging you to externalize it
     * via {@code SPRING_SECURITY_JWT_SECRET} env var or a vault before any
     * deployment.</p>
     */
    public record SecurityOptions(String style, java.util.List<UserCredentials> users, JwtOptions jwt) {

        public SecurityOptions {
            style = style == null ? "none" : style.toLowerCase();
            if (!"none".equals(style) && !"basic".equals(style) && !"jwt".equals(style)) {
                throw new IllegalArgumentException(
                        "security.style must be 'none', 'basic', or 'jwt' (got: " + style + ")");
            }
            users = users == null ? java.util.List.of() : java.util.List.copyOf(users);
            jwt = jwt == null ? JwtOptions.defaults() : jwt;
        }

        public static SecurityOptions defaults() {
            return new SecurityOptions("none", java.util.List.of(), JwtOptions.defaults());
        }

        public boolean isNone() { return "none".equals(style); }
        public boolean isBasic() { return "basic".equals(style); }
        public boolean isJwt() { return "jwt".equals(style); }
        public boolean isEnabled() { return !"none".equals(style); }
    }

    /**
     * Single in-memory user credential. {@code roles} are role names without
     * the {@code ROLE_} prefix (Spring will prepend it).
     */
    public record UserCredentials(String username, String password, java.util.List<String> roles) {
        public UserCredentials {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            roles = (roles == null || roles.isEmpty()) ? java.util.List.of("USER") : java.util.List.copyOf(roles);
        }
    }

    /** JWT-specific knobs. {@code secret} is required only when {@code security.style: jwt}. */
    public record JwtOptions(String secret, int expirationMinutes, String header, String prefix) {
        public JwtOptions {
            // Defaults for missing fields; secret intentionally remains nullable for none/basic modes.
            if (expirationMinutes <= 0) expirationMinutes = 60;
            header = (header == null || header.isEmpty()) ? "Authorization" : header;
            prefix = (prefix == null || prefix.isEmpty()) ? "Bearer " : prefix;
        }

        public static JwtOptions defaults() {
            return new JwtOptions(null, 60, "Authorization", "Bearer ");
        }
    }
}
