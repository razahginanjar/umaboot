package io.umaboot.core.generator.jooq;

import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.jpa.EntityView;
import io.umaboot.core.generator.persistence.PersistenceProvider;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * jOOQ persistence provider (basic v0.2).
 *
 * <p>Unlike JPA / MyBatis, jOOQ generates its own Java sources from the
 * database schema at build time via {@code jooq-codegen-maven}. This provider
 * therefore only emits:</p>
 * <ul>
 *   <li>A thin {@code {Entity}Repository} facade that wraps {@code DSLContext}
 *       calls — keeps the application code free of {@code DSLContext.select()}
 *       boilerplate while still allowing direct jOOQ usage.</li>
 *   <li>A {@code DslConfig} bean once for the project (via {@link #projectExtras}).</li>
 * </ul>
 *
 * <p>The Spring Boot starter ({@code spring-boot-starter-jooq}) provides the
 * {@code DSLContext} bean automatically; the generated POM declares the
 * codegen plugin so that {@code mvn compile} populates the {@code generated}
 * package referenced by the facade.</p>
 */
public final class JooqPersistenceProvider implements PersistenceProvider {

    private final TemplateEngine engine;

    public JooqPersistenceProvider(TemplateEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public String id() {
        return "jooq";
    }

    @Override
    public List<GeneratedUnit> generateForTable(TableModel table, GeneratorContext ctx) {
        Map<String, Object> model = EntityView.build(table, ctx);
        String entityName = (String) model.get("entityName");
        String javaSrc = "src/main/java/" + ctx.basePackagePath();

        return List.of(
                new GeneratedUnit(
                        javaSrc + "/entity/" + entityName + ".java",
                        engine.render("jooq/Entity.java.ftl", model)),
                new GeneratedUnit(
                        javaSrc + "/repository/" + entityName + "Repository.java",
                        engine.render("jooq/Repository.java.ftl", model)));
    }

    @Override
    public List<GeneratedUnit> projectExtras(SchemaModel schema, GeneratorContext ctx) {
        // No project-wide extras for v0.2; the POM template handles codegen wiring.
        return List.of();
    }
}
