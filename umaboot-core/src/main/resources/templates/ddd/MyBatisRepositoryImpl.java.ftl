package ${basePackage}.infrastructure.persistence;

import ${basePackage}.domain.${aggregatePackage}.${entityName};
import ${basePackage}.domain.${aggregatePackage}.${entityName}Repository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-backed implementation of the domain {@link ${entityName}Repository}.
 * Uses {@link ${entityName}PersistenceMapper} to translate between the
 * persistence model and the domain aggregate.
 */
@Component
public class ${entityName}RepositoryImpl implements ${entityName}Repository {

    private final ${entityName}MyBatisMapper sqlMapper;
    private final ${entityName}PersistenceMapper mapper;

    public ${entityName}RepositoryImpl(${entityName}MyBatisMapper sqlMapper,
                                       ${entityName}PersistenceMapper mapper) {
        this.sqlMapper = sqlMapper;
        this.mapper = mapper;
    }

    @Override
    public ${entityName} save(${entityName} aggregate) {
        ${entityName}PersistenceModel pm = mapper.toPersistence(aggregate);
        if (pm.get${idField?cap_first}() == null) {
            sqlMapper.insert(pm);
        } else {
            sqlMapper.update(pm);
        }
        return mapper.toAggregate(pm);
    }

    @Override
    public Optional<${entityName}> findById(${idType} id) {
        return Optional.ofNullable(sqlMapper.findById(id)).map(mapper::toAggregate);
    }

    @Override
    public List<${entityName}> findAll(int page, int size) {
        int offset = page * size;
        return sqlMapper.findAll(offset, size).stream().map(mapper::toAggregate).toList();
    }

    @Override
    public long count() {
        return sqlMapper.count();
    }

    @Override
    public void deleteById(${idType} id) {
        sqlMapper.deleteById(id);
    }

    @Override
    public boolean existsById(${idType} id) {
        return sqlMapper.existsById(id) > 0;
    }
}
