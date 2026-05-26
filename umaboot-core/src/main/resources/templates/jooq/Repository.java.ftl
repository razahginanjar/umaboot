package ${basePackage}.repository;

import ${basePackage}.entity.${entityName};
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static ${basePackage}.jooq.Tables.${table.name?upper_case};

/**
 * jOOQ-backed repository for {@code ${table.name}}.
 *
 * <p>Depends on {@code ${basePackage}.jooq.Tables} which is produced by the
 * jOOQ codegen plugin during {@code mvn compile}. If the import above fails
 * to resolve, run {@code mvn jooq-codegen:generate} or simply
 * {@code mvn compile} first.</p>
 */
@Repository
@Transactional
public class ${entityName}Repository {

    private final DSLContext dsl;

    public ${entityName}Repository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ${entityName} save(${entityName} pojo) {
        // Upsert: insert if PK is null, otherwise update.
        if (pojo.get${idField?cap_first}() == null) {
            ${idType} generatedId = dsl.insertInto(${table.name?upper_case})
                    .set(dsl.newRecord(${table.name?upper_case}, pojo))
                    .returning(${table.name?upper_case}.${idColumn?upper_case})
                    .fetchOne()
                    .get(${table.name?upper_case}.${idColumn?upper_case});
            pojo.set${idField?cap_first}(generatedId);
        } else {
            dsl.update(${table.name?upper_case})
                    .set(dsl.newRecord(${table.name?upper_case}, pojo))
                    .where(${table.name?upper_case}.${idColumn?upper_case}.eq(pojo.get${idField?cap_first}()))
                    .execute();
        }
        return pojo;
    }

    @Transactional(readOnly = true)
    public Optional<${entityName}> findById(${idType} id) {
        return dsl.selectFrom(${table.name?upper_case})
                .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id))
                .fetchOptionalInto(${entityName}.class);
    }

    @Transactional(readOnly = true)
    public List<${entityName}> findAll(int page, int size) {
        return dsl.selectFrom(${table.name?upper_case})
                .orderBy(${table.name?upper_case}.${idColumn?upper_case})
                .limit(size).offset(page * size)
                .fetchInto(${entityName}.class);
    }

    @Transactional(readOnly = true)
    public long count() {
        return dsl.fetchCount(${table.name?upper_case});
    }

    public int deleteById(${idType} id) {
        return dsl.deleteFrom(${table.name?upper_case})
                .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id))
                .execute();
    }

    @Transactional(readOnly = true)
    public boolean existsById(${idType} id) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(${table.name?upper_case})
                        .where(${table.name?upper_case}.${idColumn?upper_case}.eq(id)));
    }
}
