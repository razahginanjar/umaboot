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
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.relationship.RelationshipEngine;
import io.umaboot.core.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

        // Fail-fast: silently introspecting against an empty target produces 0 tables
        // and a confusing "no entities generated" outcome. Better to bail early with
        // a message that names the missing field.
        String target = config.connection().introspectionTarget();
        if (target == null || target.isBlank()) {
            String missing = "mysql".equals(config.connection().type()) ? "database" : "schema";
            throw new IllegalArgumentException(
                    "Cannot generate: connection." + missing + " is empty. "
                            + "Fill it in before running generate.");
        }

        SchemaModel schema;
        JdbcDrivers.registerAll();
        try (Connection conn = DriverManager.getConnection(
                config.connection().url(),
                config.connection().username(),
                config.connection().password())) {
            Introspector introspector = "mysql".equalsIgnoreCase(config.connection().driver())
                    ? new MysqlIntrospector(conn)
                    : new PostgresIntrospector(conn);
            schema = introspector.introspect(config.connection().introspectionTarget());
        }
        LOG.info("Introspected {} tables", schema.tables().size());

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
                config.connection().driver(),
                config.connection(),
                config.generation().applicationConfig());

        TemplateEngine engine = new TemplateEngine(templatesDir);
        ArchitectureGenerator generator = ArchitectureGenerators.forContext(ctx, engine);
        List<GeneratedUnit> units = generator.generate(schema);
        return new Result(units, ctx);
    }

    /** Tuple of generated units and the context they were produced under. */
    public record Result(List<GeneratedUnit> units, GeneratorContext ctx) {}
}
