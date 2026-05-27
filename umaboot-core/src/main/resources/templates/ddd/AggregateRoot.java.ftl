package ${basePackage}.domain.${aggregatePackage};

import ${basePackage}.domain.${aggregatePackage}.event.${entityName}CreatedEvent;
import ${basePackage}.domain.${aggregatePackage}.event.${entityName}UpdatedEvent;
import ${basePackage}.domain.shared.DomainEvent;
<#list imports as imp>
import ${imp};
</#list>
import java.util.*;
<#if useLombok>
import lombok.Getter;
</#if>

/**
 * Aggregate root for the {@code ${aggregatePackage}} bounded context.
 *
 * <p>This class encapsulates state and exposes domain methods. State changes
 * happen through domain methods (e.g. {@link #updateFrom}) that publish
 * {@link DomainEvent}s; the application service is responsible for collecting
 * and dispatching the events after persisting the aggregate.</p>
 *
 * <p>This class is part of the domain layer — no JPA, no Spring annotations.</p>
 */
<#if useLombok>
@Getter
</#if>
public class ${entityName} {

<#list fields as f>
    private ${f.javaType} ${f.fieldName};
</#list>

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /** Reconstructed-from-storage constructor. Use {@link #create} for new aggregates. */
    public ${entityName}() {}

    /**
     * Reconstruction constructor for the persistence layer (jOOQ etc.).
     * Copies all field values verbatim and does NOT record any domain events,
     * because the aggregate isn't being created — it's being rehydrated from
     * a row that already exists.
     */
    public ${entityName}(<#list fields as f>${f.javaType} ${f.fieldName}<#sep>, </#sep></#list>) {
<#list fields as f>
        this.${f.fieldName} = ${f.fieldName};
</#list>
    }

    /** Factory: create a brand-new aggregate. Records a {@code ${entityName}CreatedEvent}. */
    public static ${entityName} create(<#list fields as f><#if !f.primaryKey>${f.javaType} ${f.fieldName}<#sep>, </#sep></#if></#list>) {
        ${entityName} aggregate = new ${entityName}();
<#list fields as f><#if !f.primaryKey>
        aggregate.${f.fieldName} = ${f.fieldName};
</#if></#list>
        aggregate.recordEvent(new ${entityName}CreatedEvent(aggregate.${idField}));
        return aggregate;
    }

    /** Domain method: applies an update and records {@code ${entityName}UpdatedEvent}. */
    public void updateFrom(<#list fields as f><#if !f.primaryKey>${f.javaType} ${f.fieldName}<#sep>, </#sep></#if></#list>) {
<#list fields as f><#if !f.primaryKey>
        this.${f.fieldName} = ${f.fieldName};
</#if></#list>
        recordEvent(new ${entityName}UpdatedEvent(this.${idField}));
    }

    /** Drain the recorded events for the application service to publish. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }

    protected void recordEvent(DomainEvent event) {
        domainEvents.add(event);
    }

<#if !useLombok>
<#list fields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
</#list>
</#if>

    // Setter for primary-key reassignment after persistence (package-private mapper use).
    void set${idField?cap_first}(${idType} ${idField}) { this.${idField} = ${idField}; }
}
