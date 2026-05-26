package ${basePackage}.adapter.in.web;

import ${basePackage}.adapter.in.web.dto.${entityName}Request;
import ${basePackage}.adapter.in.web.dto.${entityName}Response;
import ${basePackage}.adapter.in.web.mapper.${entityName}WebMapper;
import ${basePackage}.application.usecase.${entityName}UseCase;
import ${basePackage}.common.PageResponse;
import ${basePackage}.domain.exception.${entityName}NotFoundException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private ${entityName}UseCase useCase;

    @Autowired
    private ${entityName}WebMapper mapper;
<#else>
    private final ${entityName}UseCase useCase;
    private final ${entityName}WebMapper mapper;
</#if>

<#if injectConstructor>
    public ${entityName}Controller(${entityName}UseCase useCase, ${entityName}WebMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

</#if>
    @PostMapping
    <#if openApiAnnotation>
    @Operation(summary = "Create a new ${entityName}")
    </#if>
    public ResponseEntity<${entityName}Response> create(<#if validationJakarta>@Valid </#if>@RequestBody ${entityName}Request request) {
        ${entityName}Response created = mapper.toResponse(useCase.create(mapper.toDomain(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Get ${entityName} by id")
    </#if>
    public ResponseEntity<${entityName}Response> findById(@PathVariable ${idType} id) {
        ${entityName}Response body = useCase.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ${entityName}NotFoundException(id));
        return ResponseEntity.ok(body);
    }

    @GetMapping
    <#if openApiAnnotation>
    @Operation(summary = "List ${entityName}s with pagination")
    </#if>
    public ResponseEntity<PageResponse<${entityName}Response>> findAll(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        List<${entityName}Response> content = useCase.findAll(page, size).stream()
                .map(mapper::toResponse)
                .toList();
        long total = useCase.count();
        return ResponseEntity.ok(PageResponse.of(content, page, size, total));
    }

    @PutMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Update ${entityName}")
    </#if>
    public ResponseEntity<${entityName}Response> update(@PathVariable ${idType} id,
                                                        <#if validationJakarta>@Valid </#if>@RequestBody ${entityName}Request request) {
        return ResponseEntity.ok(mapper.toResponse(useCase.update(id, mapper.toDomain(request))));
    }

    @DeleteMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Delete ${entityName}")
    </#if>
    public ResponseEntity<Void> delete(@PathVariable ${idType} id) {
        useCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
