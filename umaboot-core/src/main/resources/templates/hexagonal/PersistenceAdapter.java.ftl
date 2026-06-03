package ${basePackage}.adapter.out.persistence;

import ${basePackage}.adapter.out.persistence.mapper.${entityName}PersistenceMapper;
import ${basePackage}.domain.model.${entityName};
import ${basePackage}.domain.port.${entityName}Repository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
<#if !javaSupportsStreamToList>
import java.util.stream.Collectors;
</#if>

/**
 * Outbound persistence adapter — implements the domain
 * {@link ${entityName}Repository} port using Spring Data JPA. The domain
 * {@link ${entityName}} is mapped to/from {@link ${entityName}JpaEntity} by
 * {@link ${entityName}PersistenceMapper}.
 */
@Component
public class ${entityName}PersistenceAdapter implements ${entityName}Repository {

    private final ${entityName}JpaRepository jpa;
    private final ${entityName}PersistenceMapper mapper;

    public ${entityName}PersistenceAdapter(${entityName}JpaRepository jpa,
                                           ${entityName}PersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public ${entityName} save(${entityName} entity) {
        ${entityName}JpaEntity persisted = jpa.save(mapper.toJpa(entity));
        return mapper.toDomain(persisted);
    }

    @Override
    public Optional<${entityName}> findById(${idType} id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<${entityName}> findAll(int page, int size) {
        return jpa.findAll(PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
                <#if javaSupportsStreamToList>.toList()<#else>.collect(Collectors.toList())</#if>;
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
