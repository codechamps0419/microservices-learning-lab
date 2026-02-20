package com.example.product.grpc;

import com.example.product.exception.ProductNotFoundException;
import com.example.product.repository.ProductRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/*
 * gRPC server implementation for product-service.
 *
 * =========================================================================
 * HOW gRPC SERVER WORKS
 * =========================================================================
 *
 * The Maven protobuf plugin reads product.proto and generates:
 *   - ProductServiceGrpc.java          — base classes and stubs
 *   - ProductServiceGrpc.ProductServiceImplBase — abstract class to extend
 *   - GetProductRequest.java           — request message class
 *   - GetProductResponse.java          — response message class
 *
 * This class extends ProductServiceImplBase and overrides getProduct().
 * grpc-spring-boot-starter detects @GrpcService and registers this class
 * as a gRPC service on the port defined in application.yml.
 *
 * =========================================================================
 * StreamObserver PATTERN
 * =========================================================================
 *
 * gRPC uses StreamObserver for responses instead of return values.
 * For a unary call (one request → one response):
 *   1. responseObserver.onNext(response)   — send the response
 *   2. responseObserver.onCompleted()      — signal end of stream
 *
 * For errors:
 *   responseObserver.onError(statusException) — send a gRPC error status
 *
 * You must call EITHER (onNext + onCompleted) OR onError — never both.
 *
 * =========================================================================
 * gRPC STATUS CODES vs HTTP STATUS CODES
 * =========================================================================
 *
 * gRPC has its own status codes — not HTTP codes.
 *
 *   gRPC Status          HTTP equivalent    When to use
 *   -------------------  -----------------  --------------------------------
 *   Status.NOT_FOUND     404                Resource does not exist
 *   Status.INVALID_ARGUMENT  400            Bad input from caller
 *   Status.INTERNAL      500                Unexpected server error
 *   Status.UNAVAILABLE   503                Service temporarily unavailable
 *   Status.UNAUTHENTICATED  401             Missing/invalid credentials
 *   Status.PERMISSION_DENIED  403           Authenticated but not authorized
 *
 * withDescription() adds a human-readable message to the status.
 * asRuntimeException() converts it to a throwable for onError().
 *
 * =========================================================================
 * WHY QUERY THE REPOSITORY DIRECTLY (NOT VIA ProductService)?
 * =========================================================================
 *
 * ProductService returns ProductResponse DTOs — designed for the REST layer.
 * The gRPC layer needs to build GetProductResponse protobuf messages.
 * Going through ProductService would mean: entity → DTO → protobuf message.
 * Querying the repository directly: entity → protobuf message (one step).
 *
 * In a larger system, you would extract a shared domain service that both
 * the REST and gRPC layers call. For this learning project, direct repository
 * access keeps the code minimal and the focus on gRPC mechanics.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductRepository productRepository;

    /*
     * Handles: rpc GetProduct (GetProductRequest) returns (GetProductResponse)
     *
     * Called by order-service's ProductGrpcClient when it invokes getProduct().
     * Runs on a gRPC server thread — separate from Tomcat's HTTP thread pool.
     */
    @Override
    public void getProduct(GetProductRequest request, StreamObserver<GetProductResponse> responseObserver) {
        log.info("gRPC getProduct called for productId={}", request.getProductId());

        UUID productId;
        try {
            productId = UUID.fromString(request.getProductId());
        } catch (IllegalArgumentException e) {
            // Invalid UUID format — tell the caller their input is wrong.
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("Invalid productId format: " + request.getProductId())
                    .asRuntimeException()
            );
            return;
        }

        productRepository.findById(productId).ifPresentOrElse(
            product -> {
                // Build the protobuf response message using the generated builder.
                // price is serialised as a plain string to avoid floating-point issues.
                GetProductResponse response = GetProductResponse.newBuilder()
                        .setId(product.getId().toString())
                        .setName(product.getName())
                        .setPrice(product.getPrice().toPlainString())
                        .setStatus(product.getStatus().name())
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
                log.info("gRPC getProduct completed for productId={}", productId);
            },
            () -> {
                // Product not found — send NOT_FOUND status to the caller.
                log.warn("gRPC getProduct: product not found for productId={}", productId);
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Product not found: " + productId)
                        .asRuntimeException()
                );
            }
        );
    }
}
