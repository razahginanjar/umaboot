package io.umaboot.core.generator.jpa;

import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.persistence.PersistenceProvider;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JPA persistence provider — emits {@code entity/{Entity}.java} and
 * {@code repository/{Entity}Repository.java} per table.
 *
 * <p>Architecture-specific path remapping (e.g. Hexagonal moving entities into
 * {@code adapter/out/persistence/}) is the renderer's responsibility. This
 * provider always emits the canonical layered paths; the renderer rewrites
 * them as needed.</p>
 */
public final class JpaPersistenceProvider implements PersistenceProvider {

    private final TemplateEngine engine;

    public JpaPersistenceProvider(TemplateEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public String id() {
        return "jpa";
    }

    @Override
    public List<GeneratedUnit> generateForTable(TableModel table, GeneratorContext ctx) {
        Map<String, Object> model = EntityView.build(table, ctx);
        String entityName = (String) model.get("entityName");
        String javaSrc = "src/main/java/" + ctx.basePackagePath();

        List<GeneratedUnit> units = new ArrayList<>();
        units.add(new GeneratedUnit(
                javaSrc + "/entity/" + entityName + ".java",
                engine.render("mvc-jpa/Entity.java.ftl", model)));
        units.add(new GeneratedUnit(
                javaSrc + "/repository/" + entityName + "Repository.java",
                engine.render("mvc-jpa/Repository.java.ftl", model)));
        return units;
    }
}
