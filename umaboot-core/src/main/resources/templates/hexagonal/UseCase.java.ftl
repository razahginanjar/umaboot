package ${basePackage}.application.usecase;

import ${basePackage}.domain.model.${entityName};

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for {@code ${entityName}} CRUD use cases.
 *
 * <p>Defined as a single interface (rather than one per operation) for v0.3.
 * Splitting into per-operation interfaces (e.g. {@code Create${entityName}UseCase},
 * {@code Update${entityName}UseCase}) is a mechanical refactor — the
 * {@code ${entityName}ApplicationService} continues to implement them all.</p>
 */
public interface ${entityName}UseCase {

    ${entityName} create(${entityName} command);

    ${entityName} update(${idType} id, ${entityName} command);

    Optional<${entityName}> findById(${idType} id);

    List<${entityName}> findAll(int page, int size);

    long count();

    void delete(${idType} id);
}
