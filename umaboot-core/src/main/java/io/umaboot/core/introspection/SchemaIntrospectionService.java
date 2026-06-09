package io.umaboot.core.introspection;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.introspection.mysql.MysqlIntrospector;
import io.umaboot.core.introspection.postgres.PostgresIntrospector;
import io.umaboot.core.introspection.sqlfile.SqlFileIntrospector;
import io.umaboot.core.introspection.sqlite.SqliteIntrospector;
import io.umaboot.core.introspection.sqlserver.SqlServerIntrospector;
import io.umaboot.core.model.SchemaModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Loads the raw schema model from the configured schema source.
 *
 * <p>This deliberately stops before relationship analysis and table filtering.
 * Callers that need "what would generate" semantics can run those later, while
 * UI callers such as list-tables can choose whether junction tables are shown.</p>
 */
public final class SchemaIntrospectionService {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    public Result introspect(UmabootConfig config) throws SQLException {
        Objects.requireNonNull(config, "config");

        if (config.isSchemaFileMode()) {
            return introspectSchemaFile(config);
        }
        return introspectLiveDatabase(config);
    }

    private Result introspectSchemaFile(UmabootConfig config) throws SQLException {
        String relPath = config.generation().schemaFile();
        Path sqlPath = Paths.get(relPath);
        if (!Files.isReadable(sqlPath)) {
            throw new IllegalArgumentException(
                    "schemaFile not readable: " + sqlPath.toAbsolutePath()
                            + ". Provide a path to a .sql DDL file (CREATE TABLE / ALTER TABLE FK / "
                            + "CREATE TYPE ENUM / COMMENT statements).");
        }

        String sql;
        try {
            sql = Files.readString(sqlPath);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Failed to read schemaFile " + sqlPath.toAbsolutePath() + ": " + ex.getMessage(), ex);
        }

        String dbType = config.generation().schemaDialect();
        SqlFileIntrospector introspector = new SqlFileIntrospector(sql, dbType);
        SchemaModel schema = introspector.introspect(defaultSchemaName(dbType));
        LOG.info("Parsed {} tables from {}", schema.tables().size(), sqlPath);
        return new Result(schema, dbType, null, sql, introspector.warnings());
    }

    private Result introspectLiveDatabase(UmabootConfig config) throws SQLException {
        UmabootConfig.Connection connection = config.connection();
        String target = connection.introspectionTarget();
        boolean targetRequired = !"sqlite".equalsIgnoreCase(connection.driver());
        if (targetRequired && (target == null || target.isBlank())) {
            String missing = ("mysql".equals(connection.type()) || "mariadb".equals(connection.type()))
                    ? "database" : "schema";
            throw new IllegalArgumentException(
                    "Cannot generate: connection." + missing + " is empty. "
                            + "Fill it in before running generate.");
        }

        JdbcDrivers.registerAll();
        try (Connection conn = DriverManager.getConnection(
                connection.url(),
                connection.username(),
                connection.password())) {
            Introspector introspector = introspectorFor(connection.driver(), conn);
            SchemaModel schema = introspector.introspect(target);
            LOG.info("Introspected {} tables from live database", schema.tables().size());
            List<String> warnings = schema.tables().isEmpty()
                    ? emptyLiveSchemaWarnings(connection, target)
                    : List.of();
            return new Result(schema, connection.driver(), connection, null, warnings);
        }
    }

    static List<String> emptyLiveSchemaWarnings(UmabootConfig.Connection connection, String target) {
        if (connection == null) return List.of();
        String type = connection.type();
        String effectiveTarget = target == null || target.isBlank()
                ? connection.introspectionTarget()
                : target;
        if ("sqlite".equalsIgnoreCase(type)) {
            return List.of();
        }
        if ("mysql".equalsIgnoreCase(type) || "mariadb".equalsIgnoreCase(type)) {
            return List.of("No tables found in database '" + effectiveTarget
                    + "'. For MySQL/MariaDB the Database field is the table-introspection target; verify connection.database and that the configured user can see tables in that database.");
        }
        if ("sqlserver".equalsIgnoreCase(type)) {
            return List.of("No tables found in schema '" + effectiveTarget
                    + "'. For SQL Server the Schema field is the table-introspection target, usually 'dbo'; verify connection.schema and that the configured user can see tables in that schema.");
        }
        return List.of("No tables found in schema '" + effectiveTarget
                + "'. For PostgreSQL the Database field selects the database in the JDBC URL, but the Schema field selects where tables are read from; verify connection.schema and that the configured user can see tables in that schema.");
    }

    public static Introspector introspectorFor(String driver, Connection conn) {
        if ("sqlserver".equalsIgnoreCase(driver)) {
            return new SqlServerIntrospector(conn);
        }
        if ("sqlite".equalsIgnoreCase(driver)) {
            return new SqliteIntrospector(conn);
        }
        if ("mysql".equalsIgnoreCase(driver) || "mariadb".equalsIgnoreCase(driver)) {
            return new MysqlIntrospector(conn);
        }
        return new PostgresIntrospector(conn);
    }

    private static String defaultSchemaName(String dbType) {
        if ("sqlserver".equalsIgnoreCase(dbType)) return "dbo";
        if ("sqlite".equalsIgnoreCase(dbType)) return "main";
        return "public";
    }

    public record Result(
            SchemaModel schema,
            String dbType,
            UmabootConfig.Connection connection,
            String schemaFileSql,
            List<String> warnings) {
        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
