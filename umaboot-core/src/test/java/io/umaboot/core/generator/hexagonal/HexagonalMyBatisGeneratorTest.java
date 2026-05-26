package io.umaboot.core.generator.hexagonal;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the Hexagonal generator's MyBatis variants — both XML and
 * annotation modes. Each test renders a synthetic schema and verifies the
 * expected files are emitted with the expected content.
 */
class HexagonalMyBatisGeneratorTest {

    @Test
    void mybatisXmlMode_emitsMapperXml() {
        List<GeneratedUnit> units = generate("xml");

        String javaSrc = "src/main/java/com/example/shop";
        // Persistence model + Mapper interface + Adapter + Domain mapper exist
        assertHasFile(units, javaSrc + "/adapter/out/persistence/CustomerPersistenceModel.java");
        assertHasFile(units, javaSrc + "/adapter/out/persistence/CustomerMyBatisMapper.java");
        assertHasFile(units, javaSrc + "/adapter/out/persistence/CustomerPersistenceAdapter.java");
        assertHasFile(units, javaSrc + "/adapter/out/persistence/mapper/CustomerPersistenceMapper.java");
        // The XML mapper file
        assertHasFile(units, "src/main/resources/mapper/CustomerMyBatisMapper.xml");
        // No JPA artifacts present
        assertNoFile(units, javaSrc + "/adapter/out/persistence/CustomerJpaEntity.java");
        assertNoFile(units, javaSrc + "/adapter/out/persistence/CustomerJpaRepository.java");

        // Mapper interface in XML mode has no @Select annotations
        String mapperIface = readUnit(units, javaSrc + "/adapter/out/persistence/CustomerMyBatisMapper.java");
        assertThat(mapperIface).contains("@Mapper");
        assertThat(mapperIface).doesNotContain("@Select");

        // Adapter delegates to the MyBatis mapper, not a JpaRepository
        String adapter = readUnit(units, javaSrc + "/adapter/out/persistence/CustomerPersistenceAdapter.java");
        assertThat(adapter).contains("CustomerMyBatisMapper sqlMapper");
        assertThat(adapter).doesNotContain("JpaRepository");

        // pom.xml carries mybatis-spring-boot-starter
        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("mybatis-spring-boot-starter");
        assertThat(pom).doesNotContain("spring-boot-starter-data-jpa");

        // application.yml lists mapper-locations
        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("mapper-locations: classpath:mapper/*.xml");
    }

    @Test
    void mybatisAnnotationMode_inlinesSql() {
        List<GeneratedUnit> units = generate("annotation");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/adapter/out/persistence/CustomerMyBatisMapper.java");
        // No XML in annotation mode
        assertNoFile(units, "src/main/resources/mapper/CustomerMyBatisMapper.xml");

        String mapperIface = readUnit(units, javaSrc + "/adapter/out/persistence/CustomerMyBatisMapper.java");
        assertThat(mapperIface).contains("@Insert");
        assertThat(mapperIface).contains("@Update");
        assertThat(mapperIface).contains("@Delete");
        assertThat(mapperIface).contains("@Select");
        assertThat(mapperIface).contains("INSERT INTO customers");

        // application.yml does NOT include mapper-locations in annotation mode
        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).doesNotContain("mapper-locations:");
    }

    @Test
    void domainStaysJpaFreeAcrossPersistence() {
        for (String style : List.of("xml", "annotation")) {
            List<GeneratedUnit> units = generate(style);
            String domain = readUnit(units, "src/main/java/com/example/shop/domain/model/Customer.java");
            assertThat(domain).doesNotContain("jakarta.persistence")
                    .doesNotContain("@Entity");
        }
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(String mybatisStyle) {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "hexagonal", "mybatis", mybatisStyle, false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, "postgres", null, null, "");
        return new HexagonalGenerator(new TemplateEngine(null), ctx).generate(schema);
    }

    private static SchemaModel singleTableSchema() {
        return new SchemaModel("public", List.of(simpleTable("customers")));
    }

    private static TableModel simpleTable(String name) {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        ColumnModel fullName = new ColumnModel("full_name", Types.VARCHAR, "varchar", 200, 0,
                false, false, false, null, "", List.of());
        return new TableModel(name, "public", "",
                List.of(id, email, fullName),
                List.of("id"),
                List.of(),
                List.of(),
                false);
    }

    private static void assertHasFile(List<GeneratedUnit> units, String relativePath) {
        assertThat(units).extracting(GeneratedUnit::relativePath).contains(relativePath);
    }

    private static void assertNoFile(List<GeneratedUnit> units, String relativePath) {
        assertThat(units).extracting(GeneratedUnit::relativePath).doesNotContain(relativePath);
    }

    private static String readUnit(List<GeneratedUnit> units, String relativePath) {
        return units.stream()
                .filter(u -> u.relativePath().equals(relativePath))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + relativePath));
    }
}
