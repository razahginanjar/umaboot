package ${basePackage}.domain.${aggregatePackage}.event;

import ${basePackage}.domain.shared.DomainEvent;

import java.time.Instant;

<#if springBoot3>
public record ${entityName}UpdatedEvent(${idType} aggregateId, Instant occurredAt) implements DomainEvent {

    public ${entityName}UpdatedEvent(${idType} aggregateId) {
        this(aggregateId, Instant.now());
    }
}
<#else>
public final class ${entityName}UpdatedEvent implements DomainEvent {

    private final ${idType} aggregateId;
    private final Instant occurredAt;

    public ${entityName}UpdatedEvent(${idType} aggregateId, Instant occurredAt) {
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
    }

    public ${entityName}UpdatedEvent(${idType} aggregateId) {
        this(aggregateId, Instant.now());
    }

    public ${idType} aggregateId() { return aggregateId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
</#if>
