================================================================================
  GUIDE    : gRPC + WebClient — Implementation Reference
  PROJECT  : synchronous-communication
  PURPOSE  : Side-by-side comparison of REST, gRPC, and WebClient
================================================================================


--------------------------------------------------------------------------------
1. WHAT WAS ADDED (OVERVIEW)
--------------------------------------------------------------------------------

  Nothing existing was changed. Three new communication styles now coexist:

  Endpoint                                    Transport    Calls
  ------------------------------------------  -----------  -----------------
  POST   /api/orders                          REST         product + inventory
  GET    /api/orders/product/{id}/grpc        gRPC         product-service
  GET    /api/orders/inventory/{id}/webclient WebClient    inventory-service

  All three return the same DTOs (ProductClientResponse, StockClientResponse).
  The transport is the only difference — making comparison direct and clear.


--------------------------------------------------------------------------------
2. gRPC — CONCEPTS
--------------------------------------------------------------------------------

  WHAT IS gRPC?
  -------------
  gRPC is a high-performance RPC (Remote Procedure Call) framework developed
  by Google. Instead of sending JSON over HTTP/1.1, it sends binary-encoded
  Protocol Buffer (protobuf) messages over HTTP/2.

  KEY DIFFERENCES FROM REST:
  --------------------------
    Feature          REST                      gRPC
    ---------------  ------------------------  --------------------------------
    Protocol         HTTP/1.1                  HTTP/2
    Format           JSON (text)               Protobuf (binary)
    Contract         OpenAPI (optional)        .proto file (mandatory)
    Client code      You write it              Auto-generated from .proto
    Error model      HTTP status codes         gRPC status codes
    Streaming        Not native                Built-in (server/client/bidi)
    Browser support  Native                    Requires grpc-web proxy

  WHY BINARY IS FASTER:
  ---------------------
  JSON encodes field names as strings in every message:
    {"productId":"abc","name":"Keyboard","price":49.99,"status":"ACTIVE"}

  Protobuf uses field numbers (integers) instead of names:
    field 1 = "abc", field 2 = "Keyboard", field 3 = "49.99", field 4 = "ACTIVE"

  The binary encoding is 3-10x smaller than JSON and faster to parse.
  For internal service-to-service calls at high volume, this matters.

  HTTP/2 ADVANTAGES:
  ------------------
  - Multiplexing: multiple requests share one TCP connection (no head-of-line blocking)
  - Header compression: repeated headers (like correlation IDs) are compressed
  - Binary framing: more efficient than HTTP/1.1 text framing

  WHEN TO USE gRPC:
  -----------------
  - Internal service-to-service calls (not public APIs)
  - High-throughput scenarios where JSON overhead is measurable
  - Strongly-typed contracts are important (proto enforces the contract)
  - Streaming is needed (server-push, bidirectional streaming)

  WHEN NOT TO USE gRPC:
  ---------------------
  - Public APIs consumed by browsers (gRPC requires grpc-web proxy)
  - Teams unfamiliar with protobuf tooling
  - Simple CRUD services where REST is sufficient


