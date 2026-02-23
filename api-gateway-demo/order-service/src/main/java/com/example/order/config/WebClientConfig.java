package com.example.order.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/*
 * Configures the WebClient bean for inventory-service.
 *
 * =========================================================================
 * WHY WebClient NEEDS A DIFFERENT TIMEOUT SETUP THAN RestClient
 * =========================================================================
 *
 * RestClient uses SimpleClientHttpRequestFactory (JDK HttpURLConnection).
 * Timeouts are set directly: factory.setConnectTimeout() / setReadTimeout().
 *
 * WebClient uses Reactor Netty as its HTTP engine by default.
 * Timeouts must be configured on the Netty HttpClient:
 *   - Connection timeout: HttpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS)
 *   - Read timeout:       HttpClient.doOnConnected() + ReadTimeoutHandler
 *
 * The Netty HttpClient is then wrapped in ReactorClientHttpConnector
 * and passed to WebClient.builder().clientConnector().
 *
 * =========================================================================
 * REACTOR NETTY
 * =========================================================================
 *
 * Reactor Netty is a non-blocking HTTP client built on top of Netty
 * (an async event-driven network framework). It uses an event loop
 * instead of one thread per connection — this is why WebClient can
 * handle high concurrency with far fewer threads than RestClient.
 *
 * spring-boot-starter-webflux pulls in Reactor Netty automatically.
 */
@Configuration
public class WebClientConfig {

    @Value("${services.inventory-service.url}")
    private String inventoryServiceUrl;

    @Value("${services.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${services.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean("inventoryWebClientBean")
    public WebClient inventoryWebClient() {
        // Configure Reactor Netty HttpClient with timeouts.
        HttpClient httpClient = HttpClient.create()
                // Connection timeout — how long to wait for TCP handshake.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                // Read timeout — how long to wait for data after connection is open.
                // doOnConnected runs once the connection is established.
                // ReadTimeoutHandler fires if no data arrives within readTimeoutMs.
                .doOnConnected(conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                );

        return WebClient.builder()
                .baseUrl(inventoryServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
