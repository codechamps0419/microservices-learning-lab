package com.example.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRateLimitGlobalFilterTest {

    @Test
    void limitsOnlyPostOrdersAfterConfiguredCount() {
        OrderRateLimitGlobalFilter filter = new OrderRateLimitGlobalFilter(
                new ObjectMapper(), 2, Duration.ofMinutes(1));
        AtomicInteger forwarded = new AtomicInteger();
        GatewayFilterChain chain = exchange -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(orderExchange(), chain)).verifyComplete();
        StepVerifier.create(filter.filter(orderExchange(), chain)).verifyComplete();
        MockServerWebExchange rejected = orderExchange();
        StepVerifier.create(filter.filter(rejected, chain)).verifyComplete();

        assertThat(forwarded.get()).isEqualTo(2);
        assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private MockServerWebExchange orderExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/orders")
                        .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345))
                        .build());
    }
}
