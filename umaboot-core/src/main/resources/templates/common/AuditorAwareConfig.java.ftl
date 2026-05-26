package ${auditablePackage};

import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * Stub {@link AuditorAware} that supplies the current user identifier for
 * {@code @CreatedBy} / {@code @LastModifiedBy} columns.
 *
 * <p>Generated when Umaboot detects {@code created_by} or {@code updated_by}
 * columns. The default implementation returns {@code "system"}; replace it
 * with one that pulls from {@code SecurityContextHolder}, an HTTP header,
 * or whatever your auth flow provides.</p>
 */
@Configuration
public class AuditorAwareConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        // TODO: replace with real user lookup (e.g. SecurityContextHolder.getContext().getAuthentication().getName())
        return () -> Optional.of("system");
    }
}