--------------------------------------------------------------------------------
3. gRPC — HOW IT WORKS IN THIS PROJECT
--------------------------------------------------------------------------------

  FLOW:
  -----

  order-service                              product-service
       |                                           |
       |  GET /api/orders/product/{id}/grpc        |
       |  (HTTP/1.1 from Postman/browser)          |
       v                                           |
  OrderGrpcController                             |
       |                                           |
       v                                           |
  ProductGrpcClient                               |
       |  stub.getProduct(GetProductRequest)       |
       |  gRPC call over HTTP/2 port 9091 -------> |
       |                                           v
       |                                  ProductGrpcService
       |                                  (extends ProductServiceImplBase)
       |                                           |
       |                                  productRepository.findById()
       |                                           |
       |  GetProductResponse (binary) <----------- |
       v                                           |
  ProductClientResponse (mapped)
       |
       v
  200 OK (JSON to caller)

  TWO SERVERS IN ONE JVM:
  -----------------------
  product-service runs two servers simultaneously:
    port 8081 — Tomcat (REST, HTTP/1.1)   — existing, unchanged
    port 9091 — Netty  (gRPC, HTTP/2)     — new

  grpc-spring-boot-starter starts the Netty gRPC server automatically
  when it detects @GrpcService beans on the classpath.

  THE .proto FILE IS THE CONTRACT:
  --------------------------------
  product.proto defines:
    - The service name and RPC methods
    - The request and response message shapes
    - Field names and field numbers

  The Maven protobuf plugin reads product.proto and generates:
    - GetProductRequest.java        — request message
    - GetProductResponse.java       — response message
    - ProductServiceGrpc.java       — base class + stubs

  product-service extends ProductServiceGrpc.ProductServiceImplBase (server).
  order-service injects ProductServiceGrpc.ProductServiceBlockingStub (client).

  BOTH SIDES SHARE THE SAME .proto FILE:
  ---------------------------------------
  The .proto file is copied into both services under src/main/proto/.
  The only difference is the java_package option:
    product-service: option java_package = "com.example.product.grpc";
    order-service:   option java_package = "com.example.order.grpc";

  In a real team, the .proto file would live in a shared Maven module or
  a separate repository so both sides always stay in sync.

  gRPC STATUS CODES:
  ------------------
  gRPC has its own status codes — not HTTP codes.

    gRPC Code           HTTP equivalent    When used in this project
    ------------------  -----------------  --------------------------------
    NOT_FOUND           404                Product does not exist
    INVALID_ARGUMENT    400                Invalid UUID format in request
    UNAVAILABLE         503                Service temporarily down
    INTERNAL            500                Unexpected server error

  The client maps these back to the same typed exceptions as the REST client:
    NOT_FOUND    → ProductNotFoundException
    UNAVAILABLE  → ProductServiceUnavailableException
    others       → ProductServiceException

  StreamObserver PATTERN:
  -----------------------
  gRPC uses StreamObserver instead of return values.
  For a unary call (one request → one response):

    // Success path:
    responseObserver.onNext(response);    // send the response
    responseObserver.onCompleted();       // signal end of stream

    // Error path:
    responseObserver.onError(statusException);  // send error status

  You must call EITHER (onNext + onCompleted) OR onError — never both.


--------------------------------------------------------------------------------
4. FILES ADDED — gRPC
--------------------------------------------------------------------------------

  File                                              Service           Role
  ------------------------------------------------  ----------------  ----------
  src/main/proto/product.proto                      product-service   Contract
  src/main/proto/product.proto                      order-service     Contract (copy)
  .../product/grpc/ProductGrpcService.java          product-service   gRPC server
  .../order/grpc/ProductGrpcClient.java             order-service     gRPC client
  .../order/controller/OrderGrpcController.java     order-service     Demo endpoint

  FILES CHANGED:
  product-service/pom.xml       — added grpc-spring-boot-starter, protobuf-maven-plugin
  order-service/pom.xml         — added grpc-spring-boot-starter, protobuf-maven-plugin
  parent pom.xml                — added os-maven-plugin extension (detects OS for protoc)
  product-service/application.yml  — added grpc.server.port=9091
  order-service/application.yml    — added grpc.client.product-service.address


