package ${basePackage}.interfaces.rest;

import ${basePackage}.application.${aggregatePackage}.${entityName}ApplicationService;
import ${basePackage}.application.${aggregatePackage}.command.Create${entityName}Command;
import ${basePackage}.application.${aggregatePackage}.command.Update${entityName}Command;
import ${basePackage}.common.PageResponse;
import ${basePackage}.domain.${aggregatePackage}.${entityName};
import ${basePackage}.domain.${aggregatePackage}.${entityName}NotFoundException;
import ${basePackage}.interfaces.rest.dto.Create${entityName}Request;
import ${basePackage}.interfaces.rest.dto.Update${entityName}Request;
import ${basePackage}.interfaces.rest.dto.${entityName}Response;
import ${basePackage}.interfaces.rest.mapper.${entityName}WebMapper;
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
@Tag(name = "${entityName}", description = "${entityName} aggregate")
</#if>
<#if injectLombok>
@RequiredArgsConstructor
</#if>
public class ${entityName}Controller {

<#if injectAutowired>
    @Autowired
    private ${entityName}ApplicationService service;

    @Autowired
    private ${entityName}WebMapper mapper;
<#else>
    private final ${entityName}ApplicationService service;
    private final ${entityName}WebMapper mapper;
</#if>

<#if injectConstructor>
    public ${entityName}Controller(${entityName}ApplicationService service, ${entityName}WebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

</#if>
    @PostMapping
    <#if openApiAnnotation>
    @Operation(summary = "Create a new ${entityName}")
    </#if>
    public ResponseEntity<${entityName}Response> create(<#if validationJakarta>@Valid </#if>@RequestBody Create${entityName}Request request) {
        Create${entityName}Command command = mapper.toCreateCommand(request);
        ${entityName} aggregate = service.create(command);
        ${entityName}Response body = mapper.toResponse(aggregate);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Get ${entityName} by id")
    </#if>
    public ResponseEntity<${entityName}Response> findById(@PathVariable ${idType} id) {
        ${entityName}Response body = service.findById(id)
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
        List<${entityName}Response> content = service.findAll(page, size).stream()
                .map(mapper::toResponse)
                .toList();
        long total = service.count();
        return ResponseEntity.ok(PageResponse.of(content, page, size, total));
    }

    @PutMapping("/{id}")
    <#if openApiAnnotation>
    @Operation(summary = "Update ${entityName}")
    </#if>
    public ResponseEntity<${entityName}Response> update(@PathVariable ${idType} id,
                                                        <#if validationJakarta>@Valid </#if>@RequestBody Update${entityName}Request request) {
        Update${entityName}Command command = mapper.toUpdateCommand(request);
        return ResponseEntity.ok(mapper.toResponse(service.update(id, command)));
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
