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
<#if paginationCursor>
import ${basePackage}.common.CursorPage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
<#if !javaSupportsStreamToList>
import java.util.stream.Collectors;
</#if>
<#else>
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
</#if>
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
<#if paginationCursor>
    public CursorPage<${entityName}ResponseDTO> findAll(String cursor, int limit) {
        if (limit <= 0) limit = 20;
        if (limit > 100) limit = 100;
        ${idType} cursorId = decodeCursor(cursor);
        // Fetch one extra to detect if there's a next page.
        Slice<${entityName}> slice = repository.findByIdGreaterThanOrderByIdAsc(
                cursorId, PageRequest.of(0, limit + 1));
        List<${entityName}> rows = slice.getContent();
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);
        String nextCursor = (hasMore && !rows.isEmpty())
                ? encodeCursor(rows.get(rows.size() - 1).get${idField?cap_first}())
                : null;
        return CursorPage.of(rows.stream().map(mapper::toResponse)<#if javaSupportsStreamToList>.toList()<#else>.collect(Collectors.toList())</#if>, nextCursor, limit);
    }

    private static ${idType} decodeCursor(String cursor) {
        if (cursor == null || <#if javaSupportsStringIsBlank>cursor.isBlank()<#else>cursor.trim().isEmpty()</#if>) {
            // First page — start before the smallest possible id.
<#if idType == "Long" || idType == "long">
            return 0L;
<#elseif idType == "Integer" || idType == "int">
            return 0;
<#elseif idType == "String">
            return "";
<#else>
            return null;
</#if>
        }
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
<#if idType == "Long" || idType == "long">
        return Long.parseLong(raw);
<#elseif idType == "Integer" || idType == "int">
        return Integer.parseInt(raw);
<#elseif idType == "String">
        return raw;
<#elseif idType == "UUID">
        return java.util.UUID.fromString(raw);
<#else>
        return raw;
</#if>
    }

    private static String encodeCursor(${idType} id) {
        if (id == null) return null;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }
<#else>
    public Page<${entityName}ResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }
</#if>

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
