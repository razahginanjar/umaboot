package ${basePackage}.domain.shared;

import java.time.Instant;

/**
 * Marker interface for domain events. Implementations should be immutable
 * records carrying the minimum context needed by event handlers.
 */
public interface DomainEvent {

    /** Wall-clock instant the event was recorded. */
    Instant occurredAt();
}
