package io.umaboot.core.introspection;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.config.UmabootConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIntrospectionServiceTest {

    @Test
    void sqliteLiveConnectionAllowsEmptyIntrospectionTarget(@TempDir Path tempDir) throws Exception {
        Path yaml = tempDir.resolve("umaboot.yaml");
        Files.writeString(yaml, """
                connection:
                  mode: host
                  type: sqlite
                  database: ":memory:"
                generation:
                  basePackage: com.example.sqlite
                  projectName: sqlite-demo
                """);

        UmabootConfig config = UmabootConfigLoader.load(yaml);
        SchemaIntrospectionService.Result result = new SchemaIntrospectionService().introspect(config);

        assertThat(result.dbType()).isEqualTo("sqlite");
        assertThat(result.schema().schemaName()).isEqualTo("main");
        assertThat(result.schema().tables()).isEmpty();
    }
}
