package ${basePackage}.infrastructure.persistence;

import ${basePackage}.domain.${aggregatePackage}.${entityName};
import ${basePackage}.domain.${aggregatePackage}.${entityName}Repository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static ${basePackage}.jooq.Tables.${table.name?upper_case};

/**
 * jOOQ-backed implementation of the domain {@link ${entityName}Repository}.
 *
 * <p><b>Aggregate → Record</b> conversion uses {@code dsl.newRecord(TABLE, aggregate)}
 * — jOOQ reads via the aggregate's getters, which the {@code @Getter} Lombok
 * annotation (or the explicit getters when Lombok is disabled) provides.</p>
 *
 * <p><b>Record → Aggregate</b> conversion uses the aggregate's <em>reconstruction
 * constructor</em> (the all-fields constructor on {@link ${entityName}}). This
 * deliberately bypasses {@link ${entityName}#create}: rebuilding an aggregate
 * from a database row should NOT record a {@code ${entityName}CreatedEvent}.</p>
 *
 * <p>Depends on {@code ${basePackage}.jooq.Tables} which is produced by the
 * {@code jooq-codegen-maven} plugin during {@code mvn compile}.</p>
 */
@Component
@Transactional
public class ${entityName}RepositoryImpl implements ${entityName}Repository {

    private final DSLContext dsl;

    public ${entityName}RepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ${entityName} save(${entityName} aggregate) {
        if (aggregate.get${idField?cap_first}() == null) {
            ${idType} generatedId = dsl.insertInto(${table.name?upper_case})
                    .set(dsl.newRecord(${table.name?upper_case}, aggregate))
                    .returning(${table.name?upper_case}.${idColumn?upper_case})
                    .fetchOne()
                    .get(${table.name?upper_case}.${idColumn?upper_case});
            aggregate.set${idField?cap_first}(generatedId);
        } else {
            dsl.update(${table.name?upper_case})
                    .set(dsl.newRecord(${table.name?upper_case}, aggregate))
                    .where(${table.name?upper_case}.${idColumn?upper_case}.eq(aggregate.get${idField?cap_first}()))
                    .execute();
        }
        return aggregate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<${entityName}> findById(${idType} id) {
        return dsl.selectFrom(${table.name?upper_case})
                .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id))
                .fetchOptional(this::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<${entityName}> findAll(int page, int size) {
        return dsl.selectFrom(${table.name?upper_case})
                .orderBy(${table.name?upper_case}.${idColumn?upper_case})
                .limit(size).offset(page * size)
                .fetch(this::toAggregate);
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

    /**
     * Reconstruct the aggregate from a jOOQ record. Uses the all-fields
     * reconstruction constructor — does not record domain events.
     */
    private ${entityName} toAggregate(Record record) {
        return new ${entityName}(
<#list domainFields as f>
                record.get(${table.name?upper_case}.${f.columnName?upper_case})<#if f_has_next>,</#if>
</#list>
        );
    }
}
