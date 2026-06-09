package io.umaboot.core.overlay;

import io.umaboot.core.diff.DiffEngine;
import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the safe-write plan for overlay mode.
 */
public final class OverlayPlanner {

    public OverlayPlan plan(List<GeneratedUnit> generated, Path outputRoot, GeneratorContext ctx) {
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(outputRoot, "outputRoot");
        Objects.requireNonNull(ctx, "ctx");

        DiffEngine.DiffResult diff = new DiffEngine().diff(generated, outputRoot);
        Set<String> added = new LinkedHashSet<>(diff.added());
        List<GeneratedUnit> newUnits = generated.stream()
                .filter(unit -> added.contains(unit.relativePath()))
                .toList();
        return new OverlayPlan(diff, newUnits, requirements(ctx));
    }

    private static List<String> requirements(GeneratorContext ctx) {
        List<String> requirements = new ArrayList<>();
        String buildFile = ctx.isGradle() ? "build.gradle.kts" : "pom.xml";

        requirements.add("Ensure " + buildFile + " targets Spring Boot "
                + ctx.springBootVersion() + " and Java " + ctx.javaVersion() + ".");
        requirements.add("Add the " + ctx.dbDriver() + " JDBC driver dependency.");

        if (ctx.isJpa()) {
            requirements.add("Add spring-boot-starter-data-jpa.");
        } else if (ctx.isMyBatis()) {
            requirements.add("Add mybatis-spring-boot-starter.");
            if ("xml".equalsIgnoreCase(ctx.mybatisStyle())) {
                requirements.add("Add mybatis.mapper-locations for generated XML mappers.");
            }
            requirements.add("Enable MyBatis underscore-to-camel-case mapping if the project does not already do it.");
        } else if (ctx.isJooq()) {
            requirements.add("Add spring-boot-starter-jooq and jOOQ code generation/build setup if the project does not already provide it.");
        }

        if (!ctx.isValidationNone()) {
            requirements.add("Add spring-boot-starter-validation.");
        }
        if (ctx.useLombok()) {
            String version = ctx.lombokVersion() == null ? "" : " version " + ctx.lombokVersion();
            requirements.add("Add Lombok" + version + " as a dependency and annotation processor.");
        }
        if (ctx.useMapStruct()) {
            String suffix = ctx.useLombok() ? " plus lombok-mapstruct-binding." : ".";
            requirements.add("Add MapStruct dependency and mapstruct-processor annotation processor" + suffix);
        }
        if (ctx.isMigrationFlyway()) {
            String module = ctx.flywayDatabaseModule();
            requirements.add("Add Flyway dependencies"
                    + (module == null || module.isBlank() ? "." : " including " + module + "."));
        }
        if (ctx.logging().isJson()) {
            requirements.add("Add logstash-logback-encoder and merge logback-spring.xml settings.");
        } else if (ctx.logging().correlationId()) {
            requirements.add("Ensure logging configuration keeps MDC correlationId if custom logback config exists.");
        }
        if (ctx.isOpenApiAnnotation()) {
            requirements.add("Add springdoc-openapi dependency for annotation-based OpenAPI.");
        } else if (ctx.isOpenApiYaml()) {
            requirements.add("Expose or serve src/main/resources/openapi.yaml if the project does not already do it.");
        }
        if (ctx.tests().enabled()) {
            requirements.add("Add JUnit 5, Spring Boot test, and Testcontainers test dependencies.");
        }
        if (ctx.isSecurityEnabled()) {
            requirements.add("Merge generated Spring Security dependencies and configuration.");
        }

        return List.copyOf(requirements);
    }
}
