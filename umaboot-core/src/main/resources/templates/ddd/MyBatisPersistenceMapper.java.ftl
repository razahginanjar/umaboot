package ${basePackage}.infrastructure.persistence;

import ${basePackage}.domain.${aggregatePackage}.${entityName};
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link ${entityName}PersistenceModel} and the
 * domain aggregate {@link ${entityName}}.
 *
 * <p>Uses reflection for v0.5 because the aggregate exposes only domain
 * methods (not setters) for non-id fields. Replace with explicit
 * state-restoration methods or unpacked records as the model matures.</p>
 */
@Component
public class ${entityName}PersistenceMapper {

    public ${entityName}PersistenceModel toPersistence(${entityName} aggregate) {
        if (aggregate == null) return null;
        ${entityName}PersistenceModel pm = new ${entityName}PersistenceModel();
<#list fields as f>
        pm.set${f.fieldName?cap_first}(aggregate.get${f.fieldName?cap_first}());
</#list>
        return pm;
    }

    public ${entityName} toAggregate(${entityName}PersistenceModel pm) {
        if (pm == null) return null;
        ${entityName} aggregate = new ${entityName}();
        applyState(aggregate, pm);
        return aggregate;
    }

    private void applyState(${entityName} aggregate, ${entityName}PersistenceModel pm) {
        try {
            for (java.lang.reflect.Field f : ${entityName}.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (f.getName().equals("domainEvents")) continue;
                java.lang.reflect.Field pmField = ${entityName}PersistenceModel.class.getDeclaredField(f.getName());
                pmField.setAccessible(true);
                f.setAccessible(true);
                f.set(aggregate, pmField.get(pm));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to rehydrate ${entityName} from PersistenceModel", e);
        }
    }
}
