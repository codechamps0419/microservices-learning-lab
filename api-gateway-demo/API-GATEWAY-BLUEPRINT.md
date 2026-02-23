# Project 3 — API Gateway Blueprint

## Java 25 Final Edition

## 1. Project objective

This project adds a dedicated reactive API Gateway in front of the existing Product, Inventory, and Order microservices.

The Gateway is the single external entry point for clients. It handles technical and cross-cutting concerns such as:

- Routing requests to downstream services
- Correlation ID generation and propagation
- Structured request logging
- CORS
- Basic order-creation rate limiting
- Connection and response timeouts
- Standard responses for Gateway-level failures

The Gateway is an infrastructure component. It must not contain Product, Inventory, or Order business logic.

It must not:

- Validate whether a product is active
- Calculate product or order prices
- Reserve or release stock
- Save orders
- Access service databases
- Use JPA repositories
- Import downstream entities
- Replace business validation performed by downstream services

---

## 2. Final technology baseline

| Technology | Version or choice |
|---|---|
| Java | 25 |
| Spring Boot | 3.5.16 |
| Spring Cloud release train | 2025.0.3 |
| Spring Cloud Gateway starter | `spring-cloud-starter-gateway-server-webflux` |
| Lombok | 1.18.46 |
| Build tool | Maven multi-module project |
| Reactive server | Netty |
| Gateway programming model | Spring WebFlux |
| Route discovery | Static routes for this project |
| Rate limiting | In-memory fixed-window limiter for learning |
| Authentication | Not included |
| Service discovery | Not included |
| Persistence in Gateway | Not included |

### Why these versions are used

Java 25 requires a Spring Boot and supporting library baseline that can process Java 25 class files.

The project therefore uses:

```text
Java 25
Spring Boot 3.5.16
Spring Cloud 2025.0.3
Lombok 1.18.46
```

The older dependency:

```xml
<artifactId>spring-cloud-starter-gateway</artifactId>
```

has been replaced with:

```xml
<artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
```

The older Gateway property prefix:

```text
spring.cloud.gateway
```

has also been migrated to:

```text
spring.cloud.gateway.server.webflux
```

---

## 3. Modules and ports

| Module | Port | Responsibility |
|---|---:|---|
| `api-gateway` | 8080 | External entry point, routing, logging, CORS, timeouts and rate limiting |
| `product-service` | 8081 | Product business capability and product data |
| `inventory-service` | 8082 | Inventory business capability and stock data |
| `order-service` | 8083 | Order creation and orchestration |

Final project structure:

```text
api-gateway-demo/
├── pom.xml
├── product-service/
│   └── pom.xml
├── inventory-service/
│   └── pom.xml
├── order-service/
│   └── pom.xml
├── api-gateway/
│   ├── pom.xml
│   └── src/
└── synchronous-communication.postman_collection.json
```

The root project is a Maven aggregator and parent. It produces no JAR.

---

## 4. Correct Maven parent hierarchy

### Root POM identity

The root `pom.xml` must contain:

```xml
<groupId>com.example</groupId>
<artifactId>api-gateway-demo</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
```

### Root modules

```xml
<modules>
    <module>product-service</module>
    <module>inventory-service</module>
    <module>order-service</module>
    <module>api-gateway</module>
</modules>
```

Each module must appear only once.

### Child-module parent declaration

Every child POM must inherit from the root aggregator:

```xml
<parent>
    <groupId>com.example</groupId>
    <artifactId>api-gateway-demo</artifactId>
    <version>1.0.0</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

The child services must not use `api-gateway` as their parent because `api-gateway` is an executable JAR module, not a parent POM.

### Shared version management

The root POM owns shared versions:

```xml
<properties>
    <java.version>25</java.version>
    <maven.compiler.release>25</maven.compiler.release>
    <lombok.version>1.18.46</lombok.version>
    <spring-cloud.version>2025.0.3</spring-cloud.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
```

Spring Cloud dependency versions are imported once:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Child POMs should not repeat the Java version unless a module intentionally targets a different release.

---

## 5. Gateway module dependency design

The Gateway module should contain only infrastructure dependencies.

Core dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```

Actuator:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Testing:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

The Gateway must not add:

- Spring Data JPA
- PostgreSQL driver
- Flyway
- Product, Inventory, or Order repositories
- Downstream service entity dependencies
- `spring-boot-starter-web`

