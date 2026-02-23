package com.example.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
public class StructuredRequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(StructuredRequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startedAt = Instant.now();
        String correlationId = exchange.getAttributeOrDefault(
                CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE, "unknown");
        String method = exchange.getRequest().getMethod() == null
                ? "UNKNOWN" : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        log.info("event=gateway_request_started method={} path={} correlationId={}",
                method, path, correlationId);

        return chain.filter(exchange)
                .doOnError(error -> log.warn(
                        "event=gateway_request_failed method={} path={} correlationId={} exception={} message={}",
                        method, path, correlationId, error.getClass().getSimpleName(), safeMessage(error)))
                .doFinally(signalType -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                    log.info(
                            "event=gateway_request_completed method={} path={} status={} durationMs={} signal={} correlationId={}",
                            method, path, status == null ? 0 : status.value(), durationMs,
                            signalType.name(), correlationId);
                });
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return "none";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
