package ${basePackage}.common;

import ${eeNamespace}.servlet.FilterChain;
import ${eeNamespace}.servlet.ServletException;
import ${eeNamespace}.servlet.http.HttpServletRequest;
import ${eeNamespace}.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that ensures every request carries a correlation id.
 *
 * <p>Reads the {@code X-Correlation-Id} header (case-insensitive). If absent,
 * generates a fresh UUID. The id is placed into the SLF4J MDC under the key
 * {@code correlationId} for the lifetime of the request — your logback pattern
 * can reference it as {@code %X&#123;correlationId&#125;} — and is echoed back
 * on the response header so callers can link logs to their requests.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
