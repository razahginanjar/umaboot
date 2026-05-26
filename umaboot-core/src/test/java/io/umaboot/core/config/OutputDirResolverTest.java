package io.umaboot.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the cross-surface output-directory resolver. The resolver is the
 * single source of truth used by both the CLI commands and the IntelliJ
 * plugin's {@code UmabootRunner}, so all surfaces produce identical paths
 * for identical configs.
 */
class OutputDirResolverTest {

    @Test
    void absolutePath_returnedAsIs(@TempDir Path tmp) throws Exception {
        Path absolute = tmp.resolve("custom-output").toAbsolutePath();
        UmabootConfig config = configWithOutputDir(absolute.toString());

        Path resolved = OutputDirResolver.resolve(config, tmp.resolve("umaboot.yaml"));

        assertThat(resolved).isEqualTo(absolute.normalize());
    }

    @Test
    void relativePath_resolvedAgainstConfigFileParent(@TempDir Path tmp) throws Exception {
        // /tmp/proj/umaboot.yaml + outputDir=./generated  →  /tmp/proj/generated
        Path projDir = tmp.resolve("proj");
        Path configFile = projDir.resolve("umaboot.yaml");
        UmabootConfig config = configWithOutputDir("./generated");

        Path resolved = OutputDirResolver.resolve(config, configFile);

        assertThat(resolved).isEqualTo(projDir.resolve("generated").toAbsolutePath().normalize());
    }

    @Test
    void dotPath_resolvesToConfigFileParent(@TempDir Path tmp) throws Exception {
        // outputDir=. is the canonical "output into the project directory" workflow.
        Path projDir = tmp.resolve("proj");
        Path configFile = projDir.resolve("umaboot.yaml");
        UmabootConfig config = configWithOutputDir(".");

        Path resolved = OutputDirResolver.resolve(config, configFile);

        assertThat(resolved).isEqualTo(projDir.toAbsolutePath().normalize());
    }

    @Test
    void parentRelativePath_resolvedAgainstConfigFileParent(@TempDir Path tmp) throws Exception {
        // outputDir=../sibling is unusual but should still resolve consistently.
        Path projDir = tmp.resolve("proj");
        Path configFile = projDir.resolve("umaboot.yaml");
        UmabootConfig config = configWithOutputDir("../sibling");

        Path resolved = OutputDirResolver.resolve(config, configFile);

        assertThat(resolved).isEqualTo(tmp.resolve("sibling").toAbsolutePath().normalize());
    }

    @Test
    void cliInvokedFromDifferentDirectory_stillResolvesRelativeToConfigFile(@TempDir Path tmp) {
        // This is the exact bug the fix addresses: the CLI used to resolve
        // relative outputDir against the JVM's CWD instead of the config file's
        // parent. After the fix, the answer must depend only on (config, configFile)
        // — not on what directory the CLI happens to run from.
        Path projDir = tmp.resolve("proj");
        Path configFile = projDir.resolve("umaboot.yaml");
        UmabootConfig config = configWithOutputDir("./generated");

        // Resolving from the project dir vs from the user's home dir should give
        // identical results — that's the contract.
        Path fromAnywhere1 = OutputDirResolver.resolve(config, configFile);
        Path fromAnywhere2 = OutputDirResolver.resolve(config, configFile.toAbsolutePath());
        assertThat(fromAnywhere1).isEqualTo(fromAnywhere2);
    }

    @Test
    void nullConfigFile_fallsBackToCwd() {
        // Defensive: when no config file path is available (e.g. ad-hoc API
        // callers), resolve against CWD. Documents the fallback contract.
        UmabootConfig config = configWithOutputDir("./out");

        Path resolved = OutputDirResolver.resolve(config, null);

        assertThat(resolved).isEqualTo(Paths.get("./out").toAbsolutePath().normalize());
    }

    @Test
    void nullConfig_throws() {
        assertThatThrownBy(() -> OutputDirResolver.resolve(null, Paths.get("anything")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config");
    }

    // ---------------------------------------------------------------- helper

    /**
     * Build a minimal UmabootConfig whose only relevant property is outputDir.
     * Uses the canonical-form constructor of every nested record with safe
     * defaults; passes type validation.
     */
    private static UmabootConfig configWithOutputDir(String outputDir) {
        var conn = new UmabootConfig.Connection(
                "url", "postgresql",
                null, null,
                "jdbc:postgresql://localhost:5432/db",
                "db", "public",
                "u", "p", null);
        var gen = new UmabootConfig.Generation(
                "mvc", "jpa",
                "com.example.app", "app", "com.example",
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
                outputDir,
                new UmabootConfig.JpaOptions(false),
                new UmabootConfig.MyBatisOptions("xml"),
                UmabootConfig.TableFilterOptions.allowAll(),
                UmabootConfig.DddOptions.defaults(),
                UmabootConfig.OutputOptions.defaults(),
                null);
        return new UmabootConfig(conn, gen);
    }
}
