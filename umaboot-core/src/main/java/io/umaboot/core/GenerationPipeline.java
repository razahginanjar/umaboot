package io.umaboot.core;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.filter.TableFilter;
import io.umaboot.core.generator.ArchitectureGenerator;
import io.umaboot.core.generator.ArchitectureGenerators;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.introspection.Introspector;
import io.umaboot.core.introspection.JdbcDrivers;
import io.umaboot.core.introspection.mysql.MysqlIntrospector;
import io.umaboot.core.introspection.postgres.PostgresIntrospector;
import io.umaboot.core.introspection.sqlfile.SqlFileIntrospector;
import io.umaboot.core.introspection.sqlite.SqliteIntrospector;
import io.umaboot.core.introspection.sqlserver.SqlServerIntrospector;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.relationship.RelationshipEngine;
import io.umaboot.core.template.TemplateEngine;
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
 * The public entry point that runs the full generation pipeline.
 *
 * <p>Order: introspect → relationship engine → table filter → architecture
 * generator. The filter runs <em>after</em> the relationship engine so that
 * junction-table detection has already happened (a filter that drops a
 * junction-endpoint table will then drop the junction itself).</p>
 *
 * <p>Used by the CLI ({@code Umaboot generate / diff / apply}) and by the
 * IntelliJ plugin's tool-window action.</p>
 */
public final class GenerationPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationPipeline.class);

    private GenerationPipeline() {}

    /**
     * Run the pipeline against a loaded configuration.
     *
     * @param config        the resolved configuration
     * @param templatesDir  optional directory of FreeMarker template overrides; may be {@code null}
     * @return generated units + the resolved {@link GeneratorContext} used
     * @throws SQLException if introspection fails
     */
    public static Result run(UmabootConfig config, Path templatesDir) throws SQLException {
        Objects.requireNonNull(config, "config");

        SchemaModel schema;
        String dbType;          // canonical: "postgresql" | "mysql" | "mariadb"
        UmabootConfig.Connection connection = config.connection();

        if (config.isSchemaFileMode()) {
            // ----- SQL-FILE MODE: parse a checked-in .sql DDL file with JSqlParser -----
            // No JDBC, no Docker, no live DB needed. Pairs with Flyway-style runtime
            // migration where the same file is the schema authority at startup.
            String relPath = config.generation().schemaFile();
            Path sqlPath = Paths.get(relPath);
            if (!Files.isReadable(sqlPath)) {
                throw new IllegalArgumentException(
                        "schemaFile not readable: " + sqlPath.toAbsolutePath()
                                + ". Provide a path to a .sql DDL file (CREATE TABLE / ALTER TABLE FK / "
                                + "CREATE TYPE ENUM / COMMENT statements).");
            }
            String sqlText;
            try {
                sqlText = Files.readString(sqlPath);
            } catch (IOException ex) {
                throw new IllegalArgumentException(
                        "Failed to read schemaFile " + sqlPath.toAbsolutePath() + ": " + ex.getMessage(), ex);
            }
            // Default dialect to postgresql; the file may declare anything but the dialect hint
            // only affects soft parsing decisions inside the introspector.
            dbType = "postgresql";
            // Default schema name when not connecting to a live DB. The user can override via
            // tables.include/exclude if they want to pre-filter.
            String logicalSchema = "public";
            Introspector introspector = new SqlFileIntrospector(sqlText, dbType);
            schema = introspector.introspect(logicalSchema);
            LOG.info("Parsed {} tables from {}", schema.tables().size(), sqlPath);
        } else {
            // ----- LIVE-DB MODE: introspect via JDBC DatabaseMetaData -----
            // Fail-fast: silently introspecting against an empty target produces 0 tables
            // and a confusing "no entities generated" outcome. Better to bail early with
            // a message that names the missing field.
            String target = connection.introspectionTarget();
            if (target == null || target.isBlank()) {
                String missing = "mysql".equals(connection.type()) ? "database" : "schema";
                throw new IllegalArgumentException(
                        "Cannot generate: connection." + missing + " is empty. "
                                + "Fill it in before running generate.");
            }

            JdbcDrivers.registerAll();
            try (Connection conn = DriverManager.getConnection(
                    connection.url(),
                    connection.username(),
                    connection.password())) {
                String driver = connection.driver();
                Introspector introspector;
                if ("sqlserver".equalsIgnoreCase(driver)) {
                    introspector = new SqlServerIntrospector(conn);
                } else if ("sqlite".equalsIgnoreCase(driver)) {
                    introspector = new SqliteIntrospector(conn);
                } else if ("mysql".equalsIgnoreCase(driver) || "mariadb".equalsIgnoreCase(driver)) {
                    introspector = new MysqlIntrospector(conn);
                } else {
                    introspector = new PostgresIntrospector(conn);
                }
                schema = introspector.introspect(connection.introspectionTarget());
            }
            LOG.info("Introspected {} tables from live database", schema.tables().size());
            dbType = connection.driver();
        }

        schema = new RelationshipEngine().analyze(schema);

        var filter = new TableFilter(
                config.generation().tables().include(),
                config.generation().tables().exclude());
        SchemaModel filtered = filter.apply(schema);
        if (filtered.tables().size() != schema.tables().size()) {
            LOG.info("Table filter: {} -> {} tables", schema.tables().size(), filtered.tables().size());
        }
        schema = filtered;

        GeneratorContext ctx = new GeneratorContext(
                config.generation().basePackage(),
                config.generation().projectName(),
                config.generation().projectGroup(),
                config.generation().springBootVersion(),
                config.generation().javaVersion(),
                config.generation().useLombok(),
                config.generation().architecture(),
                config.generation().persistence(),
                config.generation().mybatis().style(),
                config.generation().jpa().useMapStruct(),
                config.generation().openapi().style(),
                config.generation().injection().style(),
                config.generation().validation().style(),
                config.generation().dto().style(),
                config.generation().dto().shape(),
                config.generation().exception().style(),
                config.generation().audit(),
                config.generation().softDelete(),
                config.generation().docker(),
                config.generation().ci(),
                config.generation().logging(),
                config.generation().tests(),
                config.generation().pagination().style(),
                config.generation().security(),
                config.generation().ddd(),
                config.generation().output().isOverlay(),
                dbType,
                connection,
                config.generation().applicationConfig(),
                config.generation().tables().classNameStripPrefix(),
                config.generation().tables().overrides(),
                config.generation().buildTool());

        TemplateEngine engine = new TemplateEngine(templatesDir);
        ArchitectureGenerator generator = ArchitectureGenerators.forContext(ctx, engine);
        List<GeneratedUnit> units = generator.generate(schema);
        return new Result(units, ctx);
    }

    /** Tuple of generated units and the context they were produced under. */
    public record Result(List<GeneratedUnit> units, GeneratorContext ctx) {}
}
