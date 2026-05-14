# Microservices Platform Q&A

> **Platform**: Spring Boot 4.0.5 | Spring Cloud 2025.1.1 | Java 25 | Maven Multi-Module
> **Source**: [README.md](README.md) | [AGENTS.md](AGENTS.md)

---

## Table of Contents

1. [System Design & Architecture](#1-system-design--architecture)
2. [Microservices Patterns](#2-microservices-patterns)
3. [API Gateway & Routing](#3-api-gateway--routing)
4. [Service Discovery & Configuration](#4-service-discovery--configuration)
5. [Security & OAuth2](#5-security--oauth2)
6. [Data Architecture & Persistence](#6-data-architecture--persistence)
7. [Inter-Service Communication](#7-inter-service-communication)
8. [Event-Driven Architecture](#8-event-driven-architecture)
9. [Resilience & Fault Tolerance](#9-resilience--fault-tolerance)
10. [Observability & Monitoring](#10-observability--monitoring)
11. [Containerization & Deployment](#11-containerization--deployment)
12. [CI/CD & Build Pipeline](#12-cicd--build-pipeline)
13. [Testing Strategy](#13-testing-strategy)
14. [Code Quality & Standards](#14-code-quality--standards)
15. [Troubleshooting & Debugging](#15-troubleshooting--debugging)
16. [Scalability & Performance](#16-scalability--performance)
17. [Trade-offs & Technical Debt](#17-trade-offs--technical-debt)
18. [Scenario-Based Questions](#18-scenario-based-questions)

---

## 1. System Design & Architecture

### Q1: Describe the overall architecture of this platform. What design patterns are employed?

**Expected Answer**: This is a microservices-based banking platform (EazyBank) with the following core patterns:

- **Microservices Architecture**: 6 business/utility services (accounts, cards, loans, message, gatewayserver, configserver) + infrastructure services (eurekaserver, RabbitMQ, Redis, Keycloak)
- **API Gateway Pattern**: Spring Cloud Gateway (WebFlux/Reactive) as the single entry point at port 8072, routing all external traffic
- **Service Discovery**: Netflix Eureka Server for dynamic service registration/discovery
- **Centralized Configuration**: Spring Cloud Config Server with Git backend for production, native fallback for dev
- **Database-per-Service**: Each business service owns its own MySQL database (accountsdb:3307, loansdb:3308, cardsdb:3309) with persistent Docker volumes
- **Event-Driven Messaging**: Spring Cloud Stream with Kafka (default) or RabbitMQ for async inter-service communication
- **Synchronous Communication**: Feign declarative REST clients for service-to-service calls (Accounts → Cards, Accounts → Loans)
- **CQRS-lite**: Separate read models per service; aggregation done at the service layer (not a dedicated query service)

**Architecture Diagram** (mental model):
```
Client → GatewayServer:8072 (OAuth2, Rate Limit, Circuit Breaker)
    → Eureka (service discovery)
    → Accounts:8080 / Cards:9000 / Loans:8090 (business services)
    → ConfigServer:8071 (centralized config via Git)
    → RabbitMQ (event bus + config refresh)
    → Message:9010 (async email/SMS via Kafka/RabbitMQ)
    → Redis (session/cache)
    → Keycloak:7080 (OAuth2/OIDC)
    → Tempo/Grafana/Loki/Prometheus (observability)
```

### Q2: Why is the gateway the only service using WebFlux (reactive stack) while all other services use Spring MVC?

**Expected Answer**: The gateway is the single entry point handling high-concurrency request routing — reactive non-blocking I/O is ideal for this proxy/forwarding role where threads shouldn't block waiting for downstream services. Business services (accounts, cards, loans) use blocking MVC because:
- They primarily do synchronous CRUD operations against MySQL (blocking JDBC)
- The complexity of reactive programming isn't justified for internal services with lower concurrency requirements
- Mixing stacks is acceptable — Spring Cloud Gateway is designed as a reactive gateway regardless of downstream service stacks
- If business services needed reactive support, they could use R2DBC with reactive MySQL drivers

### Q3: Explain the DNS prefix routing pattern used in the gateway.

**Expected Answer**: All external traffic follows the pattern `/goutos/bank/{service}/**`. The gateway's `RouteConfig` strips this prefix and forwards to the appropriate upstream service:

```
Incoming:  /goutos/bank/accounts/api/fetch?mobileNumber=123
    ↓ Path rewrite strips "/goutos/bank/accounts"
    ↓ Forward to lb://ACCOUNTS
Upstream:  /api/fetch?mobileNumber=123
```

The `createRoute()` method uses a `Function<PredicateSpec, Buildable<Route>>` factory pattern, making it trivial to add new services by calling `createRoute("newservice")`.

---

## 2. Microservices Patterns

### Q4: What is the rationale behind the database-per-service pattern, and what are its trade-offs?

**Expected Answer**:

**Benefits**:
- **Loose coupling**: Each service owns its data schema; changes don't affect other services
- **Independent scaling**: Each database can be scaled, tuned, and backed up independently
- **Technology freedom**: Each service could theoretically use a different database (though this project standardizes on MySQL)
- **Failure isolation**: A database issue in one service doesn't cascade to others

**Trade-offs**:
- **Data consistency**: No distributed transactions; eventual consistency via events (Saga pattern not implemented here)
- **Cross-service queries**: Requires data duplication or aggregation (solved here via Feign clients in `CustomersServiceImpl`)
- **Operational overhead**: More databases to manage, monitor, and back up
- **Join complexity**: Cannot perform cross-service SQL joins; must aggregate in application code

### Q5: Why does the Accounts service use a logical FK (`customer_id` column) instead of a JPA `@ManyToOne` relationship?

**Expected Answer**: This is a deliberate microservices design choice:
- **Database independence**: Each service should own its schema; a JPA `@ManyToOne` would create a physical FK constraint that couples the schemas
- **Service boundary enforcement**: The `customer_id` is stored in `accounts` table but managed by the Accounts service; there's no DB-level enforcement that the customer exists
- **Eventual consistency**: In a microservices architecture, enforcing referential integrity at the DB level would require distributed transactions (2PC), which is an anti-pattern
- **Application-level validation**: The service code is responsible for validating that referenced entities exist

### Q6: Explain the interface-first service design pattern used here.

**Expected Answer**: Every service layer follows the pattern:
- `I{Service}Service.java` — Interface defining the contract
- `{Service}ServiceImpl.java` — Implementation in `impl/` subpackage

Benefits:
- **Testability**: Easy to mock the service layer in controller tests
- **Decoupling**: Consumers depend on abstractions, not implementations
- **Swappability**: Multiple implementations could exist (e.g., for different environments)
- **Convention consistency**: All services follow the same pattern, reducing cognitive load

---

## 3. API Gateway & Routing

### Q7: How does the gateway handle cross-cutting concerns?

**Expected Answer**: The gateway implements several cross-cutting concerns:

| Concern | Implementation |
|---------|---------------|
| **Authentication** | OAuth2 Resource Server (Keycloak JWT) — all routes require bearer token |
| **Rate Limiting** | Redis-based rate limiter (1 req/sec per user key) via `RedisRateLimiter` |
| **Circuit Breaking** | Resilience4j circuit breaker per route with fallback to `/contactSupport` |
| **Retry** | 3 retries with exponential backoff (100ms–1000ms, multiplier 2) for GET requests |
| **Tracing** | Request/response filters inject `eazybank-correlation-id` header |
| **Response Headers** | `X-Response-Time` added to every response |
| **Path Rewriting** | Strips `/goutos/bank/{service}` prefix before forwarding |

### Q8: Explain the correlation ID tracing pattern implemented in the gateway.

**Expected Answer**: The gateway implements distributed tracing via correlation ID propagation:

1. **RequestTraceFilter** (pre-filter, Order 1): Checks for `eazybank-correlation-id` header; generates a UUID if missing
2. **ResponseTraceFilter** (post-filter): Adds the correlation ID to response headers so clients can trace their requests
3. **Downstream propagation**: The correlation ID is passed to all downstream services via Feign client `@RequestHeader` parameters
4. **OpenTelemetry integration**: The OTEL javaagent automatically picks up the correlation ID and includes it in trace spans
5. **Log correlation**: Log pattern `%5p [${spring.application.name},%X{trace_id},%X{span_id}]` includes trace context

This enables end-to-end request tracing across: Gateway → Accounts → Cards/Loans → Database.

### Q9: Why is the gateway's `main` method package-private?

**Expected Answer**: This is a security hardening measure. By making the `main` method package-private, the application cannot be accidentally launched from outside its package. The Spring Boot application is still fully functional because Spring Boot's component scanning and auto-configuration work at the class level, not the method level. This is a non-standard but deliberate choice documented in the project's known issues.

---

## 4. Service Discovery & Configuration

### Q10: How does service discovery work in this architecture?

**Expected Answer**:
1. **Eureka Server** runs on port 8070 in standalone mode (`register-with-eureka: false`, `fetch-registry: false`)
2. All business services and the gateway register with Eureka as clients (`@EnableEurekaClient` implicitly via `spring-cloud-starter-netflix-eureka-client`)
3. The gateway uses `lb://{SERVICE}` URIs in route definitions, which resolve to registered instances via Eureka
4. Services discover each other by logical name (e.g., `ACCOUNTS`, `CARDS`, `LOANS`) rather than hardcoded IPs
5. The gateway has discovery locator **disabled** (`enabled: false`) — routes are explicitly configured rather than auto-discovered

**Why explicit routes over discovery locator?**
- More control over which services are exposed
- Ability to apply per-route resilience configurations
- Security: prevents accidental exposure of internal services

### Q11: Explain the ConfigServer architecture and its role.

**Expected Answer**:
- **ConfigServer** (port 8071) serves as a centralized configuration repository
- **Git backend** (production): Configuration files stored in a Git repository under `/.config` path; `clone-on-start` and `force-pull` ensure fresh config on startup
- **Native fallback** (dev): `classpath:/shared` and `classpath:/config` directories (non-functional in this setup)
- **Security**: Basic auth (`CONFIG_SERVER_USER`/`CONFIG_SERVER_PASSWORD`); health endpoints publicly accessible; symmetric encryption support
- **Scope**: Currently serves **only datasource credentials** (URL, username, password); all other config is local to each service
- **Dynamic refresh**: Combined with Spring Cloud Bus (RabbitMQ), config changes can be pushed to all services without restart via `/actuator/busrefresh`

### Q12: What happens if ConfigServer is unavailable when a service starts?

**Expected Answer**: This is a critical failure scenario:
- Services configured with `spring.cloud.config.uri` will fail to start if ConfigServer is unreachable
- The bootstrap phase requires ConfigServer connection before the application context loads
- **Mitigation strategies** (not all implemented):
  - ConfigServer high availability (multiple instances)
  - Local config fallback with `spring.cloud.config.fail-fast: false`
  - Native profile fallback (currently non-functional in this project — noted as CQ-007)
  - Caching previously fetched configuration locally

---

## 5. Security & OAuth2

### Q13: Explain the OAuth2 security architecture.

**Expected Answer**:
- **Keycloak** (port 7080) serves as the Identity Provider (IdP) implementing OAuth2/OIDC
- **Gateway** acts as the OAuth2 Resource Server, validating JWT tokens against Keycloak's JWK Set endpoint
- **Security flow**:
  1. Client obtains JWT from Keycloak via client credentials grant
  2. Client includes `Authorization: Bearer <token>` in API requests
  3. Gateway validates the JWT signature using Keycloak's public key
  4. Gateway extracts roles and enforces RBAC at the route level
  5. Authenticated requests are forwarded to backend services

### Q14: How is role-based access control implemented?

**Expected Answer**: In the gateway's `SecurityConfig`:
```java
serverHttpSecurity.authorizeExchange(exchanges -> exchanges
    .pathMatchers(HttpMethod.GET).permitAll()  // Read access for all
    .pathMatchers("/goutos/bank/accounts/**").hasRole("ACCOUNTS")
    .pathMatchers("/goutos/bank/cards/**").hasRole("CARDS")
    .pathMatchers("/goutos/bank/loans/**").hasRole("LOANS")
);
```

- GET requests are permitted without specific roles (read-only access)
- POST/PUT/DELETE require specific roles (ACCOUNTS, CARDS, LOANS)
- Role extraction uses `KeycloakRoleConverter` which maps Keycloak realm roles to Spring Security authorities
- The `ReactiveJwtAuthenticationConverterAdapter` bridges JWT claims to Spring Security's authentication model

### Q15: What security improvements would you recommend?

**Expected Answer**:
1. **mTLS between services**: Currently services communicate over plain HTTP internally; mutual TLS would prevent lateral movement attacks
2. **Token propagation**: Backend services should validate tokens rather than trusting the gateway blindly (defense in depth)
3. **OAuth2 scopes**: Implement fine-grained scopes beyond roles (e.g., `accounts:read`, `accounts:write`)
4. **Secrets management**: Move from `.env` files to a secrets manager (HashiCorp Vault, AWS Secrets Manager)
5. **API rate limiting per client**: Currently rate limits by user header; should also consider per-client/IP limits
6. **Audit logging**: Log all authentication events and authorization decisions
7. **Key rotation**: Implement automated JWT signing key rotation

---

## 6. Data Architecture & Persistence

### Q16: Why separate MySQL databases per service instead of a shared database?

**Expected Answer**:
- **True isolation**: Each service has complete ownership of its data schema
- **Independent deployment**: Schema changes in one service don't require coordination with others
- **Performance isolation**: Heavy queries in one service don't impact others
- **Compliance**: Different data domains may have different retention/encryption requirements
- **Trade-off**: Cross-service queries require application-level aggregation (Feign calls), adding latency

### Q17: Explain the Flyway migration strategy.

**Expected Answer**:
- Each service has its own `db/migration` directory with versioned SQL scripts (e.g., `V1__init_schema.sql`)
- `ddl-auto: none` ensures JPA doesn't auto-modify the schema — Flyway is the sole schema manager
- `baseline-on-migrate: true` allows starting from an existing database
- Production databases use persistent Docker volumes for data survival across restarts
- **Limitation**: Currently only `V1` migrations exist; no rollback strategy documented

### Q18: What is the `communication_sw` boolean field in the Accounts entity?

**Expected Answer**: This field tracks whether the async notification (email/SMS) for an account has been processed:
1. When an account is created, an `AccountsMsgDto` event is published to Kafka/RabbitMQ via `StreamBridge`
2. The Message service processes the event and publishes a completion event to `communication-sent`
3. The Accounts service consumes this completion event via `AccountsFunctions.updateCommunication()` and sets `communicationSw = true`
4. This implements a simple acknowledgment pattern for guaranteed delivery tracking

---

## 7. Inter-Service Communication

### Q19: Compare the synchronous and asynchronous communication patterns used.

**Expected Answer**:

| Aspect | Synchronous (Feign) | Asynchronous (StreamBridge) |
|--------|---------------------|---------------------------|
| **Use case** | Customer details aggregation | Email/SMS notifications |
| **Protocol** | HTTP REST | Kafka/RabbitMQ message broker |
| **Coupling** | Tight (caller waits for response) | Loose (fire-and-forget) |
| **Reliability** | Circuit breaker + retry | Message broker persistence |
| **Latency** | Blocking (adds to response time) | Non-blocking (background) |
| **Error handling** | Immediate failure/fallback | Retry via broker, DLQ recommended |
| **Implementation** | `@FeignClient` + `@RequestHeader` | `StreamBridge.send()` + `@Bean` consumers |

### Q20: How are Feign clients configured for resilience?

**Expected Answer**: Feign clients in this project are configured with:
- **Circuit breaker**: `resilience4j` with sliding window of 10, 50% failure threshold, 10s wait duration
- **Retry**: 3 max attempts, exponential backoff (100ms–1000ms, 2x multiplier)
- **Time limiter**: 4s timeout per call
- **Correlation ID propagation**: Each Feign method accepts `@RequestHeader("eazybank-correlation-id")` and passes it downstream
- **Fallback**: `CardsFallback` and `LoansFallback` classes handle circuit-open scenarios (return empty/default responses)

### Q21: What is the `eazybank-correlation-id` header, and why is it required?

**Expected Answer**: This is a custom correlation/trace ID header that:
- Is generated by the gateway's `RequestTraceFilter` if not present in the incoming request
- Is propagated through all service-to-service calls for distributed tracing
- Is **required** on Feign client calls to Cards and Loans services (uniquely, these services enforce its presence)
- Enables end-to-end request tracing in the observability stack (Tempo/Grafana)
- Is included in log output via the configured log pattern

---

## 8. Event-Driven Architecture

### Q22: Describe the event-driven messaging flow.

**Expected Answer**:
```
1. Account created → AccountsServiceImpl calls streamBridge.send("sendCommunication-out-0", accountsMsgDto)
2. Kafka/RabbitMQ receives message on "send-communication" topic
3. Message service consumes from "emailsms-in-0" binding
4. Function composition processes: email() → sms() pipeline
5. Output published to "emailsms-out-0" → "communication-sent" topic
6. Accounts service consumes via AccountsFunctions.updateCommunication() Consumer
7. Accounts updates communicationSw = true
```

### Q23: Why is the default binder Kafka but the project emphasizes RabbitMQ?

**Expected Answer**: This is a noted inconsistency (EVT-006 in known issues):
- Kafka is configured as the default binder in the Message service's `application.yml`
- RabbitMQ is emphasized in the project documentation and Docker Compose setup
- The `SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit` environment variable allows switching
- This likely reflects an evolution of the project — RabbitMQ was initially used, then Kafka was added as an alternative
- In production, consistency between documentation and configuration is critical to avoid confusion

### Q24: What message broker would you choose for this use case and why?

**Expected Answer**:
- **RabbitMQ**: Better for this use case because:
  - Native Spring AMQP integration (more mature in Spring ecosystem)
  - Built-in dead-letter exchanges for failed message handling
  - Easier operational management for a banking application
  - Already configured in Docker Compose as a core infrastructure service
  - Supports both push (consumer) and pull models
  
- **Kafka**: Better suited for:
  - High-throughput event streaming (100K+ events/sec)
  - Event sourcing / log-based architectures
  - Long-term event retention and replay
  
- For notification emails/SMS, the volume is low and reliability is critical → RabbitMQ is the better fit.

---

## 9. Resilience & Fault Tolerance

### Q25: Explain the Resilience4j configuration in this project.

**Expected Answer**: Three resilience patterns are configured:

**Circuit Breaker** (per Feign client):
- Sliding window: 10 requests
- Failure threshold: 50%
- Wait duration: 10 seconds (half-open state)
- Prevents cascading failures when a downstream service is down

**Retry** (per Feign client):
- Max attempts: 3
- Exponential backoff: 100ms initial, 1000ms max, 2x multiplier
- Only retries GET requests (idempotent operations)

**Rate Limiter** (gateway level):
- 10 requests per second per user key
- 1 second timeout for rate limit wait
- Redis-backed for distributed rate limiting across gateway instances

**Time Limiter**:
- 4-second timeout per Feign call
- Prevents thread exhaustion from slow downstream services

### Q26: What is the fallback behavior when a circuit breaker opens?

**Expected Answer**: When the circuit breaker opens (50% failure rate in sliding window of 10):
1. Subsequent requests immediately fail-fast without calling the downstream service
2. The fallback URI `forward:/contactSupport` is invoked (configured in `RouteConfig`)
3. After the wait duration (10s), the circuit transitions to half-open state
4. A single test request is allowed; if it succeeds, the circuit closes
5. Fallback classes (`CardsFallback`, `LoansFallback`) return empty/default DTOs for Feign-level fallbacks

### Q27: What happens during a Redis outage?

**Expected Answer**: Impact analysis:
- **Gateway rate limiting fails**: The `RedisRateLimiter` throws an exception; requests may be rejected or allowed depending on configuration (fail-open vs fail-closed)
- **Session management disrupted**: If Redis is used for session storage, authenticated sessions may be lost
- **Mitigation strategies**:
  - Circuit breaker on Redis connection
  - Local in-memory rate limiter as fallback
  - Redis Sentinel or Cluster for high availability
  - Graceful degradation (allow requests without rate limiting during Redis outage)

---

## 10. Observability & Monitoring

### Q28: Describe the observability stack and how it integrates.

**Expected Answer**: The GLPT (Grafana, Loki, Prometheus, Tempo) stack:

| Component | Role | Integration |
|-----------|------|-------------|
| **Tempo** (4318/3110) | Distributed tracing | OTEL javaagent auto-instruments all services |
| **Prometheus** (9090) | Metrics collection | Scrapes `/actuator/prometheus` every 10s |
| **Loki** (3100) | Log aggregation | Alloy agent collects Docker container logs |
| **Grafana** (3000) | Visualization | Connects to all three as data sources |
| **Alloy** | Log collector | Reads Docker socket, sends to Loki |
| **MinIO** | Object storage | S3 backend for Loki log persistence |

### Q29: How would you troubleshoot a slow API endpoint?

**Expected Answer**: Multi-step approach using the observability stack:
1. **Grafana dashboards**: Check Prometheus metrics for endpoint latency (`http_server_requests_seconds_*`)
2. **Distributed tracing**: Search Tempo for traces with high latency spans; identify which service/span is the bottleneck
3. **Log analysis**: Query Loki for errors or warnings in the affected service during the time window
4. **Database metrics**: Check connection pool stats (`db_connection_pool_*`) and query performance
5. **Circuit breaker state**: Check `/actuator/circuitbreakers` for state changes
6. **Correlation ID**: Use the `eazybank-correlation-id` from the client request to trace the full request path

### Q30: What key metrics would you monitor in production?

**Expected Answer**:
- **HTTP metrics**: Request rate, latency (p50/p95/p99), error rate per endpoint
- **Circuit breaker**: State transitions (closed→open), failure rates
- **Database**: Connection pool utilization, query latency, active connections
- **JVM**: Memory usage (heap/GC), thread count, CPU usage
- **Message broker**: Queue depth, consumer lag, message processing rate
- **Gateway**: Request rate, rate limiter rejections, upstream response times
- **Business metrics**: Account creation rate, notification delivery success rate

---

## 11. Containerization & Deployment

### Q31: Explain the Docker image building strategies used.

**Expected Answer**: Three methods are supported:

1. **Jib** (`mvn compile jib:dockerBuild`):
   - Fast, reproducible builds without Docker daemon
   - Layered builds for efficient caching
   - Base image: `eclipse-temurin:25-jre-alpine-3.21`
   - **Recommended** for most services

2. **Buildpacks** (`mvn spring-boot:build-image`):
   - Automatic layer detection
   - No Dockerfile needed
   - Spring-optimized image creation

3. **Custom Dockerfile** (accounts only):
   - Multi-stage build with JVM target (jlink) and GraalVM Native target
   - Full control over image layers
   - Optimized for production

4. **GraalVM Native** (`mvn -Pnative native:compile`):
   - Sub-second startup time
   - Minimal memory footprint
   - Requires GraalVM 25+ installed

### Q32: Describe the Kubernetes deployment architecture.

**Expected Answer**:
- **Helm charts** in `.docker/helm/` with multi-chart architecture:
  - `eazybank-common/`: Reusable templates (Deployment, Service, ConfigMap)
  - `eazybank-services/`: Individual service charts depending on common
  - `environments/`: Umbrella charts (dev-env, prod-env, qa-env)
  - `{infrastructure}/`: Grafana, Kafka, Keycloak, etc.
- **Namespace**: `microservices`
- **Service types**: LoadBalancer for external (gateway, Keycloak, Grafana, Prometheus); ClusterIP for internal
- **Persistent volumes**: MySQL databases, Grafana data, Keycloak data, MinIO
- **Resource limits**: 0.5 CPU cores, 512MB memory per service (enforced)
- **Health checks**: Readiness probes on `/actuator/health/readiness`

### Q33: How is the service startup order managed?

**Expected Answer**: Docker Compose `depends_on` with `condition: service_healthy`:
```
1. MinIO (Loki storage)
2. Observability (Prometheus, Tempo, Loki, Alloy, Grafana)
3. Cache & Identity (Redis, Keycloak)
4. Message Brokers (RabbitMQ, Kafka)
5. Databases (accountsdb, loansdb, cardsdb)
6. Service Discovery (Eureka Server)
7. Configuration Server (ConfigServer)
8. Business Services (Accounts, Cards, Loans, Message)
9. API Gateway (Gateway Server)
```

This ensures each service starts only after its dependencies are healthy.

---

## 12. CI/CD & Build Pipeline

### Q34: How would you design a CI/CD pipeline for this project?

**Expected Answer**:
```
Stage 1: Build & Test
  → mvn clean install (all modules)
  → mvn test (unit tests)
  → jacoco:report (coverage check — 80% threshold)

Stage 2: Docker Image Build
  → mvn compile jib:dockerBuild (per service)
  → Tag with version/SHA

Stage 3: Security Scan
  → Trivy/Grype vulnerability scan on images
  → Check for known CVEs in dependencies

Stage 4: Push to Registry
  → docker push to container registry

Stage 5: Deploy
  → kubectl apply -f k8s-generated/ (or helm upgrade)
  → Rolling update strategy
  → Health check verification

Stage 6: Smoke Tests
  → API health checks through gateway
  → Verify circuit breakers are closed
```

### Q35: How are environment-specific configurations managed?

**Expected Answer**:
- **`.env`** (git-committed): Base configuration with placeholders
- **`.env.local`** (git-ignored): Dev secrets (MySQL passwords, ConfigServer credentials)
- **`.env.prod`** (git-ignored): Production secrets
- **Docker Compose env_file stacking**: `.env` + `.env.${APP_ENV}` (right-to-left priority)
- **ConfigServer profiles**: `default` (dev, localhost DBs) vs `prod` (Docker/K8s service DBs)
- **Kubernetes**: ConfigMaps + Secrets for environment-specific values

---

## 13. Testing Strategy

### Q36: Describe the testing pyramid and current coverage.

**Expected Answer**:

**Current test structure**:
- **Application context tests** (`@SpringBootTest`): Verify Spring context loads correctly
- **Controller tests** (`@WebMvcTest`): Test endpoint mappings, validation, error responses
- **Service tests** (`@ExtendWith(MockitoExtension.class)`): Unit test business logic with mocked dependencies
- **Entity tests**: Verify getters/setters, equals/hashCode/toString, inheritance
- **Exception handler tests** (`@WebMvcTest(GlobalExceptionHandler.class)`): Test all exception paths
- **Gateway filter tests**: Test correlation ID generation and propagation

**Coverage targets**:
- Services: 80%+
- Controllers: 70%+
- Repositories: 50%+
- Exceptions: 100%
- Gateway filters: 80%+

**Gaps to address**:
- Repository layer tests (`@DataJpaTest`)
- Integration tests for Feign client communication
- Stream/messaging integration tests
- End-to-end tests through the gateway
- Performance/load tests

### Q37: How would you test the event-driven messaging flow?

**Expected Answer**:
1. **Unit tests**: Mock `StreamBridge`, verify `send()` is called with correct payload
2. **Integration tests**: Use Spring Cloud Stream's `TestBinder` to verify message flow without Kafka/RabbitMQ
3. **Contract tests**: Verify message payload structure matches what the consumer expects
4. **End-to-end tests**: Deploy to a test environment, trigger account creation, verify notification is sent
5. **Error scenario tests**: Simulate message broker unavailability, verify graceful degradation

---

## 14. Code Quality & Standards

### Q38: Explain the naming conventions and their rationale.

**Expected Answer**:
| Convention | Pattern | Example | Rationale |
|-----------|---------|---------|-----------|
| Service interfaces | `I{Service}Service` | `IAccountsService` | Clear distinction between interface and implementation |
| Implementations | `{Service}ServiceImpl` | `AccountsServiceImpl` | Consistent location in `impl/` package |
| DTOs | `{Entity}Dto` | `AccountsDto` | Located in shared `utils` module |
| Mappers | `{Entity}Mapper` | `AccountsMapper` | Static methods, target-mutation pattern |
| Exceptions | `{Resource}NotFoundException` | `CustomerNotFoundException` | Self-documenting error types |
| Feign clients | `{Service}FeignClient` | `CardsFeignClient` | Located in `service/client/` package |
| Constants | `{Service}Constants` | `AccountsConstants` | HTTP status codes, business constants |

### Q39: What is the mapper pattern used, and why was it chosen over MapStruct?

**Expected Answer**:
- **Static mapper pattern**: `AccountsMapper.mapToAccountsDto(Accounts, AccountsDto)` with manual field mapping
- **Target-mutation**: The target DTO is passed as a parameter and mutated, rather than creating a new instance
- **Advantages**: No additional dependency (MapStruct), full control over mapping logic, easy to debug
- **Disadvantages**: Verbose, error-prone (forgotten fields), no compile-time safety
- **MapStruct alternative**: Would reduce boilerplate with annotation-based code generation, but adds a processor dependency and can be harder to debug

### Q40: Why does the project use `java.util.Random` instead of `SecureRandom` for ID generation?

**Expected Answer**: This is a known issue (REL-002) with implications:
- `java.util.Random` uses a predictable PRNG (Linear Congruential Generator)
- `SecureRandom` uses cryptographically strong random number generation
- **Risk**: At scale, ID collisions become possible with `Random`
- **Recommendation**: Use `SecureRandom` for any security-sensitive or collision-critical ID generation, or switch to UUIDs/database auto-increment

---

## 15. Troubleshooting & Debugging

### Q41: A customer reports that the `/goutos/bank/accounts/api/fetch` endpoint returns 404. How do you troubleshoot?

**Expected Answer**: Systematic approach:
1. **Check gateway logs**: Is the request reaching the gateway? Check `X-Response-Time` header
2. **Verify Eureka registration**: Is the accounts service registered? Check Eureka dashboard at `:8070`
3. **Check route configuration**: Verify the route is defined in `RouteConfig`
4. **Test accounts service directly**: `curl localhost:8080/actuator/health` — is it running?
5. **Check database**: Is accountsdb healthy? `docker compose exec accountsdb mysqladmin ping`
6. **Check logs**: `docker compose logs accounts | grep ERROR`
7. **Verify path**: Ensure the request URL matches the route pattern exactly
8. **Check correlation ID**: If the request comes through the gateway, ensure `eazybank-correlation-id` is present

### Q42: How would you diagnose a circuit breaker that keeps opening?

**Expected Answer**:
1. **Check metrics**: `GET /actuator/prometheus` → `resilience4j_circuitbreaker_*` metrics
2. **Grafana dashboard**: Visualize circuit breaker state transitions
3. **Root cause analysis**:
   - Is the downstream service actually down? Check health endpoint
   - Is the database connection failing? Check connection pool metrics
   - Is there a network issue? Check DNS resolution between containers
   - Is the timeout too aggressive? Review time limiter configuration (4s)
4. **Logs**: Search for circuit breaker state change logs in the affected service
5. **Temporary mitigation**: Increase failure threshold or wait duration if legitimate traffic spike

---

## 16. Scalability & Performance

### Q43: How would you scale this platform for 10x traffic?

**Expected Answer**:
1. **Horizontal scaling**: Scale business services (accounts, cards, loans) behind the load balancer
   - Kubernetes: `kubectl scale deployment accounts --replicas=5`
   - Docker Compose: Limited support, Kubernetes preferred
2. **Database scaling**:
   - Read replicas for read-heavy workloads
   - Connection pool tuning (HikariCP)
   - Consider database sharding by customer segment
3. **Gateway scaling**: Multiple gateway instances behind an external load balancer
4. **Redis clustering**: For rate limiting and session management at scale
5. **Kafka partitioning**: Increase partitions for message service throughput
6. **Caching layer**: Implement Redis caching for frequently accessed data (not just sessions)
7. **CDN**: For static content if applicable
8. **Database connection pooling**: Tune HikariCP settings per service

### Q44: What are the bottlenecks in the current architecture?

**Expected Answer**:
1. **Single Eureka Server**: Single point of failure — should be clustered in production
2. **Single ConfigServer**: Same — no HA configuration
3. **Synchronous Feign calls**: Accounts service blocks while waiting for Cards and Loans responses
4. **No caching**: Business services don't cache frequently accessed data
5. **Single MySQL per service**: Write throughput limited to a single instance
6. **Gateway as bottleneck**: All traffic flows through a single gateway instance
7. **No connection pooling between services**: Each Feign call opens a new HTTP connection (unless configured otherwise)

---

## 17. Trade-offs & Technical Debt

### Q45: What are the key technical debt items in this project?

**Expected Answer** (referencing documented issues):

| Priority | Issue | Impact |
|----------|-------|--------|
| **Critical** | `.env.prod` with plaintext credentials | Security exposure |
| **Critical** | OAuth2 not integrated for service-to-service | Weak internal auth |
| **High** | Card number validation mismatch (12 vs 16 digits) | Broken updates |
| **High** | No error handling/retry in Message service | Silent notification failures |
| **High** | No dead-letter queue for failed messages | Data loss risk |
| **Medium** | ConfigServer native profile non-functional | Broken dev fallback |
| **Medium** | No Prometheus alerting rules | No proactive monitoring |
| **Low** | `main` methods are package-private | Non-standard, inconsistent |
| **Low** | Unused `CREDIT`/`DEBIT` constants in Cards | Dead code |
| **Low** | `updateLoan()`/`deleteLoan()` always return true | Dead code path |

### Q46: If you had to prioritize improvements, what would you tackle first?

**Expected Answer** (ordered by impact):
1. **Secrets management** (Critical): Move to HashiCorp Vault or AWS Secrets Manager
2. **Service-to-service auth** (Critical): Implement OAuth2 token propagation or mTLS
3. **Message service reliability** (High): Add error handling, retry logic, and DLQ
4. **Card validation fix** (High): Align validation regex with actual card number format
5. **Alerting** (Medium): Configure Prometheus alerting rules for circuit breakers, high latency, error rates
6. **Repository tests** (Medium): Add `@DataJpaTest` coverage for custom queries
7. **Redis caching** (Medium): Implement data caching in business services
8. **Eureka HA** (Medium): Configure Eureka Server cluster for production

### Q47: What would you do differently if starting this project from scratch?

**Expected Answer**:
1. **Use Spring Boot 3.x LTS** instead of 4.0.5 for better production maturity and longer support
2. **Standardize on one message broker** (RabbitMQ or Kafka, not both)
3. **Implement proper secrets management** from day one (Vault, AWS Secrets Manager)
4. **Add API versioning** (`/api/v1/`) from the start
5. **Implement proper health checks** with liveness/readiness/startup probes
6. **Add distributed tracing** with W3C trace context propagation standard
7. **Use MapStruct** instead of manual mappers for compile-time safety
8. **Implement proper pagination** on all list endpoints
9. **Add OpenAPI documentation** generation and enforcement
10. **Use `SecureRandom`** for all ID generation
11. **Implement proper logging correlation** with MDC from the start
12. **Add integration test framework** (Testcontainers for databases, WireMock for external APIs)

---

## 18. Scenario-Based Questions

### Q48: A new microservice ("investments") needs to be added. Walk me through the steps.

**Expected Answer**:
1. **Create Maven module**: Add `<module>investments</module>` to parent `pom.xml`
2. **Create package structure**: `controller/`, `service/`, `service/impl/`, `entity/`, `mapper/`, `repository/`, `exception/`, `audit/`, `constants/`
3. **Create application class**: `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition`
4. **Create `application.yml`**: ConfigServer client config, datasource, Eureka registration
5. **Add ConfigServer config**: `investments.yml` and `investments-prod.yml` in `.config/`
6. **Create Flyway migration**: `V1__create_investments_table.sql`
7. **Implement CRUD**: Entity, Repository, Mapper, Service, Controller
8. **Add exception handling**: `GlobalExceptionHandler`, custom exceptions
9. **Add audit**: `AuditAwareImpl` returning `"INVESTMENTS_MS"`
10. **Update gateway**: Add route in `RouteConfig.createRoute("investments")`
11. **Update Docker Compose**: Add service definition with proper `depends_on`
12. **Add Helm chart**: Create under `.docker/helm/eazybank-services/investments/`
13. **Write tests**: Application context, controller, service, entity, exception handler
14. **Build and verify**: `mvn clean install`, run tests, verify Docker image builds

### Q49: How would you handle a scenario where the Cards service is down and the gateway receives requests for card data?

**Expected Answer**:
1. **Circuit breaker opens** after 50% failure rate in sliding window of 10
2. **Fallback URI** (`forward:/contactSupport`) is invoked at the gateway level
3. **Feign fallback** (`CardsFallback`) returns a default/empty response at the service level
4. **Client receives** either the fallback response or a 503 from the gateway
5. **Monitoring**: Prometheus alerts fire on circuit breaker state change
6. **Ops response**: Check Card service logs, database connectivity, restart if needed
7. **Recovery**: Circuit breaker transitions to half-open, tests with single request, closes if successful

### Q50: How would you migrate this platform from Docker Compose to Kubernetes in production?

**Expected Answer**:
1. **Assess current state**: Document all services, dependencies, resource requirements
2. **Create namespace**: `kubectl create namespace microservices`
3. **Set up infrastructure**: Deploy databases with PVCs, message brokers, Redis, Keycloak
4. **Create ConfigMaps**: For each service's `application.yml` configuration
5. **Create Secrets**: For database passwords, encryption keys, OAuth2 credentials
6. **Deploy services**: Using Helm charts or pre-generated manifests from `.docker/k8s-generated/`
7. **Configure ingress**: External LoadBalancer for gateway at port 8072
8. **Set up monitoring**: Deploy Prometheus, Grafana, Loki, Tempo in K8s
9. **Configure health checks**: Liveness and readiness probes for all services
10. **Test**: Verify all inter-service communication, event-driven messaging, and failover
11. **DNS**: Point external DNS to gateway LoadBalancer IP
12. **CI/CD**: Update pipeline to deploy via `helm upgrade` or `kubectl apply`

---

## Appendix: Quick Reference

### Key Ports
| Service | Port | External? |
|---------|------|-----------|
| Gateway | 8072 | ✅ |
| Eureka | 8070 | ✅ |
| ConfigServer | 8071 | ✅ |
| Keycloak | 7080 | ✅ |
| Grafana | 3000 | ✅ |
| Prometheus | 9090 | ✅ |
| Accounts | 8080 | ❌ |
| Loans | 8090 | ❌ |
| Cards | 9000 | ❌ |
| Message | 9010 | ❌ |

### Key Technologies
- **Java 25** | **Spring Boot 4.0.5** | **Spring Cloud 2025.1.1**
- **Maven** (multi-module) | **MySQL** (per-service) | **Flyway** (migrations)
- **Eureka** (discovery) | **Spring Cloud Gateway** (WebFlux)
- **Feign** (sync calls) | **Spring Cloud Stream** (async events)
- **Kafka/RabbitMQ** (messaging) | **Redis** (cache/rate-limit)
- **Keycloak** (OAuth2/OIDC) | **Resilience4j** (circuit breaker/retry)
- **OpenTelemetry** (tracing) | **Prometheus/Grafana/Loki/Tempo** (observability)
- **Jib/Buildpacks/GraalVM** (containerization) | **Helm** (K8s deployment)

---

*Generated from [README.md](README.md) and [AGENTS.md](AGENTS.md) — EazyBank Microservices Platform v4.1*