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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Render-parity tests for Spring Boot 2.7 + Java 8/11 generation (Phases L–N + Security on SB2).
 *
 * <p>The single template tree must produce SB2-compatible output when configured
 * with {@code springBootVersion: 2.7.x} and {@code javaVersion: 8}/11. This
 * locks down the inline-conditional strategy: {@code ${eeNamespace}}, {@code <#if springBoot3>},
 * record-vs-class branches, springdoc artifact selection, etc.</p>
 */
class SpringBoot2RenderTest {

    // ============================================================
    // MVC + JPA on SB2
    // ============================================================

    @Test
    void mvcJpa_sb2_swapsJakartaForJavax() {
        List<GeneratedUnit> units = generateMvc("jpa", false);

        String entity = readUnit(units, "src/main/java/com/example/shop/entity/Customer.java");
        assertThat(entity)
                .as("Entity must use javax.persistence on SB2")
                .contains("import javax.persistence.")
                .doesNotContain("import jakarta.");

        String controller = readUnit(units, "src/main/java/com/example/shop/controller/CustomerController.java");
        assertThat(controller)
                .contains("import javax.validation.Valid;")
                .doesNotContain("jakarta.");

        String requestDto = readUnit(units, "src/main/java/com/example/shop/dto/CustomerRequestDTO.java");
        assertThat(requestDto)
                .contains("import javax.validation.constraints.")
                .doesNotContain("jakarta.");
    }

    @Test
    void mvcJpa_sb2_pomTargetsSpringBoot27_andLegacySpringdoc() {
        List<GeneratedUnit> units = generateMvc("jpa", true /* annotation openapi */);

        String pom = readUnit(units, "pom.xml");
        assertThat(pom)
                .contains("<version>2.7.18</version>")
                .contains("<java.version>11</java.version>")
                // Legacy springdoc artifact for SB2
                .contains("<artifactId>springdoc-openapi-ui</artifactId>")
                .contains("<version>1.8.0</version>")
                // Must NOT use the SB3 artifact
                .doesNotContain("springdoc-openapi-starter-webmvc-ui");
    }

    @Test
    void mvcJpa_sb3_keepsJakartaAndModernSpringdoc() {
        List<GeneratedUnit> units = generateMvcSb3();

        String entity = readUnit(units, "src/main/java/com/example/shop/entity/Customer.java");
        assertThat(entity)
                .contains("import jakarta.persistence.")
                .doesNotContain("import javax.persistence");

        String pom = readUnit(units, "pom.xml");
        assertThat(pom)
                .contains("<version>3.3.5</version>")
                .contains("<java.version>17</java.version>");
    }

    // ============================================================
    // Common types: records vs classes
    // ============================================================

    @Test
    void apiError_isClassOnSb2_recordOnSb3() {
        // Envelope mode emits ApiError; force it on for both.
        List<GeneratedUnit> sb2Units = generateMvc("jpa", false /* openapi yaml */, /* envelope */ true);
        String sb2ApiError = readUnit(sb2Units, "src/main/java/com/example/shop/exception/ApiError.java");
        assertThat(sb2ApiError)
                .contains("public final class ApiError")
                .contains("public String getCode()")
                .contains("public Instant getTimestamp()")
                .doesNotContain("public record ApiError");

        List<GeneratedUnit> sb3Units = generateMvcSb3Envelope();
        String sb3ApiError = readUnit(sb3Units, "src/main/java/com/example/shop/exception/ApiError.java");
        assertThat(sb3ApiError)
                .contains("public record ApiError(")
                .doesNotContain("public final class ApiError");
    }

    // ============================================================
    // DDD on SB2 — commands + events become classes
    // ============================================================

    @Test
    void ddd_sb2_commandsAndEventsAreClassesNotRecords() {
        List<GeneratedUnit> units = generateDddSb2();

        String createCmd = readUnit(units, "src/main/java/com/example/shop/application/customer/command/CreateCustomerCommand.java");
        assertThat(createCmd)
                .contains("public final class CreateCustomerCommand")
                .doesNotContain("public record CreateCustomerCommand");

        String updateCmd = readUnit(units, "src/main/java/com/example/shop/application/customer/command/UpdateCustomerCommand.java");
        assertThat(updateCmd)
                .contains("public final class UpdateCustomerCommand")
                .doesNotContain("public record UpdateCustomerCommand");

        String createdEvent = readUnit(units, "src/main/java/com/example/shop/domain/customer/event/CustomerCreatedEvent.java");
        assertThat(createdEvent)
                .contains("public final class CustomerCreatedEvent implements DomainEvent")
                .contains("public Instant occurredAt()")
                .doesNotContain("public record CustomerCreatedEvent");
    }

    @Test
    void ddd_sb2_applicationServiceCallsGetters_notRecordAccessors() {
        List<GeneratedUnit> units = generateDddSb2();

        String appService = readUnit(units, "src/main/java/com/example/shop/application/customer/CustomerApplicationService.java");
        assertThat(appService)
                .as("SB2 must use getter-style command.getEmail() since Java 8 has no records")
                .contains("command.getEmail()")
                .doesNotContain("command.email()");
    }

    // ============================================================
    // Security on SB2
    // ============================================================

    @Test
    void security_sb2_extendsWebSecurityConfigurerAdapter() {
        List<GeneratedUnit> units = generateMvcSb2WithJwt();

        String securityConfig = readUnit(units, "src/main/java/com/example/shop/security/SecurityConfig.java");
        assertThat(securityConfig)
                .as("SB2 SecurityConfig must use the Spring Security 5 base class")
                .contains("extends WebSecurityConfigurerAdapter")
                .contains("protected void configure(HttpSecurity http)")
                .doesNotContain("SecurityFilterChain");
    }

    @Test
    void security_sb3_usesSecurityFilterChainBean() {
        List<GeneratedUnit> units = generateMvcSb3WithJwt();

        String securityConfig = readUnit(units, "src/main/java/com/example/shop/security/SecurityConfig.java");
        assertThat(securityConfig)
                .as("SB3 SecurityConfig must use the modern lambda DSL")
                .contains("public SecurityFilterChain securityFilterChain(")
                .doesNotContain("WebSecurityConfigurerAdapter");
    }

    @Test
    void security_jwtFilter_usesEeNamespace() {
        List<GeneratedUnit> sb2Units = generateMvcSb2WithJwt();
        String sb2Filter = readUnit(sb2Units, "src/main/java/com/example/shop/security/JwtAuthenticationFilter.java");
        assertThat(sb2Filter)
                .contains("import javax.servlet.")
                .doesNotContain("import jakarta.servlet.");

        List<GeneratedUnit> sb3Units = generateMvcSb3WithJwt();
        String sb3Filter = readUnit(sb3Units, "src/main/java/com/example/shop/security/JwtAuthenticationFilter.java");
        assertThat(sb3Filter)
                .contains("import jakarta.servlet.")
                .doesNotContain("import javax.servlet.");
    }

    // ============================================================
    // Cross-validation rejections at config load
    // ============================================================

    @Test
    void java8_plusSpringBoot3_rejected() {
        assertThatThrownBy(() -> minimalGen("8", "3.3.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Spring Boot 2.x");
    }

    @Test
    void java17_plusSpringBoot2_isAccepted() {
        UmabootConfig.Generation gen = minimalGen("17", "2.7.18");
        assertThat(gen.springBootMajor()).isEqualTo(2);
    }

    @Test
    void sb2_plusRecordDtos_rejected() {
        assertThatThrownBy(() ->
                new UmabootConfig.Generation(
                        "mvc", "jpa", "com.example.app", "shop", "com.example",
                        "2.7.18", "11", true,
                        UmabootConfig.OpenApiOptions.defaults(),
                        UmabootConfig.InjectionOptions.defaults(),
                        UmabootConfig.ValidationOptions.defaults(),
                        new UmabootConfig.DtoOptions("record", "separate"),
                        UmabootConfig.ExceptionOptions.defaults(),
                        UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                        UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                        UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                        UmabootConfig.PaginationOptions.defaults(), UmabootConfig.SecurityOptions.defaults(),
                        null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dto.style: record");
    }

    @Test
    void sb2_plusProblemDetail_rejected() {
        assertThatThrownBy(() ->
                new UmabootConfig.Generation(
                        "mvc", "jpa", "com.example.app", "shop", "com.example",
                        "2.7.18", "11", true,
                        UmabootConfig.OpenApiOptions.defaults(),
                        UmabootConfig.InjectionOptions.defaults(),
                        UmabootConfig.ValidationOptions.defaults(),
                        UmabootConfig.DtoOptions.defaults(),
                        new UmabootConfig.ExceptionOptions("problemdetail"),
                        UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                        UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                        UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                        UmabootConfig.PaginationOptions.defaults(), UmabootConfig.SecurityOptions.defaults(),
                        null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exception.style: problemdetail");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static List<GeneratedUnit> generateMvc(String persistence, boolean openApiAnnotation) {
        return generateMvc(persistence, openApiAnnotation, false);
    }

    private static List<GeneratedUnit> generateMvc(String persistence, boolean openApiAnnotation, boolean envelope) {
        GeneratorContext ctx = sb2Ctx("mvc", persistence, openApiAnnotation, envelope, "none");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateMvcSb3() {
        GeneratorContext ctx = sb3Ctx("mvc", "jpa", false, false, "none");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateMvcSb3Envelope() {
        GeneratorContext ctx = sb3Ctx("mvc", "jpa", false, true, "none");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateMvcSb2WithJwt() {
        GeneratorContext ctx = sb2Ctx("mvc", "jpa", false, true, "jwt");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateMvcSb3WithJwt() {
        GeneratorContext ctx = sb3Ctx("mvc", "jpa", false, true, "jwt");
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static List<GeneratedUnit> generateDddSb2() {
        GeneratorContext ctx = sb2Ctx("ddd", "jpa", false, true, "none");
        return new DddGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    /** Build a SB2-flavored context. */
    private static GeneratorContext sb2Ctx(String architecture, String persistence,
                                           boolean openApiAnnotation, boolean envelope, String securityStyle) {
        UmabootConfig.SecurityOptions security = "none".equals(securityStyle)
                ? UmabootConfig.SecurityOptions.defaults()
                : new UmabootConfig.SecurityOptions(
                        securityStyle,
                        List.of(new UmabootConfig.UserCredentials("admin", "admin", List.of("ADMIN", "USER"))),
                        new UmabootConfig.JwtOptions(
                                "test-secret-of-at-least-thirty-two-characters",
                                60, "Authorization", "Bearer "));
        return new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "2.7.18", "11", true,
                architecture, persistence, "xml", false,
                openApiAnnotation ? "annotation" : "yaml",
                "constructor", "jakarta", "class", "separate",
                envelope ? "envelope" : "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                "offset", security,
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null, "", null, "maven");
    }

    /** SB3-flavored context for parity comparisons. */
    private static GeneratorContext sb3Ctx(String architecture, String persistence,
                                           boolean openApiAnnotation, boolean envelope, String securityStyle) {
        UmabootConfig.SecurityOptions security = "none".equals(securityStyle)
                ? UmabootConfig.SecurityOptions.defaults()
                : new UmabootConfig.SecurityOptions(
                        securityStyle,
                        List.of(new UmabootConfig.UserCredentials("admin", "admin", List.of("ADMIN", "USER"))),
                        new UmabootConfig.JwtOptions(
                                "test-secret-of-at-least-thirty-two-characters",
                                60, "Authorization", "Bearer "));
        return new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                "3.3.5", "17", true,
                architecture, persistence, "xml", false,
                openApiAnnotation ? "annotation" : "yaml",
                "constructor", "jakarta", "class", "separate",
                envelope ? "envelope" : "problemdetail",
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                "offset", security,
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null, "", null, "maven");
    }

    /** Bare-minimum Generation factory used by cross-validation tests. */
    private static UmabootConfig.Generation minimalGen(String javaVersion, String springBootVersion) {
        return new UmabootConfig.Generation(
                "mvc", "jpa", "com.example.app", "shop", "com.example",
                springBootVersion, javaVersion, true,
                UmabootConfig.OpenApiOptions.defaults(),
                UmabootConfig.InjectionOptions.defaults(),
                UmabootConfig.ValidationOptions.defaults(),
                UmabootConfig.DtoOptions.defaults(),
                new UmabootConfig.ExceptionOptions("envelope"),
                UmabootConfig.AuditOptions.defaults(), UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(), UmabootConfig.CiOptions.defaults(),
                UmabootConfig.LoggingOptions.defaults(), UmabootConfig.TestOptions.defaults(),
                UmabootConfig.PaginationOptions.defaults(), UmabootConfig.SecurityOptions.defaults(),
                null, null, null, null, null, null, null, null, null);
    }

    private static SchemaModel schema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "int8", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        TableModel customers = new TableModel("customers", "public", "",
                List.of(id, email), List.of("id"),
                List.of(), List.of(), false);
        return new SchemaModel("public", List.of(customers));
    }

    private static void assertHasFile(List<GeneratedUnit> units, String path) {
        assertThat(units).extracting(GeneratedUnit::relativePath).contains(path);
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(u -> u.relativePath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("File not generated: " + path
                        + "\nGenerated paths:\n  "
                        + String.join("\n  ", units.stream().map(GeneratedUnit::relativePath).sorted().toList())))
                .content();
    }
}
