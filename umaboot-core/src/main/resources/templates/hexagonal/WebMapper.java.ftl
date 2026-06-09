package ${basePackage}.adapter.in.web.mapper;

import ${basePackage}.adapter.in.web.dto.${entityName}Request;
import ${basePackage}.adapter.in.web.dto.${entityName}Response;
import ${basePackage}.domain.model.${entityName};
import org.springframework.stereotype.Component;

/**
 * Maps between web DTOs and the domain {@link ${entityName}} model.
 * Handwritten in v0.3 to keep the generated project free of an annotation
 * processor; switch to MapStruct (separate option) by re-running with
 * {@code generation.jpa.useMapStruct=true}.
 */
@Component
public class ${entityName}WebMapper {

    public ${entityName} toDomain(${entityName}Request request) {
        if (request == null) return null;
        ${entityName} domain = new ${entityName}();
<#list requestFields as f>
        domain.set${f.fieldName?cap_first}(request.get${f.fieldName?cap_first}());
</#list>
        return domain;
    }

    public ${entityName}Response toResponse(${entityName} domain) {
        if (domain == null) return null;
        ${entityName}Response response = new ${entityName}Response();
<#list responseFields as f>
        response.set${f.fieldName?cap_first}(domain.get${f.fieldName?cap_first}());
</#list>
        return response;
    }
}
