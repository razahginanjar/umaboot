package ${basePackage}.controller;

<#if paginationCursor>
import ${basePackage}.common.CursorPage;
<#else>
import ${basePackage}.common.PageResponse;
</#if>
import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
import ${basePackage}.service.${entityName}Service;
<#if openApiAnnotation>
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
</#if>
<#if injectLombok>
import lombok.RequiredArgsConstructor;
</#if>
<#if injectAutowired>
import org.springframework.beans.factory.annotation.Autowired;
</#if>
<#if validationJakarta>
import ${eeNamespace}.validation.Valid;
</#if>
<#if paginationOffset>
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
</#if>
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/${entityVar}s")
<#if openApiAnnotation>
@Tag(name = "${entityName}", description = "${entityName} CRUD operations")
</#if>
<#if injectLombok>
@RequiredArgsConstructor
</#if>
public class ${entityName}Controller {

<#if injectAutowired>
    @Autowired
    private ${entityName}Service service;
<#else>
    private final ${entityName}Service service;
</#if>

<#if injectConstructor>
    public ${entityName}Controller(${entityName}Service service) {
        this.service = service;
    }

</#if>
    @PostMapping
    <#if openApiAnnotation>
    @Operation(summary = "Create a new ${entityName}")
    </#if>
    public ResponseEntity<${entityName}ResponseDTO> create(<#if validationJakarta>@Valid </#if>@RequestBody ${entityName}RequestDTO request) {
        ${entityName}ResponseDTO created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Get ${entityName} by id")
    </#if>
    public ResponseEntity<${entityName}ResponseDTO> findById(@PathVariable ${idType} id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping
    <#if openApiAnnotation>
    @Operation(summary = "List ${entityName}s with pagination")
    </#if>
<#if paginationCursor>
    public ResponseEntity<CursorPage<${entityName}ResponseDTO>> findAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.findAll(cursor, limit));
    }
<#else>
    public ResponseEntity<PageResponse<${entityName}ResponseDTO>> findAll(Pageable pageable) {
        Page<${entityName}ResponseDTO> page = service.findAll(pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }
</#if>

    @PutMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Update ${entityName}")
    </#if>
    public ResponseEntity<${entityName}ResponseDTO> update(@PathVariable ${idType} id,
                                                           <#if validationJakarta>@Valid </#if>@RequestBody ${entityName}RequestDTO request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Delete ${entityName}")
    </#if>
    public ResponseEntity<Void> delete(@PathVariable ${idType} id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