Spring Cloud Gateway Server WebFlux runs reactively and uses Netty rather than Spring MVC and Tomcat.

---

## 6. Request flow

```text
Client
  |
  | HTTP request to port 8080
  v
API Gateway
  |
  |-- CorrelationIdGlobalFilter
  |     - reuse incoming X-Correlation-ID
  |     - or generate a UUID
  |     - add it to the downstream request
  |     - add it to the response
  |
  |-- StructuredRequestLoggingFilter
  |     - method
  |     - path
  |     - status
  |     - duration
  |     - correlation ID
  |
  |-- OrderRateLimitGlobalFilter
  |     - applies only to POST /api/orders
  |     - rejects excess calls with HTTP 429
  |
  |-- Route predicate matching
  |     - /api/products/**  -> localhost:8081
  |     - /api/inventory/** -> localhost:8082
  |     - /api/orders/**    -> localhost:8083
  |
  |-- Reactive Netty HTTP client
  v
Downstream service
```

All external consumers should call port `8080`.

---

## 7. Reactive Gateway flow

Spring Cloud Gateway WebFlux is non-blocking.

A request is represented by `ServerWebExchange` and moves through a reactive filter chain.

High-level sequence:

1. Netty receives the request.
2. Gateway route predicates are evaluated.
3. Global and route-specific filters are combined.
4. Pre-filter logic runs in ascending order.
5. The request is proxied to the selected downstream URI.
6. The downstream response is received.
7. Post-filter logic runs in reverse order.
8. The response is returned to the client.

Gateway filters return:

```java
Mono<Void>
```

They must not perform blocking work on the event-loop thread.

Do not use the following in the Gateway request path:

```java
Thread.sleep(...)
mono.block()
future.get()
JDBC calls
blocking file I/O
RestClient
synchronized waiting
```

A blocking operation can occupy a Netty event-loop thread and reduce throughput for many requests.

---

## 8. Route matching

Configured routes:

| Route ID | Incoming path | Downstream URI |
|---|---|---|
| `product-service-route` | `/api/products/**` | `http://localhost:8081` |
| `inventory-service-route` | `/api/inventory/**` | `http://localhost:8082` |
| `order-service-route` | `/api/orders/**` | `http://localhost:8083` |

Example:

```yaml
- id: product-service-route
  uri: ${PRODUCT_SERVICE_URL:http://localhost:8081}
  predicates:
    - Path=/api/products/**
```

The Gateway keeps the original path.

`StripPrefix` and `RewritePath` are not required because downstream controllers already expose:

```text
/api/products
/api/inventory
/api/orders
```

Route selection must be based on technical request metadata such as path, host, header, or HTTP method.

The Gateway must not inspect the request body to make Product, Inventory, or Order business decisions.

---

