# API Gateway

Reactive Spring Cloud Gateway module for the existing Product, Inventory and Order services.

## Responsibilities

- Route Product APIs to `http://localhost:8081`
- Route Inventory APIs to `http://localhost:8082`
- Route Order APIs to `http://localhost:8083`
- Generate or propagate `X-Correlation-ID`
- Configure browser CORS
- Log request metadata without logging bodies or authorization headers
- Apply a small in-memory rate limit to `POST /api/orders`
- Convert gateway connection and timeout failures into a consistent JSON response

## Explicitly excluded

- JPA and database access
- Product, inventory or order business rules
- Authentication and authorization
- Service discovery
- Circuit breaker and retry
- Redis-backed distributed rate limiting

## Run

Start downstream services first, then run:

```bash
mvn -pl api-gateway spring-boot:run
```

Gateway base URL:

```text
http://localhost:8080
```

Example requests:

```bash
curl -i http://localhost:8080/api/products/{productId}
```

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: demo-correlation-001" \
  -d '{"productId":"YOUR_UUID","quantity":1}'
```

## Useful actuator endpoint

```text
GET http://localhost:8080/actuator/gateway/routes
```

## Rate-limit limitation

The current limiter is deliberately local and educational. Its counters exist only inside one gateway process and reset on restart. Multiple gateway instances would each maintain separate counters. A later production-oriented project should use a shared store such as Redis or an external API-management layer.
