package com.example.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/*
 * Configures two RestClient beans — one per downstream service.
 *
 * =========================================================================
 * WHY TIMEOUTS ARE NOT OPTIONAL
 * =========================================================================
 *
 * A remote call is not a Java method call. It crosses a network boundary.
 * Without timeouts, the following scenarios block a thread indefinitely:
 *
 * SCENARIO 1 — Connection timeout (connectTimeout):
 *   product-service is down. The TCP handshake never completes.
 *   Without a connection timeout, the calling thread waits forever.
 *   With a connection timeout, the thread is released after N milliseconds
 *   and ProductServiceUnavailableException is thrown.
 *
 * SCENARIO 2 — Read timeout (readTimeout):
 *   product-service accepts the connection but hangs (e.g. waiting for its
 *   own database which is slow). The response never arrives.
 *   Without a read timeout, the calling thread waits forever.
 *   With a read timeout, the thread is released after N milliseconds
 *   and ProductServiceUnavailableException is thrown.
 *
 * WHY THREAD EXHAUSTION MATTERS:
 *   Spring Boot's embedded Tomcat has a fixed thread pool (default: 200 threads).
 *   If product-service is slow and every incoming request to order-service
 *   blocks waiting for product-service, all 200 threads fill up.
 *   New requests to order-service queue up and eventually time out too.
 *   One slow downstream service has taken down the entire order-service.
 *   This is called a cascading failure. Timeouts prevent it.
 *
 * =========================================================================
 * WHY TWO SEPARATE BEANS
 * =========================================================================
 *
 * Each downstream service has its own base URL and may have different
 * timeout requirements in the future. Separate beans make this explicit
 * and allow independent tuning without touching the other client.
 *
 * @Qualifier("productRestClient") and @Qualifier("inventoryRestClient")
 * are used in ProductClient and InventoryClient to inject the correct bean.
 */
@Configuration
public class RestClientConfig {

    @Value("${services.product-service.url}")
    private String productServiceUrl;

    @Value("${services.inventory-service.url}")
    private String inventoryServiceUrl;

    @Value("${services.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${services.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean("productRestClient")
    public RestClient productRestClient() {
        return RestClient.builder()
                .baseUrl(productServiceUrl)
                .requestFactory(buildRequestFactory())
                .build();
    }

    @Bean("inventoryRestClient")
    public RestClient inventoryRestClient() {
        return RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(buildRequestFactory())
                .build();
    }

    /*
     * SimpleClientHttpRequestFactory wraps the standard JDK HttpURLConnection.
     * It is the simplest way to configure connection and read timeouts without
     * adding extra dependencies (Apache HttpClient, OkHttp).
     *
     * connectTimeout — milliseconds to wait for the TCP connection to be established.
     * readTimeout    — milliseconds to wait for data after the connection is open.
     *
     * Both are read from application.yml so they can be tuned per environment
     * without recompiling. Defaults: 3s connect, 5s read.
     */
    private SimpleClientHttpRequestFactory buildRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }
}
