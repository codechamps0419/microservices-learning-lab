package com.example.order.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/*
 * Servlet filter that manages the X-Correlation-ID header for every request.
 *
 * Runs once per request (OncePerRequestFilter) before any controller logic.
 *
 * WHAT IT DOES:
 *   1. Reads X-Correlation-ID from the incoming request header.
 *   2. If absent, generates a new UUID as the correlation ID.
 *   3. Stores the correlation ID in MDC under the key "correlationId".
 *   4. Writes the correlation ID back onto the response header so the
 *      caller can trace their request through logs.
 *   5. Clears MDC after the request completes (thread pool safety).
 *
 * WHY MDC?
 * MDC (Mapped Diagnostic Context) is a thread-local map provided by SLF4J.
 * Any log statement on the same thread automatically includes %X{correlationId}
 * from the log pattern in application.yml — without passing correlationId
 * as a parameter through every method call.
 *
 * WHY CLEAR MDC IN finally?
 * Spring Boot uses a thread pool (Tomcat). Threads are reused across requests.
 * If MDC is not cleared, the next request on the same thread inherits the
 * previous request's correlationId — a subtle and hard-to-debug bug.
 * The finally block guarantees cleanup even if the request throws.
 *
 * WHY @Order(1)?
 * This filter must run before any other filter that might log something.
 * @Order(1) gives it the highest precedence among custom filters.
 *
 * PROPAGATION TO DOWNSTREAM SERVICES:
 * ProductClient and InventoryClient read MDC.get("correlationId") and
 * forward it as X-Correlation-ID on every outgoing HTTP request.
 * This creates a single traceable ID across all three services' logs.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY               = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        // Generate a new ID if the caller did not provide one.
        // This ensures every request has a correlation ID regardless of the caller.
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Put into MDC — now available in all log statements on this thread
        // via %X{correlationId} in the log pattern.
        MDC.put(MDC_KEY, correlationId);

        // Echo back on the response so the caller can reference it when
        // reporting issues or correlating their own logs.
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // MUST clear MDC — threads are reused from the pool.
            // Without this, the next request on this thread inherits this correlationId.
            MDC.remove(MDC_KEY);
        }
    }
}