## 9. Final `application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      server:
        webflux:

          routes:
            - id: product-service-route
              uri: ${PRODUCT_SERVICE_URL:http://localhost:8081}
              predicates:
                - Path=/api/products/**
              metadata:
                connect-timeout: 2000
                response-timeout: 5000

            - id: inventory-service-route
              uri: ${INVENTORY_SERVICE_URL:http://localhost:8082}
              predicates:
                - Path=/api/inventory/**
              metadata:
                connect-timeout: 2000
                response-timeout: 5000

            - id: order-service-route
              uri: ${ORDER_SERVICE_URL:http://localhost:8083}
              predicates:
                - Path=/api/orders/**
              metadata:
                connect-timeout: 2000
                response-timeout: 8000

          httpclient:
            connect-timeout: 2000
            response-timeout: 8s

          globalcors:
            add-to-simple-url-handler-mapping: true

            cors-configurations:
              '[/**]':
                allowed-origin-patterns:
                  - ${GATEWAY_ALLOWED_ORIGIN:http://localhost:*}

                allowed-methods:
                  - GET
                  - POST
                  - PUT
                  - PATCH
                  - DELETE
                  - OPTIONS

                allowed-headers:
                  - Content-Type
                  - Accept
                  - X-Correlation-ID

                exposed-headers:
                  - X-Correlation-ID
                  - X-RateLimit-Limit
                  - X-RateLimit-Remaining
                  - X-RateLimit-Reset

                allow-credentials: true
                max-age: 3600

gateway:
  rate-limit:
    orders:
      requests-per-window: ${ORDER_RATE_LIMIT_REQUESTS:10}
      window: ${ORDER_RATE_LIMIT_WINDOW:60s}

management:
  endpoint:
    gateway:
      access: read-only

  endpoints:
    web:
      exposure:
        include:
          - health
          - info
          - gateway

logging:
  pattern:
    console: >-
      %d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}
      level=%-5level
      service=${spring.application.name}
      correlationId=%X{correlationId:-none}
      logger=%logger{36}
      message="%msg"%n

  level:
    com.example.gateway: INFO
    org.springframework.cloud.gateway: INFO
```

### Deprecated configuration removed

Do not use:

```text
spring.cloud.gateway.routes
spring.cloud.gateway.httpclient
spring.cloud.gateway.globalcors
management.endpoint.gateway.enabled
```

Use:

```text
spring.cloud.gateway.server.webflux.routes
spring.cloud.gateway.server.webflux.httpclient
spring.cloud.gateway.server.webflux.globalcors
management.endpoint.gateway.access
```

The Actuator Gateway endpoint should use:

```yaml
access: read-only
```

unless route mutation through Actuator is intentionally required.

---

## 10. Filter order

Recommended order:

| Filter | Order | Reason |
|---|---:|---|
| `CorrelationIdGlobalFilter` | Highest precedence | Correlation ID must exist before logging, rate limiting, routing, or error handling |
| `StructuredRequestLoggingFilter` | Highest precedence + 10 | Logs the complete request lifecycle with the correlation ID |
| `OrderRateLimitGlobalFilter` | Highest precedence + 20 | Rejects excessive order creation before downstream routing |

A high-precedence filter runs early during the request phase and late during the response phase.

---

## 11. Reactive request mutation

`ServerHttpRequest` is immutable.

Adding a header requires a mutated request:

```java
ServerHttpRequest mutatedRequest = exchange.getRequest()
        .mutate()
        .headers(headers ->
                headers.set("X-Correlation-ID", correlationId))
        .build();
```

A new exchange must then be passed to the chain:

```java
ServerWebExchange mutatedExchange = exchange.mutate()
        .request(mutatedRequest)
        .build();

return chain.filter(mutatedExchange);
```

Calling `mutate()` without passing the resulting exchange does not alter the proxied request.

---

## 12. Correlation ID behavior

Rules:

- Preserve a nonblank incoming `X-Correlation-ID`.
- Generate a UUID when the header is missing or blank.
- Add the ID to the downstream request.
- Add the same ID to the Gateway response.
- Store it in exchange attributes for other filters and error handling.
- Include it in Gateway logs.
- Remove temporary MDC state after request completion.

Example request:

```http
GET /api/products/{id}
X-Correlation-ID: postman-gateway-test-001
```

Expected response header:

```http
X-Correlation-ID: postman-gateway-test-001
```

### MDC limitation

Reactive execution may switch threads.

Traditional thread-local MDC is therefore not a complete distributed tracing solution. This project uses MDC only around logging operations as a learning implementation.

Production systems normally use:

- Micrometer Tracing
- OpenTelemetry
- Reactor context propagation
- Centralized log aggregation

---

## 13. Structured logging

Example completion log:

```text
event=gateway_request_completed method=POST path=/api/orders status=201 durationMs=84 correlationId=...
```

Recommended fields:

- Event name
- HTTP method
- Request path
- Response status
- Duration
- Correlation ID
- Reactive completion signal
- Exception type for failures

Do not log:

- Request or response bodies
- Authorization headers
- Cookies
- Passwords
- Tokens
- Database credentials
- Full sensitive query strings

Logging request bodies in a reactive Gateway requires buffering and can increase memory usage, expose sensitive data, and interfere with streaming.

---

## 14. CORS behavior

Global CORS allows browser applications to call Gateway endpoints.

Development origin pattern:

```text
http://localhost:*
```

This supports local frontends such as:

```text
http://localhost:3000
http://localhost:4200
http://localhost:5173
```

Preflight support is enabled with:

```yaml
add-to-simple-url-handler-mapping: true
```

Production systems should use explicit HTTPS origins rather than a broad wildcard pattern.

The environment variable:

```text
GATEWAY_ALLOWED_ORIGIN
```

can override the development value.

---

## 15. Basic order rate limiting

The custom limiter applies only to:

```text
POST /api/orders
```

Default rule:

```text
10 requests per 60 seconds per client IP
```

Configuration:

```text
ORDER_RATE_LIMIT_REQUESTS
ORDER_RATE_LIMIT_WINDOW
```

Rejected requests return:

```http
HTTP/1.1 429 Too Many Requests
```

Example response:

```json
{
  "status": 429,
  "code": "ORDER_RATE_LIMIT_EXCEEDED",
  "message": "Too many order creation requests. Please try again later.",
  "correlationId": "..."
}
```

Possible response headers:

```text
X-RateLimit-Limit
X-RateLimit-Remaining
X-RateLimit-Reset
```

### Limitations

This learning limiter is:

- Process-local
- In-memory
- Fixed-window
- Based on client IP
- Reset when the Gateway restarts
- Not shared across multiple Gateway instances
- Vulnerable to bursts near the window boundary
- Dependent on trustworthy proxy forwarding
- Potentially unbounded by client cardinality unless entries are evicted

Production alternatives include:

- Redis-backed token bucket
- Spring Cloud Gateway `RequestRateLimiter`
- Bucket4j with distributed storage
- Kong, Apigee, NGINX or cloud-managed API Gateway products

---

## 16. Timeout configuration

### Global defaults

```yaml
httpclient:
  connect-timeout: 2000
  response-timeout: 8s
```

### Route-specific values

| Route | Connection timeout | Response timeout |
|---|---:|---:|
| Product | 2 seconds | 5 seconds |
| Inventory | 2 seconds | 5 seconds |
| Order | 2 seconds | 8 seconds |

Route metadata uses millisecond values:

```yaml
metadata:
  connect-timeout: 2000
  response-timeout: 5000
```

Timeouts prevent the Gateway from waiting indefinitely when a downstream service is unavailable or slow.

---

## 17. Standard Gateway errors

### Downstream unavailable

Example:

```json
{
  "status": 503,
  "code": "DOWNSTREAM_SERVICE_UNAVAILABLE",
  "message": "The requested downstream service is currently unavailable.",
  "path": "/api/products/...",
  "correlationId": "..."
}
```

### Downstream timeout

Example:

```json
{
  "status": 504,
  "code": "DOWNSTREAM_SERVICE_TIMEOUT",
  "message": "The downstream service did not respond within the configured timeout.",
  "path": "/api/orders",
  "correlationId": "..."
}
```

### Business errors

Business errors returned by downstream services should pass through without being reinterpreted by the Gateway.

Examples:

- Product not found
- Product inactive
- Insufficient stock
- Invalid order request
- Duplicate SKU
- Inventory conflict

The Gateway should distinguish:

```text
Gateway failure:
The Gateway could not connect to or receive a timely response from a service.

Business failure:
The downstream service processed the request and rejected it based on domain rules.
```

---

## 18. Actuator verification

### Health

```text
http://localhost:8080/actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

### Registered routes

```text
http://localhost:8080/actuator/gateway/routes
```

Expected route IDs:

```text
product-service-route
inventory-service-route
order-service-route
```

### Gateway root

```text
http://localhost:8080
```

A `404 Not Found` is expected because no route or controller is defined for `/`.

Spring Cloud Gateway does not provide a graphical administration UI by default. Actuator, Postman, logs, and routed API calls are used to verify it.

---

## 19. Postman collection

The Postman collection contains an `api-gateway` folder with:

1. `Gateway Health`
2. `Gateway Registered Routes`
3. `Get Product Through Gateway`

Collection variable:

```text
gateway_base = http://localhost:8080
```

Example routed Product request:

```text
GET {{gateway_base}}/api/products/{{product_id}}
```

Before running it, execute the Product Service `Create Product` request so that `product_id` is available.

Recommended collection variables:

```text
gateway_base
product_base
inventory_base
order_base
product_id
correlation_id
```

All client-facing API tests should use the Gateway URL once downstream services are running.

---

## 20. Build and run

### Verify Maven uses Java 25

```powershell
mvn -version
```

Expected:

```text
Java version: 25
```

### Validate project hierarchy

From the project root:

```powershell
mvn validate
```

### Build all modules

```powershell
mvn clean package
```

### Build only the Gateway

```powershell
mvn -pl api-gateway clean package
```

### Build Gateway with required reactor projects

```powershell
mvn -pl api-gateway -am clean package
```

### Start services

Start in this order:

```text
product-service   : 8081
inventory-service : 8082
order-service     : 8083
api-gateway       : 8080
```

Run from the root in separate terminals:

```powershell
mvn -pl product-service spring-boot:run
```

```powershell
mvn -pl inventory-service spring-boot:run
```

```powershell
mvn -pl order-service spring-boot:run
```

```powershell
mvn -pl api-gateway spring-boot:run
```

---

## 21. Manual testing checklist

### Route verification

Call Product Service directly:

```text
GET http://localhost:8081/api/products/{productId}
```

Then through the Gateway:

```text
GET http://localhost:8080/api/products/{productId}
```

The response should be functionally equivalent.

Repeat for Inventory and Order endpoints.

### Correlation ID

Send a request without `X-Correlation-ID`.

Verify that:

- A UUID is generated
- The response contains `X-Correlation-ID`
- Gateway logs contain the same value

Send another request with:

```text
X-Correlation-ID: gateway-test-001
```

Verify the exact value is preserved.

### Rate limiting

Send more than the configured number of requests to:

```text
POST http://localhost:8080/api/orders
```

Expected result after the limit is reached:

```text
429 Too Many Requests
```

Verify that repeated Product or Inventory GET requests are not restricted by the Order limiter.

### Downstream unavailable

Stop Product Service and call:

```text
GET http://localhost:8080/api/products/{productId}
```

Expected:

```text
503 Service Unavailable
```

The Gateway must remain running.

### Timeout

Temporarily add a downstream delay longer than the configured response timeout.

Expected:

```text
504 Gateway Timeout
```

Remove the artificial delay afterward.

### CORS

Send an OPTIONS request with:

```text
Origin: http://localhost:3000
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type,X-Correlation-ID
```

Verify the response contains suitable `Access-Control-Allow-*` headers.

### Invalid route

Call:

```text
GET http://localhost:8080/api/payments
```

Expected:

```text
404 Not Found
```

---

## 22. Automated tests

### Correlation ID filter tests

Verify:

- Existing correlation ID is preserved
- Missing correlation ID generates a UUID
- Downstream request receives the header
- Gateway response receives the same header
- Exchange attributes contain the ID

### Rate limiter tests

Verify:

- Only `POST /api/orders` is limited
- Requests below the limit continue
- Requests above the limit return 429
- Product and Inventory routes are unaffected
- Rate-limit headers are returned

### Route configuration integration test

Recommended assertions:

- `/api/products/**` resolves to Product Service
- `/api/inventory/**` resolves to Inventory Service
- `/api/orders/**` resolves to Order Service
- Unknown paths return 404
- CORS preflight is handled
- Gateway-level 503 and 504 payloads use the standard format

Run:

```powershell
mvn -pl api-gateway test
```

---

## 23. Implementation phases

### Phase 1 — Module and route foundation

Implement:

- Java 25-compatible Maven baseline
- Spring Cloud BOM
- `api-gateway` module
- Current WebFlux Gateway starter
- Three static routes
- Global and route-specific timeouts
- Actuator endpoint exposure

Learn:

- Parent versus child Maven modules
- Route predicates
- Static URI routing
- Reactive proxying
- Why path rewriting is unnecessary here

### Phase 2 — Correlation and logging

Implement:

- `CorrelationIdGlobalFilter`
- Reactive request mutation
- Response correlation header
- Structured lifecycle logging

Learn:

- Global filter ordering
- Immutable reactive request objects
- Pre-filter and post-filter behavior
- MDC limitations in reactive systems

### Phase 3 — CORS and rate limiting

Implement:

- Global CORS policy
- `POST /api/orders` limiter
- Standard 429 response

Learn:

- Browser preflight
- Rate-limit scope
- Difference between local and distributed limiters
- Why business rules do not belong in rate-limit filters

### Phase 4 — Failure handling and review

Implement:

- Connection failure mapping to 503
- Timeout mapping to 504
- Filter unit tests
- Route integration tests
- Architecture review

Learn:

- Infrastructure errors versus domain errors
- Reactive error handling
- Gateway responsibility boundaries
- Safe operational logging

---

## 24. Senior architecture review

### Business logic placement

The Gateway must not:

- Calculate total prices
- Determine whether a product is active
- Reserve inventory
- Save orders
- Query databases

Those responsibilities remain in downstream services.

### Reactive safety

Review for:

- `.block()`
- `Thread.sleep()`
- JDBC
- Synchronous HTTP clients
- Blocking locks
- File I/O on request threads

None should appear in the Gateway request path.

### Logging safety

Confirm that logs exclude:

- Authorization
- Cookies
- Request bodies
- Passwords
- Tokens
- Sensitive query parameters

### Route correctness

Confirm:

- Route paths match downstream controller paths
- No unnecessary `StripPrefix`
- No accidental route overlap
- Exact rate-limit path handling
- Optional trailing slash behavior is defined

### Status handling

Expected behavior:

| Situation | Status |
|---|---:|
| Downstream business response | Passed through |
| Rate limit exceeded | 429 |
| Connection failure | 503 |
| Response timeout | 504 |
| Unknown Gateway route | 404 |

### Rate-limit weaknesses

The learning limiter is process-local and fixed-window. It should not be presented as production-ready distributed rate limiting.

### Timeout review

Both global defaults and route-specific metadata must be present.

---

## 25. Java 25 runtime notes

A Netty warning involving `sun.misc.Unsafe` may appear when running on Java 25.

Example:

```text
A terminally deprecated method in sun.misc.Unsafe has been called
```

This warning originates from the Netty version on the dependency classpath. It does not mean that the Gateway failed to start.

Verify actual startup using:

```text
Netty started on port 8080
Started ApiGatewayApplication
```

The warning should be monitored across future Netty and Spring dependency upgrades, but it does not require adding blocking or unsafe code to the application.

---

## 26. Files added or changed

```text
pom.xml
api-gateway/pom.xml
api-gateway/README.md
api-gateway/src/main/java/com/example/gateway/ApiGatewayApplication.java
api-gateway/src/main/java/com/example/gateway/filter/CorrelationIdGlobalFilter.java
api-gateway/src/main/java/com/example/gateway/filter/StructuredRequestLoggingFilter.java
api-gateway/src/main/java/com/example/gateway/filter/OrderRateLimitGlobalFilter.java
api-gateway/src/main/java/com/example/gateway/handler/GatewayErrorWebExceptionHandler.java
api-gateway/src/main/resources/application.yml
api-gateway/src/test/java/com/example/gateway/filter/CorrelationIdGlobalFilterTest.java
api-gateway/src/test/java/com/example/gateway/filter/OrderRateLimitGlobalFilterTest.java
synchronous-communication.postman_collection.json
API-GATEWAY-BLUEPRINT.md
```

Existing service POMs were also corrected to inherit from:

```text
com.example:api-gateway-demo:1.0.0
```

Duplicate dependency declarations, such as repeated `javax.annotation-api`, should be removed.

---

## 27. Completion criteria

The project is complete when:

- The full Maven reactor builds successfully with Java 25
- Root packaging is `pom`
- Every child inherits from `api-gateway-demo`
- Gateway starts on port 8080
- Product route forwards to port 8081
- Inventory route forwards to port 8082
- Order route forwards to port 8083
- `/actuator/health` returns `UP`
- `/actuator/gateway/routes` displays three routes
- Correlation IDs are generated and propagated
- Logs show method, path, status, duration and correlation ID
- CORS preflight succeeds for allowed origins
- Excessive order POST requests return 429
- Unavailable services produce standard 503 responses
- Slow services produce standard 504 responses
- Unknown paths return 404
- Business errors pass through unchanged
- Gateway contains no JPA, repository, database or domain business logic
- Tests pass
- The Postman Gateway requests work through port 8080

---

## 28. What this project teaches

After completing this project, you should be able to explain:

- Why an API Gateway is an infrastructure boundary
- How Spring Cloud Gateway matches routes
- How reactive filters execute before and after routing
- Why `ServerHttpRequest` must be mutated
- How correlation IDs move across services
- Why blocking calls are dangerous in WebFlux
- How Gateway timeouts differ from service-client timeouts
- How CORS preflight works
- How a simple local rate limiter works
- Why distributed rate limiting needs shared state
- How to distinguish infrastructure errors from domain errors
- Why the Gateway must avoid business logic
- How Maven parent and child modules should be structured
- Why Java 25 requires a compatible Spring Boot, Spring Cloud and Lombok baseline

---

## 29. Recommended next project

The next focused project can cover one of these areas:

### Option A — Service discovery and load-balanced routing

Add:

- Service registry
- Service registration
- Logical `lb://` Gateway routes
- Multiple service instances
- Client-side load balancing

### Option B — Resilience patterns

Add:

- Retry with strict boundaries
- Circuit breaker
- Time limiter
- Fallback strategy
- Bulkhead
- Failure metrics

Do not add both areas at once if the goal is concept-by-concept learning.
