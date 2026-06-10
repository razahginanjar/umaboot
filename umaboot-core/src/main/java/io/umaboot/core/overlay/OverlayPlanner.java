package io.umaboot.core.overlay;

import io.umaboot.core.config.ApplicationConfigMerger;
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

        BuildFileDependencyPlanner.Plan dependencies =
                new BuildFileDependencyPlanner().plan(outputRoot, ctx);
        ApplicationConfigMerger.Plan applicationConfig =
                ApplicationConfigMerger.plan(outputRoot, ctx);
        List<GeneratedUnit> previewUnits = new ArrayList<>(generated);
        previewUnits.addAll(dependencies.patchUnits());
        previewUnits.addAll(applicationConfig.patchUnits());

        DiffEngine.DiffResult diff = new DiffEngine().diff(previewUnits, outputRoot);
        Set<String> added = new LinkedHashSet<>(diff.added());
        List<GeneratedUnit> newUnits = generated.stream()
                .filter(unit -> added.contains(unit.relativePath()))
                .toList();
        return new OverlayPlan(diff, newUnits, previewUnits, requirements(ctx), dependencies, applicationConfig);
    }

    private static List<String> requirements(GeneratorContext ctx) {
        List<String> requirements = new ArrayList<>();
        String buildFile = ctx.isGradle() ? "build.gradle.kts" : "pom.xml";

        requirements.add("Ensure " + buildFile + " targets Spring Boot "
                + ctx.springBootVersion() + " and Java " + ctx.javaVersion() + ".");

        if (ctx.isMyBatis()) {
            requirements.add("Review generated MyBatis application config additions in Preview / Merge.");
        } else if (ctx.isJooq()) {
            requirements.add("Add spring-boot-starter-jooq and jOOQ code generation/build setup if the project does not already provide it.");
        }

        if (ctx.logging().isJson()) {
            requirements.add("Merge generated logback-spring.xml settings if the project already has custom logging.");
        } else if (ctx.logging().correlationId()) {
            requirements.add("Ensure logging configuration keeps MDC correlationId if custom logback config exists.");
        }
        if (ctx.isOpenApiYaml()) {
            requirements.add("Expose or serve src/main/resources/openapi.yaml if the project does not already do it.");
        }
        if (ctx.isSecurityEnabled()) {
            requirements.add("Merge generated Spring Security configuration.");
        }

        return List.copyOf(requirements);
    }
}
