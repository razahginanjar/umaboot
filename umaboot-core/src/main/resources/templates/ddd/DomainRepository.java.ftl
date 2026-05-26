package ${basePackage}.domain.${aggregatePackage};

import java.util.List;
import java.util.Optional;

/**
 * Domain-level repository for the {@link ${entityName}} aggregate.
 *
 * <p>Implemented in {@code infrastructure.persistence} — but the domain side
 * remains framework-agnostic.</p>
 */
public interface ${entityName}Repository {

    ${entityName} save(${entityName} aggregate);

    Optional<${entityName}> findById(${idType} id);

    List<${entityName}> findAll(int page, int size);

    long count();

    void deleteById(${idType} id);

    boolean existsById(${idType} id);
}
