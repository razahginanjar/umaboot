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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaVersionCompatibilityRenderTest {

    @Test
    void java8GeneratedSourcesAvoidPostJava8Apis() {
        String sources = javaSources(java8Units());

        assertThat(sources)
                .doesNotContain(".isBlank()")
                .doesNotContainPattern("(?<!Collectors)\\.toList\\(\\)")
                .doesNotContain("List.of()")
                .doesNotContain("List.copyOf(")
                .contains("trim().isEmpty()")
                .contains("collect(Collectors.toList())")
                .contains("new ArrayList<>(domainEvents)")
                .contains("Collections.emptyList()");
    }

    @Test
    void java11GeneratedSourcesKeepJava11ApisButAvoidStreamToList() {
        String sources = javaSources(List.of(
                generate("mvc", "jpa", "2.7.18", "11", true, true, "cursor"),
                generate("hexagonal", "jpa", "2.7.18", "11", false, false, "offset")
        ));

        assertThat(sources)
                .doesNotContainPattern("(?<!Collectors)\\.toList\\(\\)")
                .doesNotContain("Collections.emptyList()")
                .doesNotContain("trim().isEmpty()")
                .contains("id.isBlank()")
                .contains("cursor.isBlank()")
                .contains("List.of()")
                .contains("collect(Collectors.toList())");
    }

    @Test
    void java17GeneratedSourcesKeepModernApis() {
        String sources = javaSources(List.of(
                generate("mvc", "jpa", "3.3.5", "17", true, true, "cursor"),
                generate("hexagonal", "jpa", "3.3.5", "17", false, false, "offset")
        ));

        assertThat(sources)
                .doesNotContain("Collections.emptyList()")
                .doesNotContain("trim().isEmpty()")
                .contains("id.isBlank()")
                .contains("cursor.isBlank()")
                .contains("List.of()")
                .containsPattern("(?<!Collectors)\\.toList\\(\\)");
    }

    @Test
    void java18AliasRendersAsJava8() {
        String pom = readUnit(generate("mvc", "jpa", "2.7.18", "1.8", false, false, "offset"), "pom.xml");

        assertThat(pom)
                .contains("<java.version>8</java.version>")
                .doesNotContain("<java.version>1.8</java.version>");
    }

    private static List<List<GeneratedUnit>> java8Units() {
        List<List<GeneratedUnit>> runs = new ArrayList<>();
        runs.add(generate("mvc", "jpa", "2.7.18", "8", true, true, "cursor"));
        runs.add(generate("mvc", "mybatis", "2.7.18", "8", false, false, "offset"));
        runs.add(generate("mvc", "jooq", "2.7.18", "8", false, false, "offset"));
        runs.add(generate("hexagonal", "jpa", "2.7.18", "8", false, false, "offset"));
        runs.add(generate("hexagonal", "mybatis", "2.7.18", "8", false, false, "offset"));
        runs.add(generate("ddd", "jpa", "2.7.18", "8", false, false, "offset"));
        runs.add(generate("ddd", "mybatis", "2.7.18", "8", false, false, "offset"));
        return runs;
    }

    private static List<GeneratedUnit> generate(String architecture, String persistence,
                                                String springBootVersion, String javaVersion,
                                                boolean loggingCorrelationId,
                                                boolean jwtSecurity,
                                                String paginationStyle) {
        UmabootConfig.LoggingOptions logging =
                new UmabootConfig.LoggingOptions("plain", loggingCorrelationId);
        UmabootConfig.SecurityOptions security = jwtSecurity
                ? new UmabootConfig.SecurityOptions(
                        "jwt",
                        List.of(new UmabootConfig.UserCredentials("admin", "admin",
                                List.of("ADMIN", "USER"))),
                        new UmabootConfig.JwtOptions(
                                "test-secret-of-at-least-thirty-two-characters",
                                60, "Authorization", "Bearer "))
                : UmabootConfig.SecurityOptions.defaults();
        String exceptionStyle = springBootVersion.startsWith("2.") ? "envelope" : "problemdetail";

        GeneratorContext ctx = new GeneratorContext(
                "com.example.shop", "shop-api", "com.example",
                springBootVersion, javaVersion, true,
                architecture, persistence, "xml", false, "none", "constructor",
                "jakarta", "class", "separate", exceptionStyle,
                UmabootConfig.AuditOptions.defaults(),
                UmabootConfig.SoftDeleteOptions.defaults(),
                UmabootConfig.DockerOptions.defaults(),
                UmabootConfig.CiOptions.defaults(),
                logging,
                UmabootConfig.TestOptions.defaults(),
                paginationStyle,
                security,
                UmabootConfig.DddOptions.defaults(),
                false, "postgres", null, null, "", null, "maven");

        if ("hexagonal".equals(architecture)) {
            return new HexagonalGenerator(new TemplateEngine(null), ctx).generate(schema());
        }
        if ("ddd".equals(architecture)) {
            return new DddGenerator(new TemplateEngine(null), ctx).generate(schema());
        }
        return new MvcGenerator(new TemplateEngine(null), ctx).generate(schema());
    }

    private static String javaSources(List<List<GeneratedUnit>> runs) {
        StringBuilder out = new StringBuilder();
        for (List<GeneratedUnit> units : runs) {
            for (GeneratedUnit unit : units) {
                if (unit.relativePath().endsWith(".java")) {
                    out.append(unit.content()).append('\n');
                }
            }
        }
        return out.toString();
    }

    private static SchemaModel schema() {
        ColumnModel id = new ColumnModel("id", Types.BIGINT, "bigint", 19, 0,
                false, true, true, null, "", List.of());
        ColumnModel email = new ColumnModel("email", Types.VARCHAR, "varchar", 255, 0,
                false, false, false, null, "", List.of());
        TableModel customers = new TableModel("customers", "public", "",
                List.of(id, email), List.of("id"), List.of(), List.of(), false);
        return new SchemaModel("public", List.of(customers));
    }

    private static String readUnit(List<GeneratedUnit> units, String path) {
        return units.stream()
                .filter(unit -> unit.relativePath().equals(path))
                .findFirst()
                .map(GeneratedUnit::content)
                .orElseThrow(() -> new AssertionError("Missing: " + path));
    }
}
