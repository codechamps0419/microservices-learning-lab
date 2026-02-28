# Project 4 Blueprint

## Concepts
- Registration: each client publishes application name, host, port, instance ID, and status to Eureka.
- Heartbeat: clients renew leases periodically; missing renewals eventually remove an instance.
- Discovery: Order Service obtains PRODUCT-SERVICE instances from its local Eureka registry cache.
- Load balancing: Spring Cloud LoadBalancer selects one discovered instance for `http://product-service`.
- Kubernetes: Kubernetes normally provides stable Service DNS and platform-level balancing, so Eureka is usually unnecessary inside a Kubernetes cluster.

## Phases
1. Extract Product and Order modules and create Java 25 parent.
2. Externalize database, ports, service name, Eureka URL, and timeout configuration.
3. Add validated `@ConfigurationProperties`.
4. Add Eureka Server.
5. Register Product and Order clients.
6. Add load-balanced RestClient and discovery demo endpoint.
7. Run two Product instances and verify failover.

## Architecture
```text
Product :8081 ─┐
               ├─ register PRODUCT-SERVICE ─> Eureka :8761
Product :8084 ─┘
Order   :8083 ─── register ORDER-SERVICE ───> Eureka :8761

Order -> http://product-service -> Spring Cloud LoadBalancer -> :8081 or :8084
```

## Configuration validation
`DatabaseProperties` uses `@ConfigurationProperties` and `@Validated`. Missing `*_DB_URL`, `*_DB_USERNAME`, or `*_DB_PASSWORD` prevents startup. `ProductClientProperties` validates the logical service name and timeout durations.

## Kubernetes comparison
Eureka is an application-level registry used by clients. Kubernetes normally exposes a stable Service DNS name and performs platform-level endpoint routing, so Eureka is usually not added inside Kubernetes merely for discovery.
