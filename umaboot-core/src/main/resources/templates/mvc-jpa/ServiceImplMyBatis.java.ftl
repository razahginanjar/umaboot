package ${basePackage}.service.impl;

import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
<#if manualAudit>
import ${basePackage}.common.AuditProvider;
</#if>
import ${basePackage}.entity.${entityName};
import ${basePackage}.exception.${entityName}NotFoundException;
import ${basePackage}.mapper.${entityName}DtoMapper;
import ${basePackage}.mapper.${entityName}Mapper;
import ${basePackage}.service.${entityName}Service;
<#if injectLombok>
import lombok.RequiredArgsConstructor;
</#if>
<#if injectAutowired>
import org.springframework.beans.factory.annotation.Autowired;
</#if>
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
<#if !javaSupportsStreamToList>
import java.util.stream.Collectors;
</#if>

@Service
@Transactional
<#if injectLombok>
@RequiredArgsConstructor
</#if>
public class ${entityName}ServiceImpl implements ${entityName}Service {

<#if injectAutowired>
    @Autowired
    private ${entityName}Mapper sqlMapper;

    @Autowired
    private ${entityName}DtoMapper dtoMapper;
<#if manualAudit>

    @Autowired
    private AuditProvider auditProvider;
</#if>
<#else>
    private final ${entityName}Mapper sqlMapper;
    private final ${entityName}DtoMapper dtoMapper;
<#if manualAudit>
    private final AuditProvider auditProvider;
</#if>
</#if>

<#if injectConstructor>
    public ${entityName}ServiceImpl(${entityName}Mapper sqlMapper,
                                    ${entityName}DtoMapper dtoMapper<#if manualAudit>,
                                    AuditProvider auditProvider</#if>) {
        this.sqlMapper = sqlMapper;
        this.dtoMapper = dtoMapper;
<#if manualAudit>
        this.auditProvider = auditProvider;
</#if>
    }

</#if>
    @Override
    public ${entityName}ResponseDTO create(${entityName}RequestDTO request) {
        ${entityName} entity = dtoMapper.toEntity(request);
<#if manualAudit>
        markCreated(entity);
</#if>
        sqlMapper.insert(entity);
        return dtoMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ${entityName}ResponseDTO findById(${idType} id) {
        ${entityName} entity = sqlMapper.findById(id);
        if (entity == null) throw new ${entityName}NotFoundException(id);
        return dtoMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<${entityName}ResponseDTO> findAll(Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        List<${entityName}> page = sqlMapper.findAll(offset, limit);
        long total = sqlMapper.count();
        List<${entityName}ResponseDTO> content = page.stream().map(dtoMapper::toResponse)<#if javaSupportsStreamToList>.toList()<#else>.collect(Collectors.toList())</#if>;
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public ${entityName}ResponseDTO update(${idType} id, ${entityName}RequestDTO request) {
        ${entityName} existing = sqlMapper.findById(id);
        if (existing == null) throw new ${entityName}NotFoundException(id);
        dtoMapper.updateEntity(existing, request);
<#if manualAuditOnUpdate>
        markUpdated(existing);
</#if>
        sqlMapper.update(existing);
        return dtoMapper.toResponse(existing);
    }

    @Override
    public void delete(${idType} id) {
        int rows = sqlMapper.deleteById(id);
        if (rows == 0) throw new ${entityName}NotFoundException(id);
    }
<#if manualAudit>

    private void markCreated(${entityName} entity) {
<#list auditCreateFields as f>
        entity.set${f.fieldName?cap_first}(${f.auditValueExpression});
</#list>
    }
</#if>
<#if manualAuditOnUpdate>

    private void markUpdated(${entityName} entity) {
<#list auditUpdateFields as f>
        entity.set${f.fieldName?cap_first}(${f.auditValueExpression});
</#list>
    }
</#if>
}
