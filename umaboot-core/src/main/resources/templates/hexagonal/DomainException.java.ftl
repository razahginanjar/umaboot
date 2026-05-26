package ${basePackage}.domain.exception;

/**
 * Thrown by domain / application services when a {@code ${entityName}} cannot be
 * located by id. Translated to {@code 404} by the inbound web adapter.
 */
public class ${entityName}NotFoundException extends RuntimeException {

    public ${entityName}NotFoundException(${idType} id) {
        super("${entityName} not found: id=" + id);
    }
}
