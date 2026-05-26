package ${basePackage}.infrastructure.persistence;

import ${basePackage}.domain.${aggregatePackage}.${entityName};
import ${basePackage}.domain.${aggregatePackage}.${entityName}Repository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of the domain {@link ${entityName}Repository}.
 * Uses {@link ${entityName}JpaMapper} to translate between the persistence
 * model and the domain aggregate.
 */
@Component
public class ${entityName}RepositoryImpl implements ${entityName}Repository {

    private final ${entityName}JpaRepository jpa;
    private final ${entityName}JpaMapper mapper;

    public ${entityName}RepositoryImpl(${entityName}JpaRepository jpa, ${entityName}JpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public ${entityName} save(${entityName} aggregate) {
        ${entityName}JpaEntity persisted = jpa.save(mapper.toJpa(aggregate));
        return mapper.toAggregate(persisted);
    }

    @Override
    public Optional<${entityName}> findById(${idType} id) {
        return jpa.findById(id).map(mapper::toAggregate);
    }

    @Override
    public List<${entityName}> findAll(int page, int size) {
        return jpa.findAll(PageRequest.of(page, size))
                .stream()
                .map(mapper::toAggregate)
                .toList();
    }

    @Override
    public long count() {
        return jpa.count();
    }

    @Override
    public void deleteById(${idType} id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsById(${idType} id) {
        return jpa.existsById(id);
    }
}
