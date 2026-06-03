package ${basePackage}.service.impl;

import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
import ${basePackage}.entity.${entityName};
import ${basePackage}.exception.${entityName}NotFoundException;
import ${basePackage}.mapper.${entityName}DtoMapper;
import ${basePackage}.repository.${entityName}Repository;
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
    private ${entityName}Repository repository;

    @Autowired
    private ${entityName}DtoMapper mapper;
<#else>
    private final ${entityName}Repository repository;
    private final ${entityName}DtoMapper mapper;
</#if>

<#if injectConstructor>
    public ${entityName}ServiceImpl(${entityName}Repository repository, ${entityName}DtoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

</#if>
    @Override
    public ${entityName}ResponseDTO create(${entityName}RequestDTO request) {
        ${entityName} entity = mapper.toEntity(request);
        ${entityName} saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ${entityName}ResponseDTO findById(${idType} id) {
        ${entityName} entity = repository.findById(id)
                .orElseThrow(() -> new ${entityName}NotFoundException(id));
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<${entityName}ResponseDTO> findAll(Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        List<${entityName}> rows = repository.findAll(page, size);
        long total = repository.count();
        List<${entityName}ResponseDTO> content = rows.stream().map(mapper::toResponse)<#if javaSupportsStreamToList>.toList()<#else>.collect(Collectors.toList())</#if>;
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public ${entityName}ResponseDTO update(${idType} id, ${entityName}RequestDTO request) {
        ${entityName} existing = repository.findById(id)
                .orElseThrow(() -> new ${entityName}NotFoundException(id));
        mapper.updateEntity(existing, request);
        ${entityName} saved = repository.save(existing);
        return mapper.toResponse(saved);
    }

    @Override
    public void delete(${idType} id) {
        if (!repository.existsById(id)) {
            throw new ${entityName}NotFoundException(id);
        }
        repository.deleteById(id);
    }
}
