package io.umaboot.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UmabootConfigLoaderTest {

    @Test
    void loadsValidYaml(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  url: jdbc:postgresql://localhost:5432/test
                  username: postgres
                  password: secret
                  schema: public
                generation:
                  architecture: mvc
                  persistence: jpa
                  basePackage: com.example.shop
                  projectName: shop-api
                  useLombok: true
                  outputDir: ./out
                  output:
                    mode: standalone
                    existingPolicy: overwrite
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);

        assertThat(config.connection().url()).isEqualTo("jdbc:postgresql://localhost:5432/test");
        assertThat(config.connection().schema()).isEqualTo("public");
        assertThat(config.generation().basePackage()).isEqualTo("com.example.shop");
        assertThat(config.generation().projectName()).isEqualTo("shop-api");
        assertThat(config.generation().useLombok()).isTrue();
        assertThat(config.generation().output().existingPolicy()).isEqualTo("overwrite");
    }

    @Test
    void lombokVersion_isOnlyKeptWhenSpringBootIsBelow35AndLombokEnabled(@TempDir Path tmp) throws Exception {
        Path boot27 = tmp.resolve("boot27.yaml");
        Files.writeString(boot27, """
                schemaFile: schema.sql
                generation:
                  basePackage: com.example.app
                  projectName: app
                  springBootVersion: 2.7.18
                  javaVersion: 8
                  useLombok: true
                  exception:
                    style: envelope
                """);
        UmabootConfig legacy = UmabootConfigLoader.load(boot27);
        assertThat(legacy.generation().lombokVersion()).isEqualTo("1.18.30");

        Path rewritten = tmp.resolve("rewritten.yaml");
        UmabootYamlIO.save(rewritten, legacy);
        assertThat(Files.readString(rewritten)).contains("lombokVersion: 1.18.30");

        Path explicit = tmp.resolve("explicit.yaml");
        Files.writeString(explicit, """
                schemaFile: schema.sql
                generation:
                  basePackage: com.example.app
                  projectName: app
                  springBootVersion: 3.4.7
                  javaVersion: 17
                  useLombok: true
                  lombokVersion: 1.18.46
                """);
        assertThat(UmabootConfigLoader.load(explicit).generation().lombokVersion()).isEqualTo("1.18.46");

        Path boot35 = tmp.resolve("boot35.yaml");
        Files.writeString(boot35, """
                schemaFile: schema.sql
                generation:
                  basePackage: com.example.app
                  projectName: app
                  springBootVersion: 3.5.0
                  javaVersion: 17
                  useLombok: true
                  lombokVersion: 1.18.46
                """);
        assertThat(UmabootConfigLoader.load(boot35).generation().lombokVersion()).isNull();

        Path noLombok = tmp.resolve("no-lombok.yaml");
        Files.writeString(noLombok, """
                schemaFile: schema.sql
                generation:
                  basePackage: com.example.app
                  projectName: app
                  springBootVersion: 2.7.18
                  javaVersion: 8
                  useLombok: false
                  lombokVersion: 1.18.46
                  exception:
                    style: envelope
                """);
        assertThat(UmabootConfigLoader.load(noLombok).generation().lombokVersion()).isNull();
    }

    @Test
    void rejectsMissingRequiredField(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  url: jdbc:postgresql://localhost/test
                generation:
                  basePackage: com.example
                """);

        assertThatThrownBy(() -> UmabootConfigLoader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectName");
    }

    @Test
    void rejectsMissingConfigFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.yaml");
        assertThatThrownBy(() -> UmabootConfigLoader.load(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void tolerates_emptyNestedSections(@TempDir Path tmp) throws Exception {
        // Reproduces the NPE when YAML keys are present with null/empty values
        // (e.g. "belongsTo:" with nothing after the colon).
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  url: jdbc:postgresql://localhost:5432/test
                  schema: public
                generation:
                  basePackage: com.example.app
                  projectName: my-app
                  jpa:
                  mybatis:
                  tables:
                  output:
                  ddd:
                    aggregateRoots:
                    nonRoots:
                    belongsTo:
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);

        // All nested options resolved to defaults, not null
        assertThat(config.generation().jpa().useMapStruct()).isFalse();
        assertThat(config.generation().mybatis().style()).isEqualTo("xml");
        assertThat(config.generation().tables().include()).isEmpty();
        assertThat(config.generation().output().mode()).isEqualTo("standalone");
        assertThat(config.generation().ddd().aggregateRoots()).isEmpty();
        assertThat(config.generation().ddd().belongsTo()).isEmpty();
    }

    // ---------------------------------------------------------------- connection model v0.8

    @Test
    void legacyConnectionShape_isAccepted_andDatabaseInferredFromUrl(@TempDir Path tmp) throws Exception {
        // Pre-v0.8 yaml shape: only url + schema. No mode/type/host/database keys.
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  url: jdbc:mysql://localhost:3306/legacy_db
                  username: root
                  password: secret
                  schema: legacy_db
                generation:
                  basePackage: com.example.legacy
                  projectName: legacy-app
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);

        // Auto-migrated to new shape in memory.
        assertThat(config.connection().mode()).isEqualTo("url");
        assertThat(config.connection().type()).isEqualTo("mysql");
        // database parsed out of the URL path component.
        assertThat(config.connection().database()).isEqualTo("legacy_db");
        // legacy schema preserved verbatim (the documented mysql workaround).
        assertThat(config.connection().schema()).isEqualTo("legacy_db");
        // url accessor still works for any legacy reader.
        assertThat(config.connection().url()).isEqualTo("jdbc:mysql://localhost:3306/legacy_db");
        // driver alias still works.
        assertThat(config.connection().driver()).isEqualTo("mysql");
    }

    @Test
    void hostMode_composesUrlFromParts(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: host
                  type: postgresql
                  host: localhost:5432
                  database: shop
                  schema: public
                  params: useSSL=false&applicationName=umaboot
                  username: postgres
                  password: secret
                generation:
                  basePackage: com.example.shop
                  projectName: shop-api
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);

        assertThat(config.connection().mode()).isEqualTo("host");
        assertThat(config.connection().type()).isEqualTo("postgresql");
        assertThat(config.connection().host()).isEqualTo("localhost:5432");
        assertThat(config.connection().database()).isEqualTo("shop");
        assertThat(config.connection().params()).isEqualTo("useSSL=false&applicationName=umaboot");
        // composed URL has single '?' before params, never '/?'.
        assertThat(config.connection().url())
                .isEqualTo("jdbc:postgresql://localhost:5432/shop?useSSL=false&applicationName=umaboot");
    }

    @Test
    void urlMode_explicitFields_isAccepted(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: url
                  type: mysql
                  url: jdbc:mysql://prod-db:3306/orders?useSSL=true
                  database: orders
                  schema:
                  username: app
                  password: secret
                generation:
                  basePackage: com.example.orders
                  projectName: orders-svc
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);

        assertThat(config.connection().mode()).isEqualTo("url");
        assertThat(config.connection().type()).isEqualTo("mysql");
        assertThat(config.connection().url()).isEqualTo("jdbc:mysql://prod-db:3306/orders?useSSL=true");
        assertThat(config.connection().database()).isEqualTo("orders");
    }

    @Test
    void hostMode_paramsWithLeadingQuestionMark_isRejected(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: host
                  type: postgresql
                  host: localhost:5432
                  database: shop
                  params: "?useSSL=false"
                  username: postgres
                  password: secret
                generation:
                  basePackage: com.example.shop
                  projectName: shop-api
                """);

        assertThatThrownBy(() -> UmabootConfigLoader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not start with '?'");
    }

    @Test
    void hostMode_missingHostOrDatabase_isRejected(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: host
                  type: postgresql
                  host: localhost:5432
                  database:
                  username: postgres
                generation:
                  basePackage: com.example.shop
                  projectName: shop-api
                """);

        assertThatThrownBy(() -> UmabootConfigLoader.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("database is required");
    }

    @Test
    void roundTrip_hostMode_preservesShape(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: host
                  type: postgresql
                  host: localhost:5432
                  database: shop
                  schema: public
                  params: useSSL=false
                  username: postgres
                  password: secret
                generation:
                  basePackage: com.example.shop
                  projectName: shop-api
                """);

        UmabootConfig loaded = UmabootConfigLoader.load(yaml);

        Path out = tmp.resolve("written.yaml");
        UmabootYamlIO.save(out, loaded);
        UmabootConfig reloaded = UmabootConfigLoader.load(out);

        assertThat(reloaded.connection().mode()).isEqualTo("host");
        assertThat(reloaded.connection().type()).isEqualTo("postgresql");
        assertThat(reloaded.connection().host()).isEqualTo("localhost:5432");
        assertThat(reloaded.connection().database()).isEqualTo("shop");
        assertThat(reloaded.connection().params()).isEqualTo("useSSL=false");
        assertThat(reloaded.connection().url())
                .isEqualTo("jdbc:postgresql://localhost:5432/shop?useSSL=false");
    }

    @Test
    void legacyYaml_rewrittenInNewShapeOnSave(@TempDir Path tmp) throws Exception {
        // Read a legacy file, save it back: the rewritten form should be the new shape.
        Path src = tmp.resolve("legacy.yaml");
        Files.writeString(src, """
                connection:
                  url: jdbc:postgresql://localhost:5432/legacy
                  username: postgres
                  password: secret
                  schema: public
                generation:
                  basePackage: com.example.legacy
                  projectName: legacy-app
                """);

        UmabootConfig loaded = UmabootConfigLoader.load(src);

        Path rewritten = tmp.resolve("rewritten.yaml");
        UmabootYamlIO.save(rewritten, loaded);
        String body = Files.readString(rewritten);

        // New keys present:
        assertThat(body).contains("mode: url");
        assertThat(body).contains("type: postgresql");
        assertThat(body).contains("database: legacy");
        // The old `driver:` key must be gone — it's now expressed as `type:`.
        assertThat(body).doesNotContain("driver:");
    }

    @Test
    void schemaFileDialect_roundTripsAtTopLevel(@TempDir Path tmp) throws Exception {
        Path yaml = tmp.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                schemaFile: schema.sql
                schemaDialect: mysql
                generation:
                  basePackage: com.example.orders
                  projectName: orders-api
                  persistence: jpa
                  migrations:
                    style: flyway
                """);

        UmabootConfig loaded = UmabootConfigLoader.load(yaml);

        assertThat(loaded.connection()).isNull();
        assertThat(loaded.generation().schemaFile()).isEqualTo("schema.sql");
        assertThat(loaded.generation().schemaDialect()).isEqualTo("mysql");
        assertThat(loaded.generation().migrations().style()).isEqualTo("flyway");

        Path rewritten = tmp.resolve("rewritten.yaml");
        UmabootYamlIO.save(rewritten, loaded);
        String body = Files.readString(rewritten);

        assertThat(body).contains("schemaFile: schema.sql");
        assertThat(body).contains("schemaDialect: mysql");
        assertThat(body).contains("migrations:");
        assertThat(body).contains("style: flyway");
        assertThat(UmabootConfigLoader.load(rewritten).generation().schemaDialect()).isEqualTo("mysql");
        assertThat(UmabootConfigLoader.load(rewritten).generation().migrations().style()).isEqualTo("flyway");
    }
}
