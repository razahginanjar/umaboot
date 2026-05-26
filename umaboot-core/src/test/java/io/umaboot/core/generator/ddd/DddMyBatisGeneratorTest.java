package io.umaboot.core.generator.ddd;

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
 * Smoke test for the DDD generator's MyBatis variants — both XML and
 * annotation modes.
 */
class DddMyBatisGeneratorTest {

    @Test
    void mybatisXmlMode_emitsMapperXmlAndUsesMybatisRepositoryImpl() {
        List<GeneratedUnit> units = generate("xml");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/infrastructure/persistence/CustomerPersistenceModel.java");
        assertHasFile(units, javaSrc + "/infrastructure/persistence/CustomerMyBatisMapper.java");
        assertHasFile(units, javaSrc + "/infrastructure/persistence/CustomerRepositoryImpl.java");
        assertHasFile(units, javaSrc + "/infrastructure/persistence/CustomerPersistenceMapper.java");
        assertHasFile(units, "src/main/resources/mapper/CustomerMyBatisMapper.xml");
        // No JPA artifacts
        assertNoFile(units, javaSrc + "/infrastructure/persistence/CustomerJpaEntity.java");
        assertNoFile(units, javaSrc + "/infrastructure/persistence/CustomerJpaRepository.java");
        assertNoFile(units, javaSrc + "/infrastructure/persistence/CustomerJpaMapper.java");

        // RepositoryImpl uses the MyBatis mapper
        String repoImpl = readUnit(units, javaSrc + "/infrastructure/persistence/CustomerRepositoryImpl.java");
        assertThat(repoImpl).contains("CustomerMyBatisMapper sqlMapper");
        assertThat(repoImpl).contains("implements CustomerRepository");
        assertThat(repoImpl).doesNotContain("JpaRepository");

        // pom.xml has mybatis-spring-boot-starter
        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("mybatis-spring-boot-starter");
        assertThat(pom).doesNotContain("spring-boot-starter-data-jpa");

        // application.yml has mapper-locations
        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("mapper-locations: classpath:mapper/*.xml");
    }

    @Test
    void mybatisAnnotationMode_noXmlNoOnlyAnnotations() {
        List<GeneratedUnit> units = generate("annotation");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/infrastructure/persistence/CustomerMyBatisMapper.java");
        assertNoFile(units, "src/main/resources/mapper/CustomerMyBatisMapper.xml");

        String mapperIface = readUnit(units, javaSrc + "/infrastructure/persistence/CustomerMyBatisMapper.java");
        assertThat(mapperIface).contains("@Insert");
        assertThat(mapperIface).contains("@Update");
        assertThat(mapperIface).contains("@Delete");
        assertThat(mapperIface).contains("INSERT INTO customers");
    }

    @Test
    void aggregateRootStaysJpaFreeAcrossPersistence() {
        for (String style : List.of("xml", "annotation")) {
            List<GeneratedUnit> units = generate(style);
            String agg = readUnit(units, "src/main/java/com/example/shop/domain/customer/Customer.java");
            assertThat(agg).doesNotContain("jakarta.persistence")
                    .doesNotContain("@Entity")
                    .contains("public static Customer create(")
                    .contains("pullDomainEvents");
        }
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(String mybatisStyle) {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "ddd", "mybatis", mybatisStyle, false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, "postgres");
        return new DddGenerator(new TemplateEngine(null), ctx).generate(schema);
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
