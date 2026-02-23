package com.example.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;
    private final int requestsPerWindow;
    private final long windowMillis;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public OrderRateLimitGlobalFilter(
            ObjectMapper objectMapper,
            @Value("${gateway.rate-limit.orders.requests-per-window:10}") int requestsPerWindow,
            @Value("${gateway.rate-limit.orders.window:60s}") java.time.Duration window) {
        this.objectMapper = objectMapper;
        this.requestsPerWindow = requestsPerWindow;
        this.windowMillis = window.toMillis();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isCreateOrderRequest(exchange)) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(exchange);
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.windowStartedAt.get() >= windowMillis) {
                return new WindowCounter(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        int currentCount = counter.count.get();
        long resetAt = counter.windowStartedAt.get() + windowMillis;
        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(requestsPerWindow));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, requestsPerWindow - currentCount)));
        exchange.getResponse().getHeaders().set("X-RateLimit-Reset", String.valueOf(resetAt));

        if (currentCount <= requestsPerWindow) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After",
                String.valueOf(Math.max(1, windowMillis / 1000)));

        String correlationId = exchange.getAttributeOrDefault(
                CorrelationIdGlobalFilter.CORRELATION_ID_ATTRIBUTE, "unknown");
        byte[] body = toJsonBytes(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 429,
                "error", "Too Many Requests",
                "code", "ORDER_RATE_LIMIT_EXCEEDED",
                "message", "Too many order creation requests. Please try again later.",
                "path", exchange.getRequest().getURI().getPath(),
                "correlationId", correlationId
        ));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isCreateOrderRequest(ServerWebExchange exchange) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/api/orders".equals(normalizePath(exchange.getRequest().getURI().getPath()));
    }

    private String normalizePath(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown-client";
    }

    private byte[] toJsonBytes(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException ignored) {
            return "{\"status\":429,\"code\":\"ORDER_RATE_LIMIT_EXCEEDED\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static final class WindowCounter {
        private final AtomicLong windowStartedAt;
        private final AtomicInteger count = new AtomicInteger(1);

        private WindowCounter(long startedAt) {
            this.windowStartedAt = new AtomicLong(startedAt);
        }
    }
}
