package com.example.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Configures the OpenAPI / Swagger UI documentation for order-service.
 *
 * After startup, documentation is available at:
 *   http://localhost:8083/api/swagger-ui.html  — interactive UI
 *   http://localhost:8083/api/v3/api-docs      — raw OpenAPI JSON
 *
 * WHY OPENAPI MATTERS IN MICROSERVICES:
 * In a microservices system, each service is independently deployable and
 * may be consumed by other teams. OpenAPI provides a machine-readable
 * contract that:
 *   - Documents every endpoint, request body, and response shape
 *   - Allows client code generation (no manual DTO duplication)
 *   - Enables contract testing between services
 *   - Serves as living documentation that is always in sync with the code
 *
 * CORRELATION ID HEADER:
 * The X-Correlation-ID header is added as an optional parameter to every
 * operation via OperationCustomizer. This documents the tracing mechanism
 * for API consumers — they can provide their own correlation ID or let
 * order-service generate one.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("Synchronous order placement with product validation and stock reservation. " +
                                     "Demonstrates service-to-service communication, compensation, and error mapping.")
                        .version("1.0.0"));
    }

    /*
     * Adds X-Correlation-ID as an optional header parameter to every operation.
     *
     * If provided by the caller, order-service uses it for tracing.
     * If absent, order-service generates a new UUID.
     * The correlation ID is always echoed back in the response header.
     */
    @Bean
    public OperationCustomizer correlationIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-Correlation-ID")
                            .description("Optional trace ID. Generated automatically if not provided. " +
                                         "Forwarded to product-service and inventory-service. " +
                                         "Echoed back in the response header.")
                            .required(false)
                            .schema(new io.swagger.v3.oas.models.media.StringSchema()
                                    .example("550e8400-e29b-41d4-a716-446655440000"))
            );
            return operation;
        };
    }
}