--------------------------------------------------------------------------------
5. WebClient — CONCEPTS
--------------------------------------------------------------------------------

  WHAT IS WebClient?
  ------------------
  WebClient is Spring's reactive HTTP client introduced in Spring 5.
  It is the modern replacement for RestTemplate and an alternative to RestClient.

  The key difference: WebClient is non-blocking.

  BLOCKING vs NON-BLOCKING:
  -------------------------

  RestClient (blocking):
    Thread A sends request → Thread A WAITS → response arrives → Thread A continues
    Thread A is blocked (doing nothing) while waiting for the response.
    If 200 requests are in-flight, 200 threads are blocked.

  WebClient (non-blocking):
    Thread A sends request → Thread A is RELEASED → response arrives →
    any available thread picks up and continues processing.
    If 200 requests are in-flight, only a handful of threads are needed.

  This is the event-loop model (same as Node.js, Nginx).
  Reactor Netty (WebClient's engine) uses a small fixed pool of event-loop
  threads that handle thousands of concurrent connections.

  MONO<T> AND FLUX<T>:
  --------------------
  WebClient returns reactive types instead of direct values:

    Mono<T>   — a future that will produce 0 or 1 value
    Flux<T>   — a future that will produce 0..N values (streaming)

  Mono is LAZY — nothing happens until someone subscribes.

  Three ways to consume a Mono:
    .block()          — subscribe and wait (converts back to synchronous)
    .subscribe()      — subscribe asynchronously (fire and forget)
    return Mono<T>    — let the reactive framework handle it end-to-end

  In this project we use .block() because the rest of order-service is
  synchronous (Spring MVC on Tomcat). .block() is the correct bridge.

  In a fully reactive application (Spring WebFlux), you would return
  Mono<ResponseEntity<T>> from the controller and never call .block().

  WHEN TO USE WebClient:
  ----------------------
  - High-concurrency services where thread count is a bottleneck
  - Streaming responses (server-sent events, large file downloads)
  - Fully reactive applications (Spring WebFlux)
  - When you want to compose multiple async calls without blocking

  WHEN NOT TO USE WebClient:
  --------------------------
  - Simple services where RestClient is easier to read and debug
  - Teams unfamiliar with reactive programming (steep learning curve)
  - When .block() is used everywhere — you get the complexity of reactive
    with none of the concurrency benefits


--------------------------------------------------------------------------------
6. WebClient — HOW IT WORKS IN THIS PROJECT
--------------------------------------------------------------------------------

  FLOW:
  -----

  order-service
       |
       |  GET /api/orders/inventory/{id}/webclient
       v
  OrderWebClientController
       |
       v
  InventoryWebClient
       |  webClient.get().uri(...).retrieve().bodyToMono(StockClientResponse.class)
       |  returns Mono<StockClientResponse>
       |
       |  .onErrorMap(WebClientRequestException → InventoryServiceUnavailableException)
       |
       |  .block()   ← subscribes and waits (bridge to synchronous MVC)
       |
       v
  StockClientResponse
       |
       v
  200 OK (JSON to caller)

  ERROR HANDLING WITH onStatus():
  --------------------------------
  WebClient's onStatus() returns Mono<Throwable> instead of throwing directly:

    RestClient:
      .onStatus(NOT_FOUND::equals, (req, res) -> { throw new InventoryNotFoundException(); })

    WebClient:
      .onStatus(NOT_FOUND::equals, res -> Mono.error(new InventoryNotFoundException(...)))

  Mono.error() injects the exception into the reactive pipeline.
  It surfaces as a thrown exception when .block() is called.

  TIMEOUT SETUP:
  --------------
  WebClient uses Reactor Netty — timeouts are configured differently from RestClient:

    RestClient:
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(3000);
      factory.setReadTimeout(5000);

    WebClient:
      HttpClient httpClient = HttpClient.create()
          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
          .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(5000, MILLISECONDS)));

  Both achieve the same result — the configuration syntax is different because
  the underlying HTTP engine is different (JDK HttpURLConnection vs Reactor Netty).


--------------------------------------------------------------------------------
7. FILES ADDED — WebClient
--------------------------------------------------------------------------------

  File                                                  Service        Role
  ----------------------------------------------------  -------------  ----------
  .../order/client/InventoryWebClient.java              order-service  WebClient client
  .../order/config/WebClientConfig.java                 order-service  Bean config
  .../order/controller/OrderWebClientController.java    order-service  Demo endpoint

  FILES CHANGED:
  order-service/pom.xml  — added spring-boot-starter-webflux


