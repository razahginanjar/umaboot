package ${basePackage}.domain.${aggregatePackage};

public class ${entityName}NotFoundException extends RuntimeException {

    public ${entityName}NotFoundException(${idType} id) {
        super("${entityName} not found: id=" + id);
    }
}
