package io.umaboot.core.generator.mybatis;

import io.umaboot.core.generator.GeneratedUnit;
import io.umaboot.core.generator.GeneratorContext;
import io.umaboot.core.generator.jpa.EntityView;
import io.umaboot.core.generator.persistence.PersistenceProvider;
import io.umaboot.core.model.SchemaModel;
import io.umaboot.core.model.TableModel;
import io.umaboot.core.template.TemplateEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MyBatis persistence provider.
 *
 * <p>Emits a plain POJO entity (no JPA annotations), a {@code @Mapper} interface
 * with CRUD methods, and either:</p>
 * <ul>
 *   <li><b>style=xml</b> — a {@code resources/mapper/{Entity}Mapper.xml} ResultMap +
 *       SQL fragment file. Mapper interface methods carry no SQL annotations.</li>
 *   <li><b>style=annotation</b> — {@code @Select}, {@code @Insert}, {@code @Update},
 *       {@code @Delete} annotations directly on the Mapper interface methods. No XML.</li>
 * </ul>
 */
public final class MyBatisPersistenceProvider implements PersistenceProvider {

    private final TemplateEngine engine;

    public MyBatisPersistenceProvider(TemplateEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public String id() {
        return "mybatis";
    }

    @Override
    public List<GeneratedUnit> generateForTable(TableModel table, GeneratorContext ctx) {
        Map<String, Object> model = EntityView.build(table, ctx);
        String entityName = (String) model.get("entityName");
        String javaSrc = "src/main/java/" + ctx.basePackagePath();

        List<GeneratedUnit> units = new ArrayList<>();
        // Plain POJO entity (no @Entity annotations)
        units.add(new GeneratedUnit(
                javaSrc + "/entity/" + entityName + ".java",
                engine.render("mybatis/Entity.java.ftl", model)));

        if (ctx.myBatisAnnotation()) {
            units.add(new GeneratedUnit(
                    javaSrc + "/mapper/" + entityName + "Mapper.java",
                    engine.render("mybatis/MapperAnnotation.java.ftl", model)));
        } else {
            units.add(new GeneratedUnit(
                    javaSrc + "/mapper/" + entityName + "Mapper.java",
                    engine.render("mybatis/MapperInterface.java.ftl", model)));
            units.add(new GeneratedUnit(
                    "src/main/resources/mapper/" + entityName + "Mapper.xml",
                    engine.render("mybatis/MapperXml.xml.ftl", model)));
        }
        return units;
    }

    @Override
    public List<GeneratedUnit> projectExtras(SchemaModel schema, GeneratorContext ctx) {
        // No project-wide MyBatis extras for v0.2 — application.yml carries the mapper-locations.
        return List.of();
    }
}
