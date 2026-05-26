package io.umaboot.core.generator.mvc;

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
 * Smoke tests covering MVC across all persistence backends:
 * JPA, MyBatis (xml + annotation), and jOOQ.
 */
class MvcPersistenceVariantsTest {

    @Test
    void mvcJpa_emitsEntityRepositoryAndJpaPom() {
        List<GeneratedUnit> units = generate("jpa", "xml");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/Customer.java");
        assertHasFile(units, javaSrc + "/repository/CustomerRepository.java");

        String repo = readUnit(units, javaSrc + "/repository/CustomerRepository.java");
        assertThat(repo).contains("extends JpaRepository<Customer, Long>");

        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("spring-boot-starter-data-jpa");
        assertThat(pom).doesNotContain("mybatis");
        assertThat(pom).doesNotContain("jooq-codegen");
    }

    @Test
    void mvcMyBatisXml_emitsXmlMapperAndMyBatisPom() {
        List<GeneratedUnit> units = generate("mybatis", "xml");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/Customer.java");
        assertHasFile(units, javaSrc + "/mapper/CustomerMapper.java");
        assertHasFile(units, "src/main/resources/mapper/CustomerMapper.xml");
        // No JpaRepository in MyBatis mode
        assertNoFile(units, javaSrc + "/repository/CustomerRepository.java");

        // Mapper interface in XML mode has @Mapper but no @Select annotations
        String mapperIface = readUnit(units, javaSrc + "/mapper/CustomerMapper.java");
        assertThat(mapperIface).contains("@Mapper");
        assertThat(mapperIface).doesNotContain("@Select");

        // ServiceImpl uses the MyBatis mapper, not a JpaRepository
        String serviceImpl = readUnit(units, javaSrc + "/service/impl/CustomerServiceImpl.java");
        assertThat(serviceImpl).contains("CustomerMapper sqlMapper");
        assertThat(serviceImpl).contains("PageImpl");

        // POM has mybatis starter and mapper-locations in yml
        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("mybatis-spring-boot-starter");
        assertThat(pom).doesNotContain("spring-boot-starter-data-jpa");

        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("mapper-locations: classpath:mapper/*.xml");
    }

    @Test
    void mvcMyBatisAnnotation_inlinesSqlNoXml() {
        List<GeneratedUnit> units = generate("mybatis", "annotation");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/mapper/CustomerMapper.java");
        // No XML in annotation mode
        assertNoFile(units, "src/main/resources/mapper/CustomerMapper.xml");

        String mapperIface = readUnit(units, javaSrc + "/mapper/CustomerMapper.java");
        assertThat(mapperIface).contains("@Insert");
        assertThat(mapperIface).contains("@Update");
        assertThat(mapperIface).contains("@Delete");
        assertThat(mapperIface).contains("@Select");
        assertThat(mapperIface).contains("INSERT INTO customers");

        // application.yml does not have mapper-locations in annotation mode
        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).doesNotContain("mapper-locations:");
    }

    @Test
    void mvcJooq_emitsDslRepositoryWithCodegenPlugin() {
        List<GeneratedUnit> units = generate("jooq", "xml");

        String javaSrc = "src/main/java/com/example/shop";
        assertHasFile(units, javaSrc + "/entity/Customer.java");
        assertHasFile(units, javaSrc + "/repository/CustomerRepository.java");

        // Repository is the jOOQ facade, not a JpaRepository
        String repo = readUnit(units, javaSrc + "/repository/CustomerRepository.java");
        assertThat(repo).contains("DSLContext dsl");
        assertThat(repo).contains("import static com.example.shop.jooq.Tables.CUSTOMERS;");
        assertThat(repo).doesNotContain("JpaRepository");
        assertThat(repo).doesNotContain("UnsupportedOperationException");

        // ServiceImpl uses the jOOQ variant (page/size translation, count)
        String serviceImpl = readUnit(units, javaSrc + "/service/impl/CustomerServiceImpl.java");
        assertThat(serviceImpl).contains("repository.findAll(page, size)");
        assertThat(serviceImpl).contains("repository.count()");

        // POM has jOOQ starter + codegen plugin
        String pom = readUnit(units, "pom.xml");
        assertThat(pom).contains("spring-boot-starter-jooq");
        assertThat(pom).contains("jooq-codegen-maven");
        assertThat(pom).contains("PostgresDatabase");
        assertThat(pom).contains("<packageName>com.example.shop.jooq</packageName>");

        // application.yml logs jOOQ, not Hibernate
        String yml = readUnit(units, "src/main/resources/application.yml");
        assertThat(yml).contains("org.jooq.tools.LoggerListener");
        assertThat(yml).doesNotContain("org.hibernate.SQL");
    }

    // ---- helpers ----

    private static List<GeneratedUnit> generate(String persistence, String mybatisStyle) {
        SchemaModel schema = singleTableSchema();
        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                "mvc", persistence, mybatisStyle, false, "none", "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, "postgres", null);
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema);
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
