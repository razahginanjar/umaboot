package io.umaboot.core.config;

import io.umaboot.core.generator.GeneratorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigMergerTest {

    @Test
    void mybatisXml_addsMapperLocationsToYaml(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n  application:\n    name: my-app\n");

        GeneratorContext ctx = ctx("mybatis", "xml");
        Path written = ApplicationConfigMerger.merge(tmp, ctx);

        assertThat(written).isEqualTo(yml);
        String after = Files.readString(yml);
        assertThat(after).contains("# ---- begin Umaboot additions");
        assertThat(after).contains("mybatis:")
                .contains("mapper-locations: classpath:mapper/*.xml")
                .contains("map-underscore-to-camel-case: true");
        // Original content preserved
        assertThat(after).startsWith("spring:");
    }

    @Test
    void mybatisAnnotation_addsCamelCaseOnlyToYaml(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        ApplicationConfigMerger.merge(tmp, ctx("mybatis", "annotation"));

        String after = Files.readString(yml);
        assertThat(after).contains("map-underscore-to-camel-case: true");
        assertThat(after).doesNotContain("mapper-locations");
    }

    @Test
    void properties_addsDottedKeysToProperties(@TempDir Path tmp) throws Exception {
        Path props = tmp.resolve("src/main/resources/application.properties");
        Files.createDirectories(props.getParent());
        Files.writeString(props, "spring.application.name=my-app\n");

        ApplicationConfigMerger.merge(tmp, ctx("mybatis", "xml"));

        String after = Files.readString(props);
        assertThat(after).contains("mybatis.mapper-locations=classpath:mapper/*.xml");
        assertThat(after).contains("mybatis.configuration.map-underscore-to-camel-case=true");
        assertThat(after).startsWith("spring.application.name=my-app");
    }

    @Test
    void idempotent_doesNotAppendTwice(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        GeneratorContext ctx = ctx("mybatis", "xml");
        ApplicationConfigMerger.merge(tmp, ctx);
        long sizeAfterFirst = Files.size(yml);
        ApplicationConfigMerger.merge(tmp, ctx);
        long sizeAfterSecond = Files.size(yml);

        assertThat(sizeAfterSecond).isEqualTo(sizeAfterFirst);
    }

    @Test
    void noAdditionsForJpa_returnsNull(@TempDir Path tmp) throws Exception {
        Path yml = tmp.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        Path written = ApplicationConfigMerger.merge(tmp, ctx("jpa", "xml"));
        assertThat(written).isNull();
        // File unchanged
        assertThat(Files.readString(yml)).isEqualTo("spring:\n");
    }

    @Test
    void noExistingFile_isNoOp(@TempDir Path tmp) {
        Path written = ApplicationConfigMerger.merge(tmp, ctx("mybatis", "xml"));
        assertThat(written).isNull();
    }

    private static GeneratorContext ctx(String persistence, String mybatisStyle) {
        return new GeneratorContext(
                "com.example.app", "my-app", "com.example",
                "3.3.5", "17", true,
                "mvc", persistence, mybatisStyle, false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(),
                true /* overlay */, "postgres", null, null, "", null, "maven");
    }
}
