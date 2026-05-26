package ${basePackage}.service;

import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
<#if paginationCursor>
import ${basePackage}.common.CursorPage;
<#else>
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
</#if>

public interface ${entityName}Service {

    ${entityName}ResponseDTO create(${entityName}RequestDTO request);

    ${entityName}ResponseDTO findById(${idType} id);

<#if paginationCursor>
    CursorPage<${entityName}ResponseDTO> findAll(String cursor, int limit);
<#else>
    Page<${entityName}ResponseDTO> findAll(Pageable pageable);
</#if>

    ${entityName}ResponseDTO update(${idType} id, ${entityName}RequestDTO request);

    void delete(${idType} id);
}
