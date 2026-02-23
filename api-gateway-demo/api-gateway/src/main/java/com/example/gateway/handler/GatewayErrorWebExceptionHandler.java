package com.example.gateway.handler;

import com.example.gateway.filter.CorrelationIdGlobalFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        ErrorDetails details = classify(throwable);
        exchange.getResponse().setStatusCode(details.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getAttributeOrDefault(
                CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE, "unknown");
        byte[] bytes = serialize(Map.of(
                "timestamp", Instant.now().toString(),
                "status", details.status().value(),
                "error", details.status().getReasonPhrase(),
                "code", details.code(),
                "message", details.message(),
                "path", exchange.getRequest().getURI().getPath(),
                "correlationId", correlationId
        ));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private ErrorDetails classify(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root instanceof ConnectException
                || root instanceof ConnectTimeoutException
                || root instanceof PrematureCloseException
                || throwable instanceof WebClientRequestException) {
            return new ErrorDetails(HttpStatus.SERVICE_UNAVAILABLE,
                    "DOWNSTREAM_SERVICE_UNAVAILABLE",
                    "The requested downstream service is currently unavailable.");
        }
        if (root instanceof TimeoutException || root instanceof ReadTimeoutException) {
            return new ErrorDetails(HttpStatus.GATEWAY_TIMEOUT,
                    "DOWNSTREAM_SERVICE_TIMEOUT",
                    "The downstream service did not respond within the configured timeout.");
        }
        if (throwable instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status != null) {
                return new ErrorDetails(status, "GATEWAY_REQUEST_FAILED",
                        responseStatusException.getReason() == null
                                ? status.getReasonPhrase() : responseStatusException.getReason());
            }
        }
        return new ErrorDetails(HttpStatus.INTERNAL_SERVER_ERROR,
                "GATEWAY_INTERNAL_ERROR",
                "The gateway could not process the request.");
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private byte[] serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ignored) {
            return "{\"status\":500,\"code\":\"GATEWAY_INTERNAL_ERROR\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private record ErrorDetails(HttpStatus status, String code, String message) {
    }
}
