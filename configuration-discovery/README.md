# Project 4 — External Configuration and Service Discovery

Modules: discovery-server, product-service, order-service.

## Start
1. Start Eureka: `mvn -pl discovery-server spring-boot:run`
2. Start Product instance 1 with SERVER_PORT=8081 and PRODUCT_DB_* variables.
3. Start Product instance 2 with SERVER_PORT=8084 and the same PRODUCT_DB_* variables.
4. Start Order Service with ORDER_DB_* variables.
5. Open http://localhost:8761 and verify two PRODUCT-SERVICE instances.
6. Call http://localhost:8083/api/orders/discovery-demo repeatedly.

No API Gateway, Inventory module, Kafka, security, or Docker is included.
