package ${basePackage}.application.${aggregatePackage};

import ${basePackage}.application.${aggregatePackage}.command.Create${entityName}Command;
import ${basePackage}.application.${aggregatePackage}.command.Update${entityName}Command;
<#if manualAudit>
import ${basePackage}.common.AuditProvider;
</#if>
import ${basePackage}.domain.${aggregatePackage}.${entityName};
import ${basePackage}.domain.${aggregatePackage}.${entityName}NotFoundException;
import ${basePackage}.domain.${aggregatePackage}.${entityName}Repository;
import ${basePackage}.domain.shared.DomainEvent;
<#if injectLombok>
import lombok.RequiredArgsConstructor;
</#if>
<#if injectAutowired>
import org.springframework.beans.factory.annotation.Autowired;
</#if>
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
<#if injectLombok>
@RequiredArgsConstructor
</#if>
public class ${entityName}ApplicationService {

<#if injectAutowired>
    @Autowired
    private ${entityName}Repository repository;

    @Autowired
    private ApplicationEventPublisher events;
<#if manualAudit>

    @Autowired
    private AuditProvider auditProvider;
</#if>
<#else>
    private final ${entityName}Repository repository;
    private final ApplicationEventPublisher events;
<#if manualAudit>
    private final AuditProvider auditProvider;
</#if>
</#if>

<#if injectConstructor>
    public ${entityName}ApplicationService(${entityName}Repository repository,
                                           ApplicationEventPublisher events<#if manualAudit>,
                                           AuditProvider auditProvider</#if>) {
        this.repository = repository;
        this.events = events;
<#if manualAudit>
        this.auditProvider = auditProvider;
</#if>
    }

</#if>
    public ${entityName} create(Create${entityName}Command command) {
        ${entityName} aggregate = ${entityName}.create(<#list requestFields as f><#if springBoot3>command.${f.fieldName}()<#else>command.get${f.fieldName?cap_first}()</#if><#sep>, </#sep></#list>);
<#if manualAudit>
        aggregate.markCreated(<#list auditCreateFields as f>${f.auditValueExpression}<#sep>, </#sep></#list>);
</#if>
        ${entityName} saved = repository.save(aggregate);
        publish(saved.pullDomainEvents());
        return saved;
    }

    public ${entityName} update(${idType} id, Update${entityName}Command command) {
        ${entityName} aggregate = repository.findById(id)
                .orElseThrow(() -> new ${entityName}NotFoundException(id));
        aggregate.updateFrom(<#list requestUpdateFields as f><#if springBoot3>command.${f.fieldName}()<#else>command.get${f.fieldName?cap_first}()</#if><#sep>, </#sep></#list>);
<#if manualAuditOnUpdate>
        aggregate.markUpdated(<#list auditUpdateFields as f>${f.auditValueExpression}<#sep>, </#sep></#list>);
</#if>
        ${entityName} saved = repository.save(aggregate);
        publish(saved.pullDomainEvents());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<${entityName}> findById(${idType} id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<${entityName}> findAll(int page, int size) {
        return repository.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    public void delete(${idType} id) {
        if (!repository.existsById(id)) throw new ${entityName}NotFoundException(id);
        repository.deleteById(id);
    }

    private void publish(List<DomainEvent> drained) {
        for (DomainEvent e : drained) events.publishEvent(e);
    }
}
