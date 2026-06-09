package ${basePackage}.interfaces.rest.mapper;

import ${basePackage}.application.${aggregatePackage}.command.Create${entityName}Command;
import ${basePackage}.application.${aggregatePackage}.command.Update${entityName}Command;
import ${basePackage}.domain.${aggregatePackage}.${entityName};
import ${basePackage}.interfaces.rest.dto.Create${entityName}Request;
import ${basePackage}.interfaces.rest.dto.Update${entityName}Request;
import ${basePackage}.interfaces.rest.dto.${entityName}Response;
import org.springframework.stereotype.Component;

@Component
public class ${entityName}WebMapper {

    public Create${entityName}Command toCreateCommand(Create${entityName}Request request) {
        if (request == null) return null;
        return new Create${entityName}Command(
<#list requestFields as f>
                request.get${f.fieldName?cap_first}()<#sep>,</#sep>
</#list>
        );
    }

    public Update${entityName}Command toUpdateCommand(Update${entityName}Request request) {
        if (request == null) return null;
        return new Update${entityName}Command(
<#list requestUpdateFields as f>
                request.get${f.fieldName?cap_first}()<#sep>,</#sep>
</#list>
        );
    }

    public ${entityName}Response toResponse(${entityName} aggregate) {
        if (aggregate == null) return null;
        ${entityName}Response response = new ${entityName}Response();
<#list responseFields as f>
        response.set${f.fieldName?cap_first}(aggregate.get${f.fieldName?cap_first}());
</#list>
        return response;
    }
}
