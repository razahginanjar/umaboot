package ${basePackage}.domain.${aggregatePackage}.event;

import ${basePackage}.domain.shared.DomainEvent;

import java.time.Instant;

<#if springBoot3>
public record ${entityName}CreatedEvent(${idType} aggregateId, Instant occurredAt) implements DomainEvent {

    public ${entityName}CreatedEvent(${idType} aggregateId) {
        this(aggregateId, Instant.now());
    }
}
<#else>
public final class ${entityName}CreatedEvent implements DomainEvent {

    private final ${idType} aggregateId;
    private final Instant occurredAt;

    public ${entityName}CreatedEvent(${idType} aggregateId, Instant occurredAt) {
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
    }

    public ${entityName}CreatedEvent(${idType} aggregateId) {
        this(aggregateId, Instant.now());
    }

    public ${idType} aggregateId() { return aggregateId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
</#if>
