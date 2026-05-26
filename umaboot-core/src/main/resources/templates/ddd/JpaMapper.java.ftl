package ${basePackage}.infrastructure.persistence;

import ${basePackage}.domain.${aggregatePackage}.${entityName};
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link ${entityName}JpaEntity} persistence model and the
 * domain aggregate {@link ${entityName}}.
 *
 * <p>For value-object-rich aggregates this mapper is the place to handle
 * unpacking — e.g. mapping a single domain {@code Email} value object to a
 * single {@code String} column on the JPA entity.</p>
 */
@Component
public class ${entityName}JpaMapper {

    public ${entityName}JpaEntity toJpa(${entityName} aggregate) {
        if (aggregate == null) return null;
        ${entityName}JpaEntity jpa = new ${entityName}JpaEntity();
<#list fields as f>
        jpa.set${f.fieldName?cap_first}(aggregate.get${f.fieldName?cap_first}());
</#list>
        return jpa;
    }

    public ${entityName} toAggregate(${entityName}JpaEntity jpa) {
        if (jpa == null) return null;
        // Use the no-event reconstruction constructor — events should only be
        // recorded via domain methods, not on rehydration.
        ${entityName} aggregate = new ${entityName}();
        applyState(aggregate, jpa);
        return aggregate;
    }

    private void applyState(${entityName} aggregate, ${entityName}JpaEntity jpa) {
        // Reflective-style direct assignment via reflection or package-friend.
        // For v0.4 we use a helper hook: the aggregate exposes a package-private
        // setter for the id only; other fields are set via the domain factory.
        // A real-world implementation would expose state-restoration methods
        // (or use a dedicated unpacked record) — flagged as a TODO here.
        try {
            for (java.lang.reflect.Field f : ${entityName}.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (f.getName().equals("domainEvents")) continue;
                java.lang.reflect.Field jpaField = ${entityName}JpaEntity.class.getDeclaredField(f.getName());
                jpaField.setAccessible(true);
                f.setAccessible(true);
                f.set(aggregate, jpaField.get(jpa));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to rehydrate ${entityName} from JpaEntity", e);
        }
    }
}
