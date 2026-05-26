package ${basePackage}.domain.port;

import ${basePackage}.domain.model.${entityName};

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for {@link ${entityName}} persistence.
 *
 * <p>This interface is part of the domain layer and must not import any
 * persistence-framework or Spring types. Implementations live in
 * {@code adapter.out.persistence}.</p>
 */
public interface ${entityName}Repository {

    ${entityName} save(${entityName} entity);

    Optional<${entityName}> findById(${idType} id);

    List<${entityName}> findAll(int page, int size);

    long count();

    void deleteById(${idType} id);

    boolean existsById(${idType} id);
}
