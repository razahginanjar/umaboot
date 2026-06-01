package io.umaboot.core.config;

import io.umaboot.core.config.UmabootConfig.Connection;
import io.umaboot.core.config.UmabootConfig.Generation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the schema-source XOR rules introduced for sqlFile mode:
 *
 * <ul>
 *   <li>exactly one of {@code connection:} / {@code schemaFile:} must be set</li>
 *   <li>{@code persistence: jooq} requires a live connection (rejects schemaFile mode)</li>
 *   <li>{@code isSchemaFileMode()} reports the right thing</li>
 * </ul>
 */
class SchemaSourceXorTest {

    @Test
    void connectionAndSchemaFile_bothSet_rejected() {
        Connection conn = sampleConnection();
        Generation gen = sampleGeneration("./schema.sql");

        assertThatThrownBy(() -> new UmabootConfig(conn, gen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void connectionAndSchemaFile_neitherSet_rejected() {
        Generation gen = sampleGeneration(null);

        assertThatThrownBy(() -> new UmabootConfig(null, gen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema source missing");
    }

    @Test
    void schemaFileWithJooq_rejected() {
        Generation jooqGen = sampleGenerationWithPersistence("jooq", "./schema.sql");

        assertThatThrownBy(() -> new UmabootConfig(null, jooqGen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persistence: jooq");
    }

    @Test
    void schemaFileWithJpa_accepted() {
        Generation jpaGen = sampleGenerationWithPersistence("jpa", "./schema.sql");
        UmabootConfig cfg = new UmabootConfig(null, jpaGen);
        assertThat(cfg.isSchemaFileMode()).isTrue();
        assertThat(cfg.connection()).isNull();
    }

    @Test
    void schemaFileWithMyBatis_accepted() {
        Generation mybatisGen = sampleGenerationWithPersistence("mybatis", "./schema.sql");
        UmabootConfig cfg = new UmabootConfig(null, mybatisGen);
        assertThat(cfg.isSchemaFileMode()).isTrue();
    }

    @Test
    void connectionOnly_remainsBackwardsCompatible() {
        UmabootConfig cfg = new UmabootConfig(sampleConnection(), sampleGeneration(null));
        assertThat(cfg.isSchemaFileMode()).isFalse();
        assertThat(cfg.connection()).isNotNull();
    }

    @Test
    void blankSchemaFile_treatedAsAbsent() {
        // Blank string ≠ "set" — the connection should still be required.
        Generation gen = sampleGeneration("   ");
        // connection:none + schemaFile:blank → should reject as missing
        assertThatThrownBy(() -> new UmabootConfig(null, gen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema source missing");
    }

    // ============================================================ fixtures

    private static Connection sampleConnection() {
        return new Connection(
                "url", "postgresql", null, null,
                "jdbc:postgresql://localhost:5432/db", "db", "public", "u", "p", null);
    }

    private static Generation sampleGeneration(String schemaFile) {
        return sampleGenerationWithPersistence("jpa", schemaFile);
    }

    private static Generation sampleGenerationWithPersistence(String persistence, String schemaFile) {
        return new Generation(
                "mvc", persistence, "com.example.app", "app", "com.example",
                "3.3.5", "17", true,
                UmabootConfig.OpenApiOptions.defaults(),
                UmabootConfig.InjectionOptions.defaults(),
                UmabootConfig.ValidationOptions.defaults(),
                UmabootConfig.DtoOptions.defaults(),
                UmabootConfig.ExceptionOptions.defaults(),
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(),
                UmabootConfig.PaginationOptions.defaults(),
                UmabootConfig.SecurityOptions.defaults(),
                null,
                new UmabootConfig.JpaOptions(false),
                new UmabootConfig.MyBatisOptions("xml"),
                UmabootConfig.TableFilterOptions.allowAll(),
                UmabootConfig.DddOptions.defaults(),
                UmabootConfig.OutputOptions.defaults(),
                UmabootConfig.ApplicationConfigOptions.defaults(),
                schemaFile,
                "maven");
    }
}
