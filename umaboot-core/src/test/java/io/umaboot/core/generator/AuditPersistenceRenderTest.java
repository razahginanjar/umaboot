package io.umaboot.core.generator;

import io.umaboot.core.config.UmabootConfig;
import io.umaboot.core.generator.ddd.DddGenerator;
import io.umaboot.core.generator.hexagonal.HexagonalGenerator;
import io.umaboot.core.generator.mvc.MvcGenerator;
import io.umaboot.core.model.ColumnModel;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPersistenceRenderTest {

    @Test
    void jpaUsesSpringDataJpaAuditingAndKeepsAuditOutOfRequests() {
        List<GeneratedUnit> units = generateMvc("jpa", "xml", "yaml");
        String javaSrc = "src/main/java/com/example/shop";

        assertHasFile(units, javaSrc + "/common/Auditable.java");
        assertHasFile(units, javaSrc + "/common/AuditorAwareConfig.java");
        assertNoFile(units, javaSrc + "/common/AuditProvider.java");

        String app = readUnit(units, javaSrc + "/Application.java");
        assertThat(app)
                .contains("import org.springframework.data.jpa.repository.config.EnableJpaAuditing;")
                .contains("@EnableJpaAuditing(auditorAwareRef = \"auditorAware\")");

        String request = readUnit(units, javaSrc + "/dto/CustomerRequestDTO.java");
        assertThat(request)
                .doesNotContain("createdAt")
                .doesNotContain("updatedAt")
                .doesNotContain("createdBy")
                .doesNotContain("updatedBy");

        String response = readUnit(units, javaSrc + "/dto/CustomerResponseDTO.java");
        assertThat(response)
                .contains("private Instant createdAt;")
                .contains("private Instant updatedAt;")
                .contains("private String createdBy;")
                .contains("private String updatedBy;");
    }

    @Test
    void mvcMyBatisUsesManualAuditProviderAndReadOnlyAuditDtos() {
        List<GeneratedUnit> units = generateMvc("mybatis", "xml", "yaml");
        String javaSrc = "src/main/java/com/example/shop";

        assertNoFile(units, javaSrc + "/common/Auditable.java");
        assertNoFile(units, javaSrc + "/common/AuditorAwareConfig.java");
        assertHasFile(units, javaSrc + "/common/AuditProvider.java");

        String app = readUnit(units, javaSrc + "/Application.java");
        assertThat(app)
                .doesNotContain("EnableJpaAuditing")
                .doesNotContain("org.springframework.data.jpa");

        String service = readUnit(units, javaSrc + "/service/impl/CustomerServiceImpl.java");
        assertThat(service)
                .contains("private final AuditProvider auditProvider;")
                .contains("markCreated(entity);")
                .contains("markUpdated(existing);")
                .contains("entity.setCreatedAt(auditProvider.nowLocalDateTime());")
                .contains("entity.setCreatedBy(auditProvider.currentUser());")
                .contains("entity.setUpdatedAt(auditProvider.nowLocalDateTime());")
                .contains("entity.setUpdatedBy(auditProvider.currentUser());");

        String entity = readUnit(units, javaSrc + "/entity/Customer.java");
        assertThat(entity)
                .contains("private LocalDateTime createdAt;")
                .contains("private LocalDateTime updatedAt;")
                .contains("private String createdBy;")
                .contains("private String updatedBy;");

        String request = readUnit(units, javaSrc + "/dto/CustomerRequestDTO.java");
        assertThat(request)
                .doesNotContain("createdAt")
                .doesNotContain("updatedAt")
                .doesNotContain("createdBy")
                .doesNotContain("updatedBy");

        String response = readUnit(units, javaSrc + "/dto/CustomerResponseDTO.java");
        assertThat(response)
                .contains("private LocalDateTime createdAt;")
                .contains("private LocalDateTime updatedAt;")
                .contains("private String createdBy;")
                .contains("private String updatedBy;");

        String mapperXml = readUnit(units, "src/main/resources/mapper/CustomerMapper.xml");
        assertThat(mapperXml)
                .contains("created_at")
                .contains("updated_at")
                .doesNotContain("created_at =")
                .doesNotContain("created_by =")
                .contains("updated_at = #{updatedAt}")
                .contains("updated_by = #{updatedBy}");

        String openApi = readUnit(units, "src/main/resources/openapi.yaml");
        String requestSchema = openApi.substring(
                openApi.indexOf("    CustomerRequestDTO:"),
                openApi.indexOf("    CustomerResponseDTO:"));
        assertThat(requestSchema)
                .doesNotContain("createdAt")
                .doesNotContain("updatedAt")
                .doesNotContain("createdBy")
                .doesNotContain("updatedBy");
        assertThat(openApi)
                .contains("createdAt:")
                .contains("updatedBy:");
    }

    @Test
    void mvcJooqUsesManualAuditProviderWithoutJpaAuditing() {
        List<GeneratedUnit> units = generateMvc("jooq", "xml", "none");
        String javaSrc = "src/main/java/com/example/shop";

        assertHasFile(units, javaSrc + "/common/AuditProvider.java");

        String app = readUnit(units, javaSrc + "/Application.java");
        assertThat(app).doesNotContain("EnableJpaAuditing");

        String service = readUnit(units, javaSrc + "/service/impl/CustomerServiceImpl.java");
        assertThat(service)
                .contains("private final AuditProvider auditProvider;")
                .contains("markCreated(entity);")
                .contains("markUpdated(existing);");

        String entity = readUnit(units, javaSrc + "/entity/Customer.java");
        assertThat(entity)
                .contains("private LocalDateTime createdAt;")
                .contains("private String updatedBy;");
    }

    @Test
    void hexagonalMyBatisSetsManualAuditInApplicationService() {
        List<GeneratedUnit> units = generateHexagonal("mybatis", "xml");
        String javaSrc = "src/main/java/com/example/shop";

        assertHasFile(units, javaSrc + "/common/AuditProvider.java");
        String app = readUnit(units, javaSrc + "/Application.java");
        assertThat(app).doesNotContain("EnableJpaAuditing");

        String service = readUnit(units, javaSrc + "/application/service/CustomerApplicationService.java");
        assertThat(service)
                .contains("private final AuditProvider auditProvider;")
                .contains("markCreated(command);")
                .contains("markUpdated(existing);");

        String request = readUnit(units, javaSrc + "/adapter/in/web/dto/CustomerRequest.java");
        assertThat(request).doesNotContain("createdAt").doesNotContain("updatedBy");

        String response = readUnit(units, javaSrc + "/adapter/in/web/dto/CustomerResponse.java");
        assertThat(response)
                .contains("private LocalDateTime createdAt;")
                .contains("private String updatedBy;");
    }

    @Test
    void dddMyBatisSetsManualAuditThroughAggregateHooks() {
        List<GeneratedUnit> units = generateDdd("mybatis", "xml");
        String javaSrc = "src/main/java/com/example/shop";

        assertHasFile(units, javaSrc + "/common/AuditProvider.java");
        String app = readUnit(units, javaSrc + "/Application.java");
        assertThat(app).doesNotContain("EnableJpaAuditing");

        String service = readUnit(units, javaSrc + "/application/customer/CustomerApplicationService.java");
        assertThat(service)
                .contains("private final AuditProvider auditProvider;")
                .contains("aggregate.markCreated(")
                .contains("aggregate.markUpdated(");

        String aggregate = readUnit(units, javaSrc + "/domain/customer/Customer.java");
        assertThat(aggregate)
                .contains("public void markCreated(")
                .contains("public void markUpdated(")
                .contains("private LocalDateTime createdAt;")
                .contains("private String updatedBy;");

        String request = readUnit(units, javaSrc + "/interfaces/rest/dto/CreateCustomerRequest.java");
        assertThat(request).doesNotContain("createdAt").doesNotContain("updatedBy");

        String response = readUnit(units, javaSrc + "/interfaces/rest/dto/CustomerResponse.java");
        assertThat(response)
                .contains("private LocalDateTime createdAt;")
                .contains("private String updatedBy;");
    }

    private static List<GeneratedUnit> generateMvc(String persistence, String mybatisStyle, String openApiStyle) {
        return new MvcGenerator(new TemplateEngine(null), ctx("mvc", persistence, mybatisStyle, openApiStyle))
                .generate(schema());
    }

    private static List<GeneratedUnit> generateHexagonal(String persistence, String mybatisStyle) {
        return new HexagonalGenerator(new TemplateEngine(null), ctx("hexagonal", persistence, mybatisStyle, "none"))
                .generate(schema());
    }

    private static List<GeneratedUnit> generateDdd(String persistence, String mybatisStyle) {
        return new DddGenerator(new TemplateEngine(null), ctx("ddd", persistence, mybatisStyle, "none"))
                .generate(schema());
    }

    private static GeneratorContext ctx(
            String architecture,
            String persistence,
            String mybatisStyle,
            String openApiStyle) {
        return new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                architecture, persistence, mybatisStyle, false, openApiStyle, "constructor",
                "jakarta", "class", "separate", "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(), UmabootConfig.LoggingOptions.defaults(),
                UmabootConfig.TestOptions.defaults(), "offset", UmabootConfig.SecurityOptions.defaults(),
                UmabootConfig.DddOptions.defaults(), false, "postgres", null, null, "", null, "maven");
    }

    private static SchemaModel schema() {
        return new SchemaModel("public", List.of(table()));
    }

    private static TableModel table() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        ColumnModel createdAt = new ColumnModel("created_at", Types.TIMESTAMP, "timestamp", 0, 0,
                false, false, false, null, "", List.of());
        ColumnModel updatedAt = new ColumnModel("updated_at", Types.TIMESTAMP, "timestamp", 0, 0,
                true, false, false, null, "", List.of());
        ColumnModel createdBy = new ColumnModel("created_by", Types.VARCHAR, "varchar", 64, 0,
                false, false, false, null, "", List.of());
        ColumnModel updatedBy = new ColumnModel("updated_by", Types.VARCHAR, "varchar", 64, 0,
                true, false, false, null, "", List.of());
        return new TableModel("customers", "public", "",
                List.of(id, email, createdAt, updatedAt, createdBy, updatedBy),
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
