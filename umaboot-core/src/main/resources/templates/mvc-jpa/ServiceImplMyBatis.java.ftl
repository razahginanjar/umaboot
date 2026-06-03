package ${basePackage}.service.impl;

import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
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
<#else>
    private final ${entityName}Mapper sqlMapper;
    private final ${entityName}DtoMapper dtoMapper;
</#if>

<#if injectConstructor>
    public ${entityName}ServiceImpl(${entityName}Mapper sqlMapper, ${entityName}DtoMapper dtoMapper) {
        this.sqlMapper = sqlMapper;
        this.dtoMapper = dtoMapper;
    }

</#if>
    @Override
    public ${entityName}ResponseDTO create(${entityName}RequestDTO request) {
        ${entityName} entity = dtoMapper.toEntity(request);
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
        sqlMapper.update(existing);
        return dtoMapper.toResponse(existing);
    }

    @Override
    public void delete(${idType} id) {
        int rows = sqlMapper.deleteById(id);
        if (rows == 0) throw new ${entityName}NotFoundException(id);
    }
}
