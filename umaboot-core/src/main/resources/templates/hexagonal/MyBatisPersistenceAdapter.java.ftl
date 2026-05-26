package ${basePackage}.adapter.out.persistence;

import ${basePackage}.adapter.out.persistence.mapper.${entityName}PersistenceMapper;
import ${basePackage}.domain.model.${entityName};
import ${basePackage}.domain.port.${entityName}Repository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Outbound persistence adapter — implements the domain
 * {@link ${entityName}Repository} port using MyBatis. The domain
 * {@link ${entityName}} is mapped to/from {@link ${entityName}PersistenceModel} by
 * {@link ${entityName}PersistenceMapper}.
 */
@Component
public class ${entityName}PersistenceAdapter implements ${entityName}Repository {

    private final ${entityName}MyBatisMapper sqlMapper;
    private final ${entityName}PersistenceMapper mapper;

    public ${entityName}PersistenceAdapter(${entityName}MyBatisMapper sqlMapper,
                                           ${entityName}PersistenceMapper mapper) {
        this.sqlMapper = sqlMapper;
        this.mapper = mapper;
    }

    @Override
    public ${entityName} save(${entityName} entity) {
        ${entityName}PersistenceModel persistence = mapper.toPersistence(entity);
        if (persistence.get${idField?cap_first}() == null) {
            sqlMapper.insert(persistence);
        } else {
            sqlMapper.update(persistence);
        }
        return mapper.toDomain(persistence);
    }

    @Override
    public Optional<${entityName}> findById(${idType} id) {
        return Optional.ofNullable(sqlMapper.findById(id)).map(mapper::toDomain);
    }

    @Override
    public List<${entityName}> findAll(int page, int size) {
        int offset = page * size;
        return sqlMapper.findAll(offset, size).stream().map(mapper::toDomain).toList();
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
