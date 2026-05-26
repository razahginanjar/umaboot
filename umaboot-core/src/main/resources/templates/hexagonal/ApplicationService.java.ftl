package ${basePackage}.application.service;

import ${basePackage}.application.usecase.${entityName}UseCase;
import ${basePackage}.domain.exception.${entityName}NotFoundException;
import ${basePackage}.domain.model.${entityName};
import ${basePackage}.domain.port.${entityName}Repository;
<#if injectLombok>
import lombok.RequiredArgsConstructor;
</#if>
<#if injectAutowired>
import org.springframework.beans.factory.annotation.Autowired;
</#if>
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
<#if injectLombok>
@RequiredArgsConstructor
</#if>
public class ${entityName}ApplicationService implements ${entityName}UseCase {

<#if injectAutowired>
    @Autowired
    private ${entityName}Repository repository;
<#else>
    private final ${entityName}Repository repository;
</#if>

<#if injectConstructor>
    public ${entityName}ApplicationService(${entityName}Repository repository) {
        this.repository = repository;
    }

</#if>
    @Override
    public ${entityName} create(${entityName} command) {
        return repository.save(command);
    }

    @Override
    public ${entityName} update(${idType} id, ${entityName} command) {
        ${entityName} existing = repository.findById(id)
                .orElseThrow(() -> new ${entityName}NotFoundException(id));
        // Domain-level merge: copy mutable fields from command onto existing.
        // Replace this with proper domain methods (e.g. existing.rename(command.getName()))
        // as the model grows.
<#list fields as f>
    <#if !f.primaryKey>
        existing.set${f.fieldName?cap_first}(command.get${f.fieldName?cap_first}());
    </#if>
</#list>
        return repository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<${entityName}> findById(${idType} id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<${entityName}> findAll(int page, int size) {
        return repository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    @Override
    public void delete(${idType} id) {
        if (!repository.existsById(id)) throw new ${entityName}NotFoundException(id);
        repository.deleteById(id);
    }
}