--------------------------------------------------------------------------------
8. THREE-WAY COMPARISON
--------------------------------------------------------------------------------

                        RestClient          gRPC                WebClient
  --------------------  ------------------  ------------------  ------------------
  Protocol              HTTP/1.1            HTTP/2              HTTP/1.1 or HTTP/2
  Format                JSON (text)         Protobuf (binary)   JSON (text)
  Contract              None (optional)     .proto (mandatory)  None (optional)
  Client code           Manual              Auto-generated      Manual
  Threading model       Blocking            Blocking (stub)     Non-blocking
  Error type caught     RestClientResponse  StatusRuntime       WebClientRequest
                        Exception           Exception           Exception
  Spring dependency     starter-web         grpc-starter        starter-webflux
  Best for              Simple internal     High-perf internal  High-concurrency
                        calls               calls               or streaming

  DEMO ENDPOINTS IN THIS PROJECT:
  --------------------------------
  POST /api/orders                          → RestClient  → product + inventory
  GET  /api/orders/product/{id}/grpc        → gRPC        → product-service :9091
  GET  /api/orders/inventory/{id}/webclient → WebClient   → inventory-service :8082


--------------------------------------------------------------------------------
9. HOW TO TEST
--------------------------------------------------------------------------------

  PREREQUISITES:
  - All three services running
  - product_db, inventory_db, order_db created in PostgreSQL
  - At least one product created in product-service
  - At least one inventory record created in inventory-service

  gRPC DEMO:
  ----------
  GET http://localhost:8083/api/orders/product/{productId}/grpc

  What to observe:
  - Same response as GET http://localhost:8081/api/products/{productId}
  - product-service logs show "gRPC getProduct called for productId=..."
  - Transport is HTTP/2 on port 9091 (invisible to you, visible in Wireshark)

  WebClient DEMO:
  ---------------
  GET http://localhost:8083/api/orders/inventory/{productId}/webclient

  What to observe:
  - Same response as GET http://localhost:8082/api/inventories/{productId}
  - order-service logs show "WebClient call → inventory-service getStock..."
  - Correlation ID is forwarded (check inventory-service logs)

  SWAGGER UI:
  -----------
  http://localhost:8083/swagger-ui/index.html
  Both demo endpoints appear under their own tags:
    "gRPC Demo"      — GET /orders/product/{productId}/grpc
    "WebClient Demo" — GET /orders/inventory/{productId}/webclient


--------------------------------------------------------------------------------
10. KEY INTERVIEW POINTS
--------------------------------------------------------------------------------

  Q: Why use gRPC for internal calls instead of REST?
  A: gRPC uses HTTP/2 (multiplexing, header compression) and protobuf (binary,
     smaller payload). For high-volume internal calls, this reduces latency and
     bandwidth. The .proto contract also enforces type safety at compile time —
     a field rename in the proto breaks the build immediately, not at runtime.

  Q: What is the difference between blocking stub and async stub in gRPC?
  A: Blocking stub blocks the calling thread until the server responds — same
     behaviour as RestClient. Async stub uses StreamObserver callbacks and does
     not block. Future stub returns a ListenableFuture. For synchronous Spring MVC
     services, blocking stub is the simplest and most readable choice.

  Q: Why does WebClient use Mono<T> instead of returning T directly?
  A: Mono<T> represents a future value. It is lazy — nothing happens until
     subscribed. This allows the framework to schedule the I/O on an event-loop
     thread without blocking the calling thread. The non-blocking model lets a
     small number of threads handle thousands of concurrent connections.

  Q: When would you use .block() on a Mono?
  A: When bridging reactive WebClient into a synchronous Spring MVC application.
     .block() subscribes to the Mono and waits for the result on the current thread.
     In a fully reactive application (Spring WebFlux), you would never call .block()
     — you return Mono<T> from the controller and let the framework handle it.

  Q: Can you run WebClient and RestClient in the same application?
  A: Yes. Adding spring-boot-starter-webflux alongside spring-boot-starter-web
     is supported. Spring MVC (Tomcat) remains the active web layer. WebFlux
     only contributes WebClient and the reactive types. This is exactly what
     this project does.

  Q: Can you run REST and gRPC on the same service simultaneously?
  A: Yes. grpc-spring-boot-starter starts a separate Netty gRPC server on a
     different port (9091). Tomcat continues serving REST on port 8081. Both
     run in the same JVM process with separate thread pools.


================================================================================
  END OF GUIDE
================================================================================
