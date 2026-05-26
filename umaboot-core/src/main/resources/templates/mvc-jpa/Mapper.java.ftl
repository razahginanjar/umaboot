package ${basePackage}.mapper;

import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
import ${basePackage}.entity.${entityName};
import org.springframework.stereotype.Component;

/**
 * Manual mapper between {@link ${entityName}} and its DTOs.
 *
 * <p>v0.1 generates a hand-written mapper to keep the generated project free of
 * an annotation processor; switch to MapStruct by enabling
 * {@code generation.jpa.useMapStruct=true} in {@code umaboot.yaml}.</p>
 */
@Component
public class ${entityName}DtoMapper {

    public ${entityName} toEntity(${entityName}RequestDTO dto) {
        if (dto == null) return null;
        ${entityName} entity = new ${entityName}();
<#list fields as f>
    <#if !f.primaryKey || !f.autoIncrement>
        entity.set${f.fieldName?cap_first}(dto.get${f.fieldName?cap_first}());
    </#if>
</#list>
        return entity;
    }

    public ${entityName}ResponseDTO toResponse(${entityName} entity) {
        if (entity == null) return null;
        ${entityName}ResponseDTO dto = new ${entityName}ResponseDTO();
<#list fields as f>
        dto.set${f.fieldName?cap_first}(entity.get${f.fieldName?cap_first}());
</#list>
        return dto;
    }

    public void updateEntity(${entityName} entity, ${entityName}RequestDTO dto) {
        if (entity == null || dto == null) return;
<#list fields as f>
    <#if !f.primaryKey>
        entity.set${f.fieldName?cap_first}(dto.get${f.fieldName?cap_first}());
    </#if>
</#list>
    }
}
