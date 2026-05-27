package ${basePackage}.adapter.out.persistence;

import ${basePackage}.domain.model.${entityName};
import ${basePackage}.domain.port.${entityName}Repository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static ${basePackage}.jooq.Tables.${table.name?upper_case};

/**
 * Outbound persistence adapter — implements the domain
 * {@link ${entityName}Repository} port using jOOQ. The domain
 * {@link ${entityName}} is mapped to/from jOOQ-generated table records via
 * reflective {@code dsl.newRecord} / {@code .fetchInto} based on matching
 * field names — no per-entity mapper needed.
 *
 * <p>Depends on {@code ${basePackage}.jooq.Tables} which is produced by the
 * {@code jooq-codegen-maven} plugin during {@code mvn compile}. If the import
 * above fails to resolve in your IDE, run {@code mvn compile} first.</p>
 */
@Component
@Transactional
public class ${entityName}PersistenceAdapter implements ${entityName}Repository {

    private final DSLContext dsl;

    public ${entityName}PersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ${entityName} save(${entityName} entity) {
        if (entity.get${idField?cap_first}() == null) {
            ${idType} generatedId = dsl.insertInto(${table.name?upper_case})
                    .set(dsl.newRecord(${table.name?upper_case}, entity))
                    .returning(${table.name?upper_case}.${idColumn?upper_case})
                    .fetchOne()
                    .get(${table.name?upper_case}.${idColumn?upper_case});
            entity.set${idField?cap_first}(generatedId);
        } else {
            dsl.update(${table.name?upper_case})
                    .set(dsl.newRecord(${table.name?upper_case}, entity))
                    .where(${table.name?upper_case}.${idColumn?upper_case}.eq(entity.get${idField?cap_first}()))
                    .execute();
        }
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<${entityName}> findById(${idType} id) {
        return dsl.selectFrom(${table.name?upper_case})
                .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id))
                .fetchOptionalInto(${entityName}.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<${entityName}> findAll(int page, int size) {
        return dsl.selectFrom(${table.name?upper_case})
                .orderBy(${table.name?upper_case}.${idColumn?upper_case})
                .limit(size).offset(page * size)
                .fetchInto(${entityName}.class);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return dsl.fetchCount(${table.name?upper_case});
    }

    @Override
    public void deleteById(${idType} id) {
        dsl.deleteFrom(${table.name?upper_case})
                .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id))
                .execute();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(${idType} id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(${table.name?upper_case})
                        .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id)));
    }
}
