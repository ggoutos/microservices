# AGENTS.md - AI Agent Guidance for EazyBank Microservices Platform

> **Comprehensive Documentation** - Complete guide for developing, building, and deploying the EazyBank microservices platform with Spring Boot 4.0.5, Spring Cloud 2025.1.1, and Java 25.

---

## Table of Contents

1. [Quick Reference](#1-quick-reference)
2. [Architecture Overview](#2-architecture-overview)
3. [Technology Stack](#3-technology-stack)
4. [Service Specifications](#4-service-specifications)
5. [Development Workflows](#5-development-workflows)
6. [Build & Deployment](#6-build--deployment)
7. [Configuration Management](#7-configuration-management)
8. [Data Model](#8-data-model)
9. [API Reference](#9-api-reference)
10. [Exception Handling](#10-exception-handling)
11. [Testing Guide](#11-testing-guide)
12. [Security](#12-security)
13. [Monitoring & Observability](#13-monitoring--observability)
14. [Troubleshooting](#14-troubleshooting)
15. [Extending the Platform](#15-extending-the-platform)
16. [Known Issues & Technical Debt](#16-known-issues--technical-debt)

---

## 1. Quick Reference

### Service Ports

| Service | Port | Purpose |
|---------|------|---------|
| **eurekaserver** | 8070 | Service discovery & registration |
| **configserver** | 8071 | Centralized configuration |
| **gatewayserver** | 8072 | API Gateway (reverse proxy) |
| **accounts** | 8080 | Customer accounts management (internal, not exposed) |
| **loans** | 8090 | Loan management (internal, not exposed) |
| **cards** | 9000 | Credit/debit cards management (internal, not exposed) |
| **message** | 9010 | Event-driven messaging (email/SMS notifications) (internal, not exposed) |
| **rabbitmq** | 5672, 15672 | Message broker (config refresh, event communication) |
| **redis** | 6379 | Redis cache (session, rate limiting) |
| **keycloak** | 7080 | Identity & Access Management (OAuth2/OIDC) |
| **prometheus** | 9090 | Metrics collection & visualization |
| **grafana** | 3000 | Observability dashboards (Loki, Prometheus, Tempo) |
| **tempo** | 3110, 4318 | Distributed tracing backend & OTEL receiver |
| **loki** | 3100 | Log aggregation (read: 3101, write: 3102) |
| **accountsdb** | 3307 | MySQL database (accounts) |
| **loansdb** | 3308 | MySQL database (loans) |
| **cardsdb** | 3309 | MySQL database (cards) |

### Build Commands Cheat Sheet

```bash
# Local Development
mvn clean install              # Build all services (including utils, eurekaserver)
mvn spring-boot:run            # Run current service
mvn test                       # Run tests

# Docker Images (all services)
mvn compile jib:dockerBuild    # Jib build → ggoutos/{service}:jib
mvn spring-boot:build-image   # Buildpacks → ggoutos/{service}:spring
mvn -Pnative native:compile   # Native image (requires GraalVM)

# Full Stack (includes Eureka Server)
cd .docker && docker compose up --build              # Dev
cd .docker && docker compose --env-file .env.prod up # Prod
```

### API Endpoint Patterns

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/create?mobileNumber={}` | Create resource | OAuth2 JWT |
| GET | `/api/fetch?mobileNumber={}` | Fetch by mobile number | OAuth2 JWT |
| PUT | `/api/update` | Update resource (body) | OAuth2 JWT |
| DELETE | `/api/delete?mobileNumber={}` | Delete by mobile number | OAuth2 JWT |

**Note**: All endpoints require OAuth2 JWT bearer token from Keycloak (resource server mode)

### Gateway Routing Pattern

All external traffic goes through the gateway at port 8072:

```
Incoming:  /goutos/bank/{service}/**
           ↓  Path rewrite strips prefix
           ↓  Forward to lb://{SERVICE} (via Eureka)
Upstream:  /**
```

Example: `http://localhost:8072/goutos/bank/accounts/api/fetch?mobileNumber=1234567890` → `accounts:8080/api/fetch?mobileNumber=1234567890`

### URLs

| Service | Swagger UI | Actuator Health | Eureka Registration | Port Status |
|---------|------------|-----------------|---------------------|------------|
| accounts | N/A (internal) | http://localhost:8080/actuator/health | ✅ (client) | Internal |
| cards | N/A (internal) | http://localhost:9000/actuator/health | ✅ (client) | Internal |
| loans | N/A (internal) | http://localhost:8090/actuator/health | ✅ (client) | Internal |
| gatewayserver | N/A | http://localhost:8072/actuator/health | ✅ (client) | 8072 |
| configserver | N/A | http://localhost:8071/actuator/health | ❌ (standalone) | 8071 |
| eurekaserver | http://localhost:8070 | http://localhost:8070/actuator/health | ✅ (self) | 8070 |
| **grafana** | **http://localhost:3000** | **N/A** | **N/A** | **3000** |
| **prometheus** | **http://localhost:9090** | **N/A** | **N/A** | **9090** |
| **keycloak** | **http://localhost:7080** | **N/A** | **N/A** | **7080** |

**Note**: All services use MySQL databases with persistent volumes. Circuit breaker, retry, and rate limiter patterns enabled via Resilience4j. OpenTelemetry distributed tracing sends traces to Tempo (port 4318). Logs aggregated via Loki. OAuth2 JWT authentication via Keycloak.

---

## 2. Architecture Overview

### System Context

```
┌───────────────────────────────────────────────────────────────────────┐
│                     EazyBank Microservices Platform                   │
│                                                                       │
│                        ┌─────────────────┐                            │
│                        │  GatewayServer  │                            │
│                        │     :8072       │                            │
│                        │  (WebFlux/Reactive)                          │
│                        │  [OAuth2 JWT]   │                            │
│                        │  [Trace Filters]│                            │
│                        │  [Resilience4j] │                            │
│                        └────────┬────────┘                            │
│                                 │                                     │
│         ┌───────────────────────┼─────────────────────┐               │
│         │                       │                     │               │
│  ┌──────▼───────┐     ┌─────────▼──────┐     ┌────────▼───────┐       │
│  │   Accounts   │     │    Loans       │     │    Cards       │       │
│  │   :8080      │     │    :8090       │     │    :9000       │       │
│  │   (MySQL)    │     │   (MySQL)      │     │   (MySQL)      │       │
│  │   [Feign]    │────▶│   [Client]     │     │   [Client]     │       │
│  │ [CircuitBr]  │     │ [CircuitBr]    │     │ [CircuitBr]    │       │
│  └──────┬───────┘     └────────────────┘     └────────────────┘       │
│         │                                                             │
│         │ [StreamBridge - publish account events]                     │
│         ▼                                                             │
│  ┌──────────────────────────────────┐                                 │
│  │      RabbitMQ (Event Bus)        │                                 │
│  │        :5672/:15672              │                                 │
│  └─┬───────────────────────────────┬┘                                 │
│    │                               │                                  │
│    │ send-communication        communication-sent                     │
│    │ (account events)          (notifications sent)                   │
│    ▼                               ▼                                  │
│  ┌──────────────────────┐  ┌──────────────────────┐                   │
│  │  Message Service     │  │   Accounts Service   │                   │
│  │     :9010            │  │   (receives acks)    │                   │
│  │ (Event Processor)    │  │                      │                   │
│  │ [email|sms]          │  │ updateCommunication  │                   │
│  └──────────────────────┘  └──────────────────────┘                   │
│                                                                       │
│         ┌──────────────────┐                ┌──────────────┐          │
│         │  ConfigServer    │                │  Eureka      │          │
│         │     :8071        │                │  Server      │          │
│         │   (Git Backend)  │                │  :8070       │          │
│         └────────┬─────────┘                └──────────────┘          │
│                  │                                                    │
│    [Dynamic Config via Bus Refresh]                                   │
│                  │                                                    │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                   Observability Stack                           │  │
│  │                                                                 │  │
│  │  Keycloak :7080 ──▶  Redis :6379                                │  │
│  │      ▲                    │                                     │  │
│  │      │                    └──────────────┐                      │  │
│  │  [OAuth2 JWT from all services]         │                       │  │
│  │                                          ▼                      │  │
│  │  Prometheus :9090  ◀───  Grafana :3000  ◀───  Loki :3100        │  │
│  │        ▲                                          │             │  │
│  │        │                            ┌────────────┘              │  │
│  │        │                            ▼                           │  │
│  │        │                      Tempo :3110/4318                  │  │
│  │        │                    (OTEL Collector)                    │  │
│  │        └────────────────────────────┘                           │  │
│  │              (OpenTelemetry Traces from all services)           │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

- **Microservices Pattern**: Each service independently deployable with dedicated database
- **API Gateway**: Spring Cloud Gateway (WebFlux-based) as single entry point with circuit breaker & resilience
- **Service Discovery**: Eureka Server for service registration and discovery
- **Centralized Configuration**: Spring Cloud Config Server with Git backend (prod) / Native fallback (dev)
- **Event-Driven Config Refresh**: RabbitMQ message bus for distributed configuration updates
- **Database-per-Service**: MySQL for all services with Flyway schema migrations (separate ports: 3307, 3308, 3309) with persistent volumes
- **API-First Design**: OpenAPI/Swagger documentation on all business services
- **Interface-First Services**: Service layer exposes interfaces, implementations in `impl/` subpackage
- **Declarative REST Clients**: Feign clients for inter-service communication (Accounts → Cards/Loans)
- **Distributed Tracing**: OpenTelemetry (OTEL javaagent) sends traces to Tempo; logs aggregated via Loki; metrics via Prometheus
- **OAuth2 Security**: Keycloak integration for JWT-based API authentication (resource server mode)
- **Resilience Patterns**: Circuit breaker, retry, and rate limiter via Resilience4j on all services
- **Caching Layer**: Redis for session/cache management (gateway)
- **Observability Stack**: Grafana dashboards for centralized monitoring and visualization

### Cross-Service Communication

**Current State**: Accounts service has Feign clients to communicate with Cards and Loans services.

```java
// Feign Client example
@FeignClient(name = "cards")
public interface CardsFeignClient {
    @GetMapping(value = "/api/fetch", consumes = "application/json")
    CardsDto fetchCardDetails(
        @RequestHeader(name = "eazybank-correlation-id", required = true) String correlationId,
        @RequestParam String mobileNumber);
}

@FeignClient(name = "loans")
public interface LoansFeignClient {
    @GetMapping(value = "/api/fetch", consumes = "application/json")
    LoansDto fetchLoanDetails(
        @RequestHeader(name = "eazybank-correlation-id", required = true) String correlationId,
        @RequestParam String mobileNumber);
}
```

**Usage in Service Layer**:
```java
@RequiredArgsConstructor
@Service
public class CustomersServiceImpl implements ICustomersService {
    private final CardsFeignClient cardsFeignClient;
    private final LoansFeignClient loansFeignClient;
    
    // Use Feign clients for inter-service calls with correlation ID
    public CustomerDetailsDto fetchCustomerDetails(String mobileNumber, String correlationId) {
        CardsDto cards = cardsFeignClient.fetchCardDetails(correlationId, mobileNumber);
        LoansDto loans = loansFeignClient.fetchLoanDetails(correlationId, mobileNumber);
        // ... aggregate data
    }
}
```

### Gateway Tracing Pattern

The gateway server implements a correlation ID tracing pattern:

1. **RequestTraceFilter** (Pre-filter, Order 1): Generates UUID if `eazybank-correlation-id` header is missing
2. **ResponseTraceFilter** (Post-filter): Adds correlation ID to response headers
3. **Downstream Propagation**: Cards and Loans services require this header on `/api/fetch` endpoints

---

## 3. Technology Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 25 | Runtime & compilation |
| **Maven** | 3.9+ | Build automation (multi-module project) |
| **Spring Boot** | 4.0.5 | Application framework |
| **Spring Cloud** | 2025.1.1 | Microservices patterns (Config, Bus, Gateway) |
| **Spring Data JPA** | Included | Data persistence with Hibernate |
| **Flyway** | Included | Database migration tool |
| **Spring WebFlux** | Included | Reactive gateway (gatewayserver only) |

### Dependencies (Managed in Parent POM)

```xml
<!-- Core Starters -->
spring-boot-starter-webmvc          # REST APIs (business services)
spring-boot-starter-webflux         # Reactive APIs (gatewayserver)
spring-boot-starter-validation      # Jakarta Bean Validation
spring-boot-starter-actuator        # Health checks, metrics, monitoring
spring-boot-starter-data-jpa        # Data persistence with Hibernate
spring-boot-starter-security        # Security infrastructure
spring-boot-starter-oauth2-resource-server  # OAuth2 resource server (gateway)
spring-boot-starter-data-redis      # Redis client (gateway caching)

<!-- Spring Cloud -->
spring-cloud-starter-config         # Centralized configuration
spring-cloud-starter-bus-amqp       # Config refresh via RabbitMQ
spring-cloud-starter-netflix-eureka-client  # Service discovery
spring-cloud-starter-openfeign      # Declarative REST clients
spring-cloud-starter-gateway-server-webflux # API Gateway (gatewayserver)

<!-- Resilience & Observability -->
io.github.resilience4j (circuit-breaker, retry, timelimiter, ratelimiter)  # Fault tolerance
io.opentelemetry.javaagent          # OTEL traces (auto-instrumentation)
io.micrometer.micrometer-registry-prometheus  # Prometheus metrics export

<!-- Database Migration -->
spring-boot-starter-flyway          # Flyway database migrations
flyway-mysql                        # Flyway MySQL support

<!-- Documentation -->
springdoc-openapi-starter-webmvc-ui  # v3.0.2 - Swagger UI

<!-- Development -->
spring-boot-devtools                # Hot reload (runtime)
lombok                              # Boilerplate reduction (compile-time)

<!-- Databases -->
mysql-connector-j                   # MySQL driver (runtime)
spring-boot-starter-test            # Test frameworks (JUnit, MockMvc, etc.)
spring-boot-starter-actuator-test   # Actuator test utilities

<!-- Docker Plugins -->
jib-maven-plugin                    # v3.5.1 - Container image building
spring-boot-maven-plugin            # Buildpacks image creation
native-maven-plugin                 # GraalVM native compilation

<!-- Code Coverage -->
jacoco-maven-plugin                 # v0.8.14 - Code coverage (80% minimum threshold)
```

### Why These Technologies

- **Spring Boot 4.0.5**: Latest stable with Jakarta EE 10 support, improved performance, security hardening
- **Spring Cloud 2025.1.1**: Compatible with Boot 4.0.5, provides Config Server, Bus, Eureka, Feign, and Gateway patterns
- **Java 25**: Latest with enhanced pattern matching, records, and virtual threads support
- **Flyway**: Schema version control and migration management for production databases with persistent volumes
- **Eureka**: Service discovery and registration for dynamic microservice environments
- **Feign**: Declarative REST clients for simplified inter-service communication
- **Spring Cloud Gateway**: Reactive API gateway with custom filter support, circuit breaker, and resilience
- **Jib**: Fast, reproducible Docker builds without Docker daemon dependency
- **GraalVM Native**: Sub-second startup, reduced memory footprint for production
- **Resilience4j**: Fault tolerance patterns (circuit breaker, retry, rate limiting, time limiter)
- **OpenTelemetry**: Vendor-neutral distributed tracing and observability via OTEL javaagent
- **Prometheus/Grafana**: Metrics collection and visualization for operational insights
- **Loki**: Scalable log aggregation system (integrates with Grafana)
- **Tempo**: Distributed tracing backend for OTEL traces
- **Keycloak**: Production-grade identity & access management with OAuth2/OIDC support
- **Redis**: High-performance caching and session storage
- **JaCoCo**: Code coverage enforcement (80% minimum threshold for quality gates)

---

## 4. Service Specifications

### 4.1 Eureka Server

**Purpose**: Service discovery and registration for microservices architecture.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.eurekaserver` |
| Port | 8070 |
| Database | None (stateless) |
| Docker Image | `ggoutos/eurekaserver:jib` |

**Key Classes**:
- `EurekaserverApplication.java` - Entry point with `@EnableEurekaServer`

**Configuration**:
- Standalone mode (`register-with-eureka: false`, `fetch-registry: false`)
- Health checks with readiness/liveness probes
- Actuator endpoints: `health`, `info`, `refresh`, `busrefresh`, `shutdown`
- No ConfigServer client dependency (self-configured)
- No security/authentication (internal service)

---

### 4.2 Utils Module

**Purpose**: Shared DTOs and utility classes used across all microservices.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.utils.dto` |
| Type | Shared library (JAR) |
| Dependencies | `springdoc-openapi-starter-webmvc-ui` (v3.0.2) |

**Shared DTOs** (all use Lombok `@Data` and OpenAPI `@Schema`):
- `CustomerDto.java` - Customer data (name, email, mobileNumber, accountsDto)
- `CustomerDetailsDto.java` - Aggregated customer view (accounts, cards, loans)
- `AccountsDto.java` - Account data (accountNumber, accountType, branchAddress)
- `CardsDto.java` - Card data (mobileNumber, cardNumber, cardType, limits)
- `LoansDto.java` - Loan data (mobileNumber, loanNumber, loanType, balances)
- `ResponseDto.java` - Standard success response (statusCode, statusMsg)
- `ErrorResponseDto.java` - Standard error response (apiPath, errorCode, errorMessage, errorTime)
- `AccountsMsgDto.java` - Message record for async account events (accountNumber, name, email, mobileNumber) - used by Message service

**Note**: This is a plain JAR library, not a Spring Boot application. Spring Boot repackaging is disabled so other modules can import it as a regular dependency.

---

### 4.3 ConfigServer

**Purpose**: Centralized configuration management with dynamic refresh capability.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.configserver` |
| Port | 8071 |
| Database | None (stateless) |
| Docker Image | `ggoutos/configserver:jib` |
| Active Profile | `git` (default), `native` (dev fallback) |

**Key Classes**:
- `ConfigserverApplication.java` - Entry point with `@EnableConfigServer`
- `SecurityConfig.java` - Basic auth configuration (CSRF disabled, health endpoints public)

**Configuration Backends**:
- **Git (production/default)**: Remote Git repository (`${GIT_URI}`), branch `master`, search path `/.config`, `clone-on-start: true`, `force-pull: true`
- **Native (dev fallback)**: `classpath:/shared`, `classpath:/config` (directories do not exist - non-functional)

**Security**:
- Basic authentication (username/password via env vars: `CONFIG_SERVER_USER`, `CONFIG_SERVER_PASSWORD`)
- `/actuator/health/**` publicly accessible
- All other endpoints protected
- Symmetric encryption enabled (`ENCRYPTION_KEY` env var)

**Note**: The `main` method is package-private. ConfigServer only serves datasource credentials (all other config is local to each service).

---

### 4.4 GatewayServer

**Purpose**: API Gateway - single entry point for all client traffic with OAuth2 authentication, circuit breaker resilience, and distributed tracing.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.gatewayserver` |
| Port | 8072 |
| Stack | WebFlux (Reactive) - **only service using reactive stack** |
| Database | None (stateless); uses Redis for session/cache |
| Docker Image | `ggoutos/gatewayserver:jib` |
| Authentication | OAuth2 Resource Server (Keycloak JWT) |

**Key Classes**:
- `GatewayserverApplication.java` - Entry point with `RouteLocator` bean for route definitions
- `filters/FilterUtility.java` - Correlation ID header utilities (`eazybank-correlation-id`)
- `filters/RequestTraceFilter.java` - Pre-filter (Order 1): generates/passes correlation ID
- `filters/ResponseTraceFilter.java` - Post-filter: adds correlation ID to response headers
- `config/SecurityConfig.java` - OAuth2 resource server configuration (Keycloak JWT validation)

**Route Configuration**:
```java
public static final String DNS_PREFIX = "goutos/bank";

private Function<PredicateSpec, Buildable<Route>> createRoute(String service) {
    return p -> p
        .path("/" + DNS_PREFIX + "/" + service.toLowerCase() + "/**")
        .filters(f -> f.rewritePath("/" + DNS_PREFIX + "/" + service.toLowerCase() + "/(?<segment>.*)", "/${segment}")
            .addResponseHeader("X-Response-Time", Instant.now().toString()))
        .uri("lb://" + service.toUpperCase());
}
```

**Registered Routes**:
- `/goutos/bank/accounts/**` → `lb://ACCOUNTS`
- `/goutos/bank/loans/**` → `lb://LOANS`
- `/goutos/bank/cards/**` → `lb://CARDS`

**Configuration**: 
- OAuth2 Resource Server with Keycloak JWT validation (JWK Set URI from Keycloak)
- Redis configured for distributed session/cache (host: `REDIS_HOST`, port: 6379)
- No ConfigServer client dependency (fully self-contained in local `application.yml`)
- Discovery locator disabled (`enabled: false`)
- Circuit breaker, retry, and time limiter via Resilience4j
- OpenTelemetry tracing enabled via javaagent

**Note**: The `main` method is package-private. OAuth2 JWT authentication enforced on all routes (bearer token required).

---

### 4.5 Accounts Service

**Purpose**: Customer accounts and relationship management.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.accounts` |
| Port | 8080 |
| Database | MySQL (dev: `localhost:3306`, prod: `accountsdb:3306`) |
| Docker Image | `ggoutos/accounts:jib` |
| Custom Dockerfile | Yes (multi-stage: JVM via jlink + GraalVM Native) |
| Feign Clients | Cards, Loans |
| Schema Management | Flyway (`V1__init_schema.sql`) |
| Stream Binder | RabbitMQ (default) or Kafka |

**Entities**:
- `Customer` - Customer information (PK: `customer_id`, fields: name, email, mobileNumber)
- `Accounts` - Account details (PK: `account_number`, FK: `customer_id` logical, accountType, branchAddress, communicationSw for tracking async notification status)

**Relationship**: One-to-one (Customer → Accounts via `customer_id` column, no JPA `@ManyToOne`)

**Key Classes**:
- `AccountsApplication.java` - Entry point with `@EnableJpaAuditing`, `@EnableFeignClients`
- `AccountsController.java` - REST endpoints for accounts CRUD
- `CustomerController.java` - REST endpoint for aggregated customer details (requires `eazybank-correlation-id` header)
- `IAccountsService.java` / `AccountsServiceImpl.java` - Account CRUD service layer with **event publishing via StreamBridge**
- `ICustomersService.java` / `CustomersServiceImpl.java` - Customer details aggregation with Feign calls
- `AccountsMapper.java` / `CustomerMapper.java` - Static entity/DTO mapping (target-mutation pattern)
- `AccountsRepository.java` / `CustomerRepository.java` - Data access
- `AuditAwareImpl.java` - Auditor provider (`"ACCOUNTS_MS"`)
- `AccountsConstants.java` - HTTP status codes and business constants
- `CardsFeignClient.java` / `LoansFeignClient.java` - Inter-service clients (pass correlation ID)
- `AccountsFunctions.java` - Spring Cloud Stream functions: `updateCommunication()` Consumer bean listens to notification completion events from Message service

**Notable Implementation Details**:
- Account number generation: `1000000000L + random.nextInt(900000000)` (uses `java.util.Random`, not `SecureRandom`)
- Default account type: `"Savings"`, branch: `"123 Main Street, New York"` (hardcoded constants)
- `CustomerController.fetchCustomerDetails()` is the cross-service aggregation endpoint
- Feign clients use circuit breaker pattern (sliding window 10, failure threshold 50%, wait duration 10s)
- Retry pattern enabled (max attempts 3, exponential backoff with multiplier 2)
- Rate limiter configured (10 requests per second, timeout 1s)
- **Event-Driven Communication**: On account creation, sends `AccountsMsgDto` to RabbitMQ via `streamBridge.send("sendCommunication-out-0", accountsMsgDto)` for async email/SMS notifications
- **Event Consumption**: Listens to `updateCommunication-in-0` (topic: `communication-sent`) to update communication status after Message service processes

---

### 4.6 Cards Service

**Purpose**: Credit/debit card management and limits tracking.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.cards` |
| Port | 9000 |
| Database | MySQL (dev: `localhost:3306`, prod: `cardsdb:3306`) |
| Docker Image | `ggoutos/cards:jib` |
| Schema Management | Flyway (`V1__init_schema.sql`) |

**Entities**:
- `Cards` - Card details and limits (PK: `card_id`, fields: mobileNumber, cardNumber, cardType, totalLimit, amountUsed, availableAmount)

**Key Classes**:
- `CardsApplication.java` - Entry point with `@EnableJpaAuditing`, `@EnableFeignClients` (no Feign clients defined)
- `CardsController.java` - REST endpoints (uses `@Slf4j` for logging)
- `ICardsService.java` / `CardsServiceImpl.java` - Service layer (uses `@Slf4j`)
- `CardsMapper.java` - Static entity/DTO mapping (target-mutation pattern)
- `CardsRepository.java` - Data access (findByMobileNumber, findByCardNumber)
- `AuditAwareImpl.java` - Auditor provider (`"CARDS_MS"`)
- `CardsConstants.java` - HTTP status codes and business constants

**Notable Implementation Details**:
- Card number generation: `1000000000000000L + random.nextLong(9000000000000000L)` (16-digit, uses `java.util.Random`)
- Default card type: `"Credit Card"`, limit: `100,000`
- `/api/fetch` endpoint **requires** `eazybank-correlation-id` header (unique among business services)
- `CREDIT` and `DEBIT` constants defined but unused

---

### 4.7 Loans Service

**Purpose**: Loan management and payment tracking.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.loans` |
| Port | 8090 |
| Database | MySQL (dev: `localhost:3306`, prod: `loansdb:3306`) |
| Docker Image | `ggoutos/loans:jib` |
| Schema Management | Flyway (`V1__init_schema.sql`) |

**Entities**:
- `Loans` - Loan details and balances (PK: `loan_id`, `@Table(name = "loans")`, fields: mobileNumber, loanNumber, loanType, totalLoan, amountPaid, outstandingAmount)

**Key Classes**:
- `LoansApplication.java` - Entry point with `@EnableJpaAuditing`, `@EnableFeignClients` (no Feign clients defined)
- `LoansController.java` - REST endpoints (uses `@Slf4j` for logging)
- `ILoansService.java` / `LoansServiceImpl.java` - Service layer
- `LoansMapper.java` - Static entity/DTO mapping (target-mutation pattern)
- `LoansRepository.java` - Data access (findByMobileNumber, findByLoanNumber)
- `AuditAwareImpl.java` - Auditor provider (`"LOANS_MS"`)
- `LoansConstants.java` - HTTP status codes and business constants

**Notable Implementation Details**:
- Loan number generation: `100000000000L + new Random().nextInt(900000000)` (12-digit, narrow range: 100000000000-100899999999)
- Default loan type: `"Home Loan"`, limit: `100,000`
- `/api/fetch` endpoint **requires** `eazybank-correlation-id` header
- `updateLoan()` and `deleteLoan()` always return `true` (417 failure path in controller is unreachable)

---

### 4.8 Message Service

**Purpose**: Event-driven messaging service for asynchronous communication (email/SMS notifications) triggered by account lifecycle events.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.message` |
| Port | 9010 |
| Database | None (stateless event processor) |
| Docker Image | `ggoutos/message:jib` |
| Pattern | Spring Cloud Function + Stream (event-driven) |
| Message Broker | Kafka (default) or RabbitMQ (configurable) |

**Key Classes**:
- `MessageApplication.java` - Entry point with `@SpringBootApplication`
- `MessageFunctions.java` - Spring Cloud Functions for event processing:
  - `email()` - Bean function (Function) for email notifications (logs account details, returns AccountsMsgDto)
  - `sms()` - Bean function (Function) for SMS notifications (logs and returns account number as Long)

**Function Composition**:
```yaml
spring.cloud.function.definition: email|sms
# Processes: email → sms (piped functions)
# email() accepts AccountsMsgDto, returns AccountsMsgDto
# sms() accepts Long (from email output), returns Long
```

**Message Broker Configuration**:
```yaml
spring.cloud.stream:
  default-binder: kafka  # Default is Kafka, can override with KAFKA_HOST or switch to 'rabbit'
  binders:
    kafka:
      type: kafka
      environment:
        spring.kafka:
          bootstrap-servers: ${KAFKA_HOST:localhost}:9092
    rabbit:
      type: rabbit
      environment:
        spring.rabbitmq:
          host: ${RABBITMQ_HOST:localhost}
          port: 5672
```

**Message Flow**:
1. **Accounts Service** calls `streamBridge.send("sendCommunication-out-0", accountsMsgDto)` on account creation
2. **Kafka/RabbitMQ** receives message on `send-communication` topic/destination
3. **Message Service** consumes from `emailsms-in-0` binding (destination: `send-communication`)
4. **Functions** process event (`email()` → `sms()` pipeline)
5. **Output** published to `emailsms-out-0` binding (destination: `communication-sent`)
6. **Accounts Service** consumes on `updateCommunication-in-0` binding via `AccountsFunctions.updateCommunication()` Consumer

**DTOs**:
- `AccountsMsgDto` (record with fields: `accountNumber`, `name`, `email`, `mobileNumber`) - Published from Accounts service

**Stream Configuration**:
```yaml
spring.cloud.stream.bindings:
  emailsms-in-0:
    destination: send-communication
    group: message  # Consumer group for message service
  emailsms-out-0:
    destination: communication-sent
```

**Notable Implementation Details**:
- Fully async/non-blocking via Spring Cloud Stream with pluggable binders
- No database required (stateless processor)
- Uses `@Slf4j` for all logging
- RabbitMQ binder configured in `application.yml` for easy switching (set `SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit`)
- Kafka binder configured as default with configurable bootstrap servers
- Function composition allows flexible piping of processing stages (email → sms)

---

## 5. Development Workflows

### 5.1 Local Development Setup

**Prerequisites**:
- Java 25+ installed (`JAVA_HOME` set)
- Maven 3.9+ installed
- Docker Desktop installed (for containerized MySQL, RabbitMQ, Eureka)

**Step-by-Step**:

```bash
# 1. Clone and navigate to project
cd microservices

# 2. Build all services (including utils and eurekaserver)
mvn clean install

# 3. Start infrastructure (RabbitMQ + ConfigServer + Eureka + Databases)
cd .docker
docker compose up rabbit configserver eurekaserver accountsdb cardsdb loansdb

# 4. In separate terminals, start each service
cd accounts && mvn spring-boot:run
cd cards && mvn spring-boot:run
cd loans && mvn spring-boot:run
```

### 5.2 Debugging

**IDE Setup**:
- Run services in debug mode from IDE
- Default debug port: 5005 (add `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`)

**Common Debug Scenarios**:

```bash
# Enable Hibernate SQL logging
# Add to application.yml:
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        logging:
          level:
            org.hibernate.SQL: DEBUG
            org.hibernate.type.descriptor.sql.BasicBinder: TRACE

# Enable config client logging
logging:
  level:
    org.springframework.cloud.config: DEBUG
    
# Enable Feign client logging
logging:
  level:
    com.ggoutos.accounts.service.client: DEBUG
```

**Watch Logs**:
```bash
# Follow service logs (Docker)
docker compose logs -f accounts
docker compose logs -f configserver
docker compose logs -f eurekaserver
```

### 5.3 Code Style Guidelines

**Naming Conventions**:
- Packages: `com.ggoutos.{service}` (lowercase)
- Interfaces: `I{Service}Service` (capital I prefix)
- Implementations: `{Service}ServiceImpl` in `impl/` subpackage
- DTOs: `{Entity}Dto` suffix (in utils module)
- Mappers: `{Entity}Mapper` with static methods
- Constants: `{Service}Constants` with private constructor
- Exceptions: `{Resource}NotFoundException`, `{Entity}AlreadyExistsException`
- Feign Clients: `{Service}FeignClient` in `service/client/` subpackage
- Spring Cloud Functions: `{Service}Functions` in `functions/` subpackage (Configuration class with Bean methods)

**Lombok Usage**:
```java
// Service classes - use @RequiredArgsConstructor with final fields
@RequiredArgsConstructor
@Service
public class AccountsServiceImpl implements IAccountsService {
    private final AccountsRepository accountsRepository;
    private final AccountsMapper accountsMapper;
}

// DTOs - use @Data
@Data
public class CustomerDto { ... }

// Entities - use @Getter @Setter @ToString @RequiredArgsConstructor
@Entity
@Getter @Setter @ToString @RequiredArgsConstructor
public class Customer extends BaseEntity { ... }
```

**Test Class Organization** (Modern Pattern):
```java
@WebMvcTest(AccountsController.class)
@DisplayName("AccountsController Tests")
class AccountsControllerTest {
    
    @Nested
    @DisplayName("createAccount() Tests")
    class CreateAccountTests {
        @Test
        @DisplayName("Should create account and return 201")
        void shouldCreateAndReturn201() { ... }
        
        @Test
        @DisplayName("Should validate mobile number")
        void shouldValidateMobileNumber() { ... }
    }
    
    @Nested
    @DisplayName("fetchAccount() Tests")
    class FetchAccountTests {
        @Test
        @DisplayName("Should fetch account successfully")
        void shouldFetchSuccessfully() { ... }
    }
}
```

**Mapper Pattern** (static, target-mutation):
```java
public class AccountsMapper {
    static AccountsDto mapToAccountsDto(Accounts accounts, AccountsDto accountsDto) {
        accountsDto.setAccountNumber(accounts.getAccountNumber());
        // ... set other fields
        return accountsDto;
    }
    
    static Accounts mapToAccounts(AccountsDto accountsDto, Accounts accounts) {
        accounts.setAccountType(accountsDto.getAccountType());
        // ... set other fields
        return accounts;
    }
}
```

---

## 6. Build & Deployment

### 6.1 Maven Build Lifecycle

```bash
# Full build with tests
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Build specific module
mvn clean install -pl accounts

# Build with dependency tree
mvn dependency:tree

# CI-friendly version override
mvn clean install -Drevision=1.2.3
```

### 6.2 Docker Image Building

All services support **three** build methods (utils module is a library, not an application):

#### Method 1: Jib (Recommended)
```bash
cd {service}
mvn compile jib:dockerBuild
# Creates: ggoutos/{service}:jib
```

**Advantages**: Fast, reproducible, no Docker daemon required, layered builds. Base image: `eclipse-temurin:25-jre-alpine-3.21`

#### Method 2: Buildpacks (Spring Boot)
```bash
cd {service}
mvn spring-boot:build-image
# Creates: ggoutos/{service}:spring
```

**Advantages**: Automatic layer detection, no Dockerfile needed

#### Method 3: Custom Dockerfile (Accounts Only)
```bash
cd accounts
docker build --target jvm -t ggoutos/accounts:latest .
docker build --target native -t ggoutos/accounts:native .
```

**Advantages**: Full control over image layers, optimized for production. JVM target uses custom JRE via `jlink`.

#### Method 4: Native Image (GraalVM)
```bash
mvn -Pnative native:compile
```

**Prerequisites**: GraalVM 25+ installed, `GRAALVM_HOME` set

**Advantages**: Sub-second startup, minimal memory footprint

### 6.3 Docker Compose Orchestration

**Development**:
```bash
cd .docker
docker compose up --build
```

**Production**:
```bash
cd .docker
docker compose --env-file .env.prod up --build
```

**Service-Specific**:
```bash
docker compose up accounts cards  # Start only accounts and cards
docker compose down               # Stop all services
docker compose restart accounts   # Restart specific service
docker compose logs -f accounts   # Stream service logs
```

**Startup Order** (enforced by `depends_on` with `service_healthy`):
```
Monitoring Stack (Loki, Prometheus, Tempo, Grafana, Alloy)
    ↓
Keycloak (identity & access management)
    ↓
RabbitMQ (message broker)
    ↓
Redis (caching layer)
    ↓
MySQL Databases (accountsdb, loansdb, cardsdb)
    ↓
Eureka Server (service discovery)
    ↓
ConfigServer (centralized config)
    ↓
Business Services (accounts, cards, loans)
    ↓
GatewayServer (API gateway)
```

**Database Persistence**: All MySQL databases (accountsdb, loansdb, cardsdb) use Docker volumes (`*-data`) for data persistence across container restarts.

**Service Exposure**: Business services (accounts, cards, loans) are **not exposed** on host ports directly. Access only through gateway at port 8072 with OAuth2 JWT authentication.

### 6.4 Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `COMPOSE_PROJECT_NAME` | `microservices` | Docker compose project name |
| `IMAGE_TAG` | `jib` | Docker image tag for all services |
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile |
| `APP_ENV` | `local` | Environment selector (dev, local, prod) |
| `RABBITMQ_HOST` | `rabbit` | RabbitMQ hostname |
| `CONFIG_SERVER_HOST` | `configserver` | ConfigServer hostname |
| `EUREKA_SERVER_HOST` | `eurekaserver` | Eureka Server hostname |
| `REDIS_HOST` | `redis` | Redis hostname |
| `KEYCLOAK_HOST` | `keycloak` | Keycloak hostname |
| `KEYCLOAK_PORT` | `8080` | Keycloak port (in container) |
| `CONFIG_SERVER_USER` | *(required)* | ConfigServer basic auth username |
| `CONFIG_SERVER_PASSWORD` | *(required)* | ConfigServer basic auth password |
| `ENCRYPTION_KEY` | *(required for encryption)* | Symmetric encryption key |
| `GIT_URI` | *(required for git profile)* | Git repository URL |
| `GIT_USERNAME` | *(required for git profile)* | Git authentication username |
| `GIT_TOKEN` | *(required for git profile)* | Git authentication token (PAT) |
| `MYSQL_ROOT_PASSWORD` | *(required)* | MySQL root password |
| `JAVA_TOOL_OPTIONS` | `-javaagent:/app/libs/opentelemetry-javaagent-2.26.1.jar` | OTEL javaagent for distributed tracing |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://tempo:4318` | Tempo endpoint for OTEL traces |
| `OTEL_METRICS_EXPORTER` | `none` | Metrics export disabled (use Prometheus instead) |
| `OTEL_LOGS_EXPORTER` | `none` | Logs export disabled (use Loki instead) |

**Note**: Sensitive credentials should be set in `.env.local` (git-ignored for dev) or `.env.prod` (production secrets). The `.env` file has placeholders; actual values must be provided in environment-specific files.

### 6.5 Docker & Kubernetes Setup

All Docker Compose and Kubernetes manifests are organized in the `.docker/` directory:

```
.docker/
├── docker-compose.yml       # Complete Docker Compose setup
├── common-config.yml        # Shared service configurations
├── .env                     # Default environment variables
├── .env.local              # Development secrets (git-ignored)
├── .env.prod               # Production secrets (git-ignored)
├── alloy/                  # Grafana Alloy configuration
├── grafana/                # Grafana dashboards and datasources
├── loki/                   # Loki log aggregation configuration
├── prometheus/             # Prometheus metrics configuration
├── tempo/                  # Tempo distributed tracing configuration
├── nginx/                  # Nginx gateway configuration
├── helm/                   # Helm charts for Kubernetes
│   └── microservices-common/  # Common Helm chart (library)
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── charts/
│       └── templates/
├── k8s-generated/          # Pre-generated Kubernetes manifests
│   ├── *-deployment.yaml   # Service deployments
│   ├── *-service.yaml      # Service definitions
│   ├── *-configmap.yaml    # Configuration maps
│   ├── *-persistentvolumeclaim.yaml  # Persistent volumes
│   └── *-rbac.yaml         # RBAC configurations
└── .data/                  # Local data persistence (git-ignored)
    ├── minio/              # MinIO S3 storage for Loki
```

#### **Docker Compose Architecture**

**Managed Services** (Complete stack):
- **Observability Stack**: Grafana, Prometheus, Tempo, Loki (read/write/backend), Alloy (log collector), MinIO (S3 backend for Loki)
- **Message Brokers**: RabbitMQ (port 5672, management UI 15672), Kafka (KRaft mode, port 9092)
- **Cache**: Redis (port 6379)
- **Identity**: Keycloak (port 7080)
- **Databases**: MySQL LTS instances for accounts (3307), loans (3308), cards (3309)
- **Infrastructure**: Eureka Server (8070), ConfigServer (8071), Gateway Server (8072)
- **Business Services**: Accounts (8080), Cards (9000), Loans (8090), Message (9010)
- **Routing**: Nginx (port 3100) - Load balancer for Loki read/write/backend requests

**docker-compose.yml Structure**:
```yaml
# Base configuration definitions (via extends)
services:
  network-deploy-service:     # Network config, CPU/memory limits (0.5 cores, 512MB)
  microservice-db-config:     # MySQL health checks (mysqladmin ping)
  microservice-base-config:   # HTTP readiness probe (/actuator/health/readiness)
  
  # Individual services extend from base configs
  accounts:
    extends: microservice-base-config
    depends_on:
      eurekaserver: { condition: service_healthy }
      accountsdb: { condition: service_healthy }
      configserver: { condition: service_healthy }
      kafka: { condition: service_healthy }
      rabbit: { condition: service_healthy }
```

**common-config.yml**:
- Centralized configuration reused by all services via `extends`
- `network-deploy-service`: Sets network and resource limits (0.50 CPU cores, 512M memory)
- `microservice-db-config`: MySQL health check pattern (checks port 3306 via mysqladmin)
- `microservice-base-config`: HTTP health check for Spring Boot services (readiness endpoint, 30s start grace, 10s interval)

**Health Check Pattern**:
```yaml
# Microservice health check (all business services)
healthcheck:
  test: [ "CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:PORT/actuator/health/readiness || exit 1" ]
  interval: 10s    # Check every 10 seconds
  timeout: 5s      # Timeout if check takes >5s
  retries: 10      # Container unhealthy after 10 consecutive failures
  start_period: 30s # Grace period before first health check

# Database health check (MySQL services)
healthcheck:
  test: [ "CMD", "mysqladmin", "ping", "-h", "localhost" ]
  interval: 10s
  retries: 10
  start_period: 10s
```

**Service Startup Order** (enforced by `depends_on: {service_healthy}` conditions):
```
1. Loki Storage Layer (MinIO)
2. Observability Services (Prometheus, Tempo, Loki read/write/backend, Alloy, Grafana)
3. Cache & Identity (Redis, Keycloak)
4. Message Brokers (RabbitMQ, Kafka)
5. Databases (accountsdb, loansdb, cardsdb)
6. Service Discovery (Eureka Server)
7. Configuration Server (ConfigServer, depends on RabbitMQ for bus refresh)
8. Business Services (Accounts, Cards, Loans, Message - depend on Eureka, ConfigServer, databases)
9. API Gateway (Gateway Server, depends on Eureka and Redis)
```

**Environment Loading**:
```yaml
# Services use env file stacking (right-to-left priority)
env_file:
  - .env              # Base configuration (checked in git)
  - .env.${APP_ENV}   # Environment overrides (git-ignored, local/prod secrets)
```

Example: `docker compose up` with `APP_ENV=local` loads `.env` then `.env.local`

**Resource Limits**:
All services limited to 0.5 CPU cores and 512M memory (enforce in `common-config.yml`)
- Prevents single service from consuming all host resources
- Kubernetes can apply stricter limits via resource requests/limits

**Network Configuration**:
- Single bridge network: `microservices-network`
- Internal service discovery via DNS (e.g., `accountsdb:3306` resolves within network)
- External ports mapped only for LoadBalancer services (Keycloak, Eureka, Gateway, Grafana, Prometheus)

**Kompose Labels** (for docker-compose → K8s conversion):

All services in docker-compose.yml include `labels` section for Kompose tool hints:
```yaml
services:
  keycloak:
    labels:
      kompose.service.type: LoadBalancer    # Expose as external LoadBalancer service
  eurekaserver:
    labels:
      kompose.service.type: LoadBalancer
  gatewayserver:
    labels:
      kompose.service.type: LoadBalancer
  alloy:
    labels:
      kompose.volume.type: configMap        # Convert volume to K8s ConfigMap
      kompose.serviceaccount-name: alloy    # Use dedicated service account
```

**Kompose Label Reference**:
- `kompose.service.type: LoadBalancer` - Creates K8s LoadBalancer service (external IP)
- `kompose.service.type: ClusterIP` - Creates internal K8s ClusterIP service (default)
- `kompose.volume.type: configMap` - Converts Docker volume to K8s ConfigMap
- `kompose.serviceaccount-name: {name}` - Assigns custom service account for RBAC

#### **`.docker` Directory Structure & Configuration Files**

**Root Level Files**:
```
.docker/
├── docker-compose.yml           # Complete Docker Compose configuration (all services)
├── common-config.yml            # Base service configurations (network, health checks, limits)
├── .env                         # Base environment variables (public, in git)
├── .env.local                   # Dev secrets (git-ignored: mysql root pwd, config server creds, keycloak pwd)
├── .env.prod                    # Prod secrets (git-ignored: cloud credentials, git tokens, encryption keys)
├── .data/                       # Local persistent data (git-ignored)
│   ├── minio/                   # MinIO S3 storage for Loki logs
│   └── [other volumes mounted to host]
```

**Configuration Subdirectories**:

**`alloy/`** - Grafana Alloy configuration (observability agent):
```
alloy/
├── alloy-local-config.yaml      # Local dev setup - collects logs from Docker containers
└── alloy-k8s-config.yaml        # Kubernetes setup - collects logs from mounted volumes
```
- Reads container logs via `/var/run/docker.sock`
- Sends to Loki write endpoint with tenant ID `tenant1`
- Tag-based log routing by container name

**`grafana/`** - Grafana dashboards and data sources:
```
grafana/
└── datasource.yml               # Configures data sources (Prometheus, Loki, Tempo)
                                 # Anonymous admin access enabled for local dev (GF_AUTH_ANONYMOUS_ORG_ROLE=Admin)
```
- Pre-configured to connect to: Prometheus (9090), Loki (3100), Tempo (3110)
- Tenant ID: `tenant1` for Loki

**`prometheus/`** - Metrics scraping configuration:
```
prometheus/
└── prometheus.yml               # Scrape targets configuration
```
- Scrapes `/actuator/prometheus` endpoint from all Spring Boot services (10s interval)
- Retention: 24 hours (default)
- Targets: eurekaserver, configserver, accounts, cards, loans, gatewayserver, etc.

**`tempo/`** - Distributed tracing backend:
```
tempo/
└── tempo.yml                    # Tempo configuration (receivers, storage)
```
- OTEL receiver on port 4318 (gRPC)
- Tempo operational UI on port 3110
- Validates traces and forwards to backend storage

**`loki/`** - Log aggregation system:
```
loki/
└── loki-config.yaml             # Loki configuration (read/write/backend topology)
```
- **Three-part topology**:
  - `read`: Query logs, expose port 3101
  - `write`: Ingest logs from Alloy, expose port 3102
  - `backend`: Manages index and object storage coordination
- **Gateway (Nginx)**: Load balances traffic to read/write/backend on port 3100
- **Storage**: MinIO S3 backend (bucket: `loki-data`)
- **Index**: In-memory (default), not persisted

**`nginx/`** - Reverse proxy for Loki:
```
nginx/
└── nginx.conf                   # Nginx configuration for Loki gateway
```
- Routes requests to correct Loki target (read, write, or backend)
- Exposes consolidated port 3100 for all Loki operations
- Template-based config (substituted at container startup)

**`helm/`** - Kubernetes Helm charts:
```
helm/
└── microservices-common/        # Reusable Helm chart (library chart)
    ├── Chart.yaml               # Chart metadata
    ├── values.yaml              # Default values for all services
    ├── charts/                  # Dependent charts
    └── templates/               # Helm templates (generated K8s resources)
```
- **Library chart**: Contains common templates reused across services
- `values.yaml`: Image tags, replicas, resource limits, environment variables
- Deploy via: `helm install microservices .docker/helm/microservices-common -n microservices`

**`k8s-generated/`** - Pre-generated Kubernetes manifests (from docker-compose or Helm):
```
k8s-generated/
├── *-deployment.yaml            # Service deployments
├── *-service.yaml               # Service definitions (ClusterIP, LoadBalancer)
├── *-configmap.yaml             # ConfigMaps (env, prometheus config, etc.)
├── *-persistentvolumeclaim.yaml  # PVCs (MySQL, Grafana, Keycloak, MinIO)
├── *-rbac.yaml                  # RBAC (Alloy service account, cluster role)
── *-external-service.yaml      # ExternalName services for external databases
```
- Generated via `kompose convert` or Helm templates
- Can be deployed directly: `kubectl apply -f .docker/k8s-generated/`
- Update manifests when docker-compose.yml changes

#### **Running with Docker Compose**

**Quick Start Commands** (from `.docker` directory):

```bash
# 1. Set environment (dev/local/prod)
cd .docker
export APP_ENV=local    # Loads .env + .env.local

# 2. Start full stack
docker compose up --build

# 3. Tail logs
docker compose logs -f

# 4. Stop everything
docker compose down
docker compose down -v  # Also removes volumes (database data, Grafana config)
```

**Building New Images** (before first compose run):

```bash
# Option 1: Build inside compose (--build flag)
docker compose up --build

# Option 2: Pre-build using Maven/Jib
cd ../accounts && mvn compile jib:dockerBuild
cd ../cards && mvn compile jib:dockerBuild
cd ../loans && mvn compile jib:dockerBuild
cd ../message && mvn compile jib:dockerBuild
cd ../eurekaserver && mvn compile jib:dockerBuild
cd ../configserver && mvn compile jib:dockerBuild
cd ../gatewayserver && mvn compile jib:dockerBuild

# Option 3: Use docker-compose --build
docker compose build
docker compose up
```

**Service-Level Operations**:

```bash
# View running services
docker compose ps                 # All services
docker compose ps -a              # Including stopped

# Start/stop specific services
docker compose up -d accounts     # Start in background
docker compose down rabbitmq      # Stop single service (depends_on ignored)
docker compose restart accounts   # Quick restart

# View logs
docker compose logs accounts                    # Last 100 lines
docker compose logs -f accounts                 # Follow mode
docker compose logs --tail=50 accounts          # Last 50 lines
docker compose logs accounts | grep ERROR       # Filter

# Execute commands in running container
docker compose exec accounts sh                           # Interactive shell
docker compose exec accounts curl http://localhost:8080/actuator/health
docker compose exec accountsdb mysql -u root -p accountsdb  # MySQL CLI
docker compose exec redis redis-cli ping | grep PONG        # Redis CLI

# View service config
docker compose config                           # Merged YAML (all services, env substituted)
docker compose config services                  # List service names
docker compose images                           # Show image info
```

**Troubleshooting**:

```bash
# Check service status
docker compose ps
docker compose health                           # Show health checks

# View detailed logs (errors)
docker compose logs configserver | grep ERROR
docker compose logs accountsdb | tail -20
docker compose logs rabbit 2>&1 | grep WARN

# Test connectivity between services
docker compose exec accounts ping eurekaserver
docker compose exec accounts wget http://eurekaserver:8070/actuator/health
docker compose exec accountsdb mysqladmin ping -h localhost

# Check environment variables inside container
docker compose exec accounts printenv SPRING_PROFILES_ACTIVE
docker compose exec accountsdb printenv MYSQL_ROOT_PASSWORD

# Inspect network
docker compose exec accounts ip addr show
docker network ls
docker network inspect microservices_microservices-network  # See container IPs

# Resource usage
docker stats                                    # CPU, memory, network
docker compose stats                            # Only compose services (*Not all versions support)

# Access service directly
docker compose exec gateway curl http://loki-backend:3100/-/health
docker compose exec grafana curl http://localhost:3000/api/health
docker compose exec prometheus curl http://localhost:9090/-/healthy
```

**Database Operations**:

```bash
# Reset database (delete all data)
docker compose down -v                          # Remove volumes
docker compose up accountsdb                    # Recreate fresh

# Backup database (export)
docker compose exec accountsdb mysqldump -u root -p accountsdb > backup.sql

# Restore database (import)
docker compose exec -T accountsdb mysql -u root -p accountsdb < backup.sql

# Direct schema inspection
docker compose exec accountsdb mysql -u root -p accountsdb -e "SHOW TABLES;"
docker compose exec accountsdb mysql -u root -p accountsdb -e "DESCRIBE accounts;"

# Check Flyway migrations
docker compose exec accountsdb mysql -u root -p accountsdb -e "SELECT * FROM flyway_schema_history;"
```

**Environment Configuration for Compose**:

```bash
# Use different environment files
docker compose --env-file .env.local up        # Explicit env file
docker compose --env-file .env.prod up         # Production

# Override environment at command line
MYSQL_ROOT_PASSWORD=mypassword docker compose up
export CONFIG_SERVER_USER=admin && docker compose up

# Check final environment (after .env merging)
docker compose exec accounts env | grep SPRING
docker compose exec accountsdb env | grep MYSQL
```

**Cleanup & Maintenance**:

```bash
# Remove unused Docker resources
docker system prune                             # Remove dangling images, containers, networks
docker system prune -a                          # Also remove unused images
docker volume prune                             # Remove unused volumes
docker image prune                              # Remove unused images

# Remove all project resources
docker compose down --remove-orphans            # Remove services no longer in compose
docker compose down -v --remove-orphans         # Also remove volumes

# Rebuild from scratch
docker compose down -v
docker image rm ggoutos/accounts:jib ggoutos/cards:jib ggoutos/loans:jib ggoutos/eurekaserver:jib ggoutos/configserver:jib ggoutos/gatewayserver:jib ggoutos/message:jib
docker compose up --build
```

**Performance Tuning for Compose**:

```bash
# Reduce startup time (increase health check grace period in prod)
# In docker-compose.yml:
healthcheck:
  start_period: 60s     # Give services more time to start

# Resource constraints
# In common-config.yml:
deploy:
  resources:
    limits:
      cpus: '1.0'       # Increase if services are throttled
      memory: 1G        # Increase if OOMKilled

# Parallel startup (remove some depends_on to parallelize)
# But be careful - ensures correct startup order for stability
```

**Health Checks**:
All services have health checks configured:
- HTTP services: `/actuator/health/readiness` endpoint
- MySQL databases: `mysqladmin ping`
- Message brokers: Port connectivity tests
- Cache services: Command-based checks (e.g., Redis PING)

**Troubleshooting Docker Compose**:
```bash
# Check service logs for errors
docker compose logs configserver | grep ERROR

# Verify network connectivity
docker compose exec accounts ping eurekaserver

# Check environment variables in running container
docker compose exec accountsdb printenv MYSQL_ROOT_PASSWORD

# Access service directly
docker compose exec accounts wget -O- http://localhost:8080/actuator/health
```

#### **Kubernetes Deployment**

**K8s Manifests** (Pre-generated in `.docker/k8s-generated/`):

| Manifest Type | Convention | Examples |
|---------------|-----------|----------|
| **Deployments** | `{service}-deployment.yaml` | `accounts-deployment.yaml`, `prometheus-deployment.yaml` |
| **Services** | `{service}-service.yaml` | `accounts-service.yaml`, `kafka-service.yaml` |
| **ConfigMaps** | `{service}-cm*.yaml` | `prometheus-cm0-configmap.yaml`, `env-configmap.yaml` |
| **PersistentVolumeClaims** | `{service}-data-persistentvolumeclaim.yaml` | `accounts-data-persistentvolumeclaim.yaml` |
| **RBAC** | `{service}-rbac.yaml` | `alloy-rbac.yaml` |
| **External Services** | `{service}-external-service.yaml` | `accountsdb-external-service.yaml` (for external DBs) |

**Kubernetes Namespace Structure**:
```
microservices/
├── Config & Secrets
│   ├── env-configmap.yaml              # Default environment
│   ├── env-prod-configmap.yaml         # Production environment
│   └── *.yaml (service-specific)
├── Service Layer (Port 8072 ingress)
│   ├── gateway-service.yaml            # LoadBalancer - External entry point
│   ├── gatewayserver-deployment.yaml   # API Gateway instance
│   └── gateway-cm0-configmap.yaml
├── Discovery & Config
│   ├── eurekaserver-{deployment,service}.yaml
│   ├── configserver-{deployment,service}.yaml
│   └── configserver external DB service
├── Business Services
│   ├── accounts-{deployment,service,data-pvc}.yaml
│   ├── cards-{deployment,service,data-pvc}.yaml
│   ├── loans-{deployment,service,data-pvc}.yaml
│   ├── message-{deployment,service}.yaml
├── Infrastructure Services
│   ├── rabbit-{deployment,service}.yaml
│   ├── kafka-{deployment,service}.yaml
│   ��── redis-{deployment,service}.yaml
│   ├── keycloak-{deployment,service,data-pvc}.yaml
├── Databases
│   ├── accountsdb-{deployment,service,external-service,data-pvc}.yaml
│   ├── loansdb-{deployment,service,external-service,data-pvc}.yaml
│   ├── cardsdb-{deployment,service,external-service,data-pvc}.yaml
├── Observability
│   ├── prometheus-{deployment,service,cm0-configmap}.yaml
│   ├── grafana-{deployment,service,data-pvc,cm0-configmap}.yaml
│   ├── tempo-{deployment,service,cm0-configmap}.yaml
│   ├── {read,write,backend}-{deployment,service,cm0-configmap}.yaml (Loki)
│   ├── alloy-{deployment,service,rbac,cm0-configmap}.yaml
│   └── minio-{deployment,service}.yaml
```

**Deploy to Kubernetes**:
```bash
# Install Helm (if deploying via Helm)
helm install microservices .docker/helm/microservices-common \
  --namespace microservices \
  --create-namespace \
  -f .docker/helm/microservices-common/values.yaml

# Or apply pre-generated K8s manifests directly
kubectl create namespace microservices
kubectl apply -f .docker/k8s-generated/ \
  -n microservices

# Verify deployment
kubectl get pods -n microservices
kubectl get svc -n microservices
kubectl get pvc -n microservices

# Monitor pod startup
kubectl logs -f deployment/accounts -n microservices
kubectl describe pod <pod-name> -n microservices

# Port forwarding to access services
kubectl port-forward -n microservices svc/gateway 3100:3100   # Grafana
kubectl port-forward -n microservices svc/accounts 8080:8080  # Accounts service

# Scale deployments
kubectl scale deployment accounts --replicas=3 -n microservices

# Delete everything
kubectl delete namespace microservices
```

**External Database Configuration**:
- Files like `accountsdb-external-service.yaml` are used when databases are managed externally
- Configure ExternalName services to point to cloud-hosted databases
- Update environment variables to use external connection strings

**Persistent Volumes**:
- All data-bound services (MySQL, Grafana, Keycloak, MinIO) have PersistentVolumeClaims (PVCs)
- Storage class defaults to `standard` (can be customized per environment)
- Data survives pod restarts but NOT namespace deletion

**Common Kubernetes Commands**:
```bash
# View resources
kubectl get all -n microservices
kubectl describe node
kubectl top nodes
kubectl top pods -n microservices

# Debugging
kubectl exec -it <pod-name> -n microservices -- /bin/sh
kubectl logs --tail=50 -f <pod-name> -n microservices
kubectl events -n microservices

# Update deployments
kubectl set image deployment/accounts \
  accounts=ggoutos/accounts:latest \
  -n microservices

kubectl rollout status deployment/accounts -n microservices
```

**Kubernetes Manifest Structure & Deployment Details**:

**K8s Manifest Inventory** (in `.docker/k8s-generated/`):

| Type | Pattern | Count | Examples |
|------|---------|-------|----------|
| **Deployments** | `{service}-deployment.yaml` | 17 | accounts, cards, loans, message, eurekaserver, configserver, gatewayserver, rabbit, kafka, redis, prometheus, grafana, tempo, read, write, backend, keycloak |
| **Services** | `{service}-service.yaml` | 17 | Same services (ClusterIP or LoadBalancer) |
| **ConfigMaps** | `{service}-cm*.yaml` | 8+ | prometheus, grafana, tempo, read, write, backend, alloy, gateway, env config |
| **PersistentVolumeClaims** | `{service}-data-pvc.yaml` | 5 | accounts, cards, loans, grafana, keycloak |
| **RBAC** | `{service}-rbac.yaml` | 1 | alloy (service account, cluster role, cluster role binding) |
| **External Services** | `{service}-external-service.yaml` | 3 | accountsdb, loansdb, cardsdb (optional: for external cloud databases) |

**Total Manifest File Count**: ~50-60 YAML files covering all microservices and infrastructure

**Kubernetes Deployment Architecture**:

```
microservices (namespace)
│
├─ ConfigMaps (Configuration)
│  ├── env-configmap                     # Default environment
│  ├── env-prod-configmap                # Production environment  
│  ├── prometheus-cm0-configmap          # Prometheus scrape config (17 targets)
│  ├── grafana-cm0-configmap             # Grafana datasources
│  ├── tempo-cm0-configmap               # Tempo configuration
│  ├── {read,write,backend}-cm0-configmap # Loki components
│  ├── alloy-cm0-configmap               # Alloy log collection
│  └── gateway-cm0-configmap              # Nginx Loki gateway
│
├─ External API Gateway (LoadBalancer)
│  ├── gatewayserver-deployment
│  ├── gatewayserver-service             # Type: LoadBalancer (External IP on port 8072)
│  └── gateway-cm0-configmap
│
├─ Service Discovery & Configuration
│  ├── eurekaserver-{deployment,service}
│  ├── configserver-{deployment,service}
│  └── keycloak-{deployment,service,data-pvc}
│
├─ Business Services (Internal: ClusterIP)
│  ├── accounts-{deployment,service,data-pvc}
│  ├── cards-{deployment,service,data-pvc}
│  ├── loans-{deployment,service,data-pvc}
│  └── message-{deployment,service}
│
├─ Infrastructure (Message Brokers, Cache)
│  ├── rabbit-{deployment,service}       # RabbitMQ
│  ├── kafka-{deployment,service}        # Kafka (KRaft mode)
│  └── redis-{deployment,service}        # Redis cache
│
├─ Databases (Storage)
│  ├── accountsdb-{deployment,service,data-pvc}
│  ├── loansdb-{deployment,service,data-pvc}
│  ├── cardsdb-{deployment,service,data-pvc}
│  └── [*-external-service.yaml]         # For AWS RDS, Cloud SQL, etc.
│
└─ Observability Stack
   ├─ Metrics Collection
   │  ├── prometheus-{deployment,service,cm0-configmap}
   │  └── [scrapes all services' /actuator/prometheus]
   ├─ Tracing
   │  ├── tempo-{deployment,service,cm0-configmap}
   │  └── backend-{deployment,service}
   ├─ Log Aggregation
   │  ├── minio-{deployment,service}
   │  ├── read-{deployment,service,cm0-configmap}
   │  ├── write-{deployment,service,cm0-configmap}
   │  ├── backend-{deployment,service,cm0-configmap}
   │  ├── gateway-{deployment,service}
   │  └── alloy-{deployment,service,rbac,cm0-configmap}
   └─ Visualization
      └── grafana-{deployment,service,data-pvc,cm0-configmap}

Persistent Volumes (auto-provisioned):
   ├── accounts-data-pvc
   ├── cards-data-pvc
   ├── loans-data-pvc
   ├── grafana-data-pvc
   ├── keycloak-data-pvc
   └── minio-data-pvc (implied by Loki S3 backend)
```

**Kubernetes Service Network**:

- **LoadBalancer Services** (external IP for outside cluster):
  - `gatewayserver-service` - Main entry point (port 8072)
  - `eurekaserver-service` - Optional external Eureka access (port 8070)
  - `grafana-service` - Monitoring dashboard (port 3000)
  - `prometheus-service` - Metrics endpoint (port 9090)

- **ClusterIP Services** (internal DNS only):
  - All business services (accounts, cards, loans, message)
  - All infrastructure services (rabbit, kafka, redis)
  - All database services (accountsdb, loansdb, cardsdb)
  - All observability services (tempo, loki read/write/backend, alloy)

- **DNS Naming Convention**:
  - Format: `{service-name}-service.{namespace}.svc.cluster.local`
  - Example: `accountsdb-service.microservices.svc.cluster.local:3306`
  - Short form (within namespace): `accountsdb-service:3306`

**Deployment Strategies**:

**Strategy 1: Direct Manifest Application**
```bash
# Setup namespace
kubectl create namespace microservices
kubectl config set-context --current --namespace=microservices

# Apply all manifests (order doesn't matter, K8s handles dependencies)
kubectl apply -f .docker/k8s-generated/

# Verify deployment
kubectl get pods -w                                   # Watch startup
kubectl get svc                                       # See services, external IPs
kubectl get pvc                                       # See persistent volumes

# Expose services for local testing (no LoadBalancer ingress)
kubectl port-forward svc/gatewayserver 8072:8072
kubectl port-forward svc/grafana 3000:3000
kubectl port-forward svc/prometheus 9090:9090
```

**Strategy 2: Helm Chart Deployment**
```bash
# Install from Helm chart
kubectl create namespace microservices
cd .docker/helm

# Install with defaults
helm install microservices microservices-common \
  --namespace microservices \
  --values microservices-common/values.yaml

# Install with overrides
helm install microservices microservices-common \
  --namespace microservices \
  --set image.tag=latest \
  --set replicas.accounts=3 \
  --set prometheus.retention=48h

# Verify
helm list -n microservices
helm status microservices -n microservices
kubectl get all -n microservices
```

**Strategy 3: Generate K8s from docker-compose.yml**
```bash
# Install kompose (one-time: https://kompose.io/installation/)
# Convert Docker Compose to K8s manifests
cd .docker
kompose convert -f docker-compose.yml -o k8s-generated-new/

# Review generated manifests (may need tweaks)
# Apply when ready
kubectl apply -f k8s-generated-new/
```

**Database Configuration Options**:

**Option A: In-Cluster MySQL (Default)**
```bash
# Pre-generated manifests handle deployment
kubectl apply -f accountsdb-deployment.yaml
kubectl apply -f accountsdb-service.yaml

# Connection from apps
# Service DNS: accountsdb-service.microservices.svc.cluster.local:3306
# Spring config: jdbc:mysql://accountsdb-service:3306/accountsdb
# (Short DNS works within same namespace)
```

**Option B: External Cloud Database (RDS, Cloud SQL, GCP)**
```bash
# Example: AWS RDS MySQL instance
# Use ExternalName service to map external database

# Edit or create ExternalName service:
apiVersion: v1
kind: Service
metadata:
  name: accountsdb-service
  namespace: microservices
spec:
  type: ExternalName
  externalName: mydb-instance-123.us-east-1.rds.amazonaws.com
  ports:
  - port: 3306
    targetPort: 3306

# Apply
kubectl apply -f accountsdb-external-service.yaml

# Spring config (same as in-cluster!)
# jdbc:mysql://accountsdb-service:3306/accountsdb
# No code changes needed - network abstraction handles it
```

**Option C: External Database with Credentials**
```bash
# Create secret for database password
kubectl create secret generic db-credentials \
  --from-literal=password=MySecurePassword123 \
  -n microservices

# Reference in deployment env:
# env:
#   - name: SPRING_DATASOURCE_PASSWORD
#     valueFrom:
#       secretKeyRef:
#         name: db-credentials
#         key: password
```

**Scaling & Advanced Operations**:

```bash
# Scale a service
kubectl scale deployment accounts --replicas=3 -n microservices

# Check pod autoscaling (requires HorizontalPodAutoscaler manifest)
kubectl get hpa -n microservices
kubectl autoscale deployment accounts --min=1 --max=5 --cpu-percent=80 -n microservices

# Rolling update with new image
kubectl set image deployment/accounts \
  accounts=ggoutos/accounts:v1.2.3 \
  -n microservices \
  --record

# Check rollout status
kubectl rollout status deployment/accounts -n microservices

# Rollback to previous version
kubectl rollout undo deployment/accounts -n microservices
kubectl rollout history deployment/accounts -n microservices

# Edit resource live (careful!)
kubectl edit deployment accounts -n microservices
```

**Monitoring Kubernetes Health**:

```bash
# View nodes
kubectl get nodes
kubectl top nodes                                     # Resource usage
kubectl describe node <node-name>

# View all resources in namespace
kubectl get all -n microservices

# Check pod conditions
kubectl get pods -n microservices -o wide
kubectl get pods -n microservices -o json | jq '.items[].status.conditions'

# View events (admission failures, pod failures, etc.)
kubectl get events -n microservices --sort-by='.lastTimestamp'
kubectl get events -n microservices | grep Warning
kubectl get events -n microservices | grep Error

# Check PVC status
kubectl get pvc -n microservices
kubectl describe pvc accounts-data-pvc -n microservices

# Resource quotas & limits
kubectl get resourcequotas -n microservices
kubectl describe resourcequota -n microservices
```

**Helm Configuration Management**:

```bash
# Chart values inspection
helm show values microservices-common                  # Chart defaults
helm get values microservices -n microservices        # Deployed values
helm get manifest microservices -n microservices      # Final K8s manifests

# Upgrade with new values
helm upgrade microservices microservices-common \
  --namespace microservices \
  -f custom-values.yaml

# Dry-run (see what would change)
helm upgrade microservices microservices-common \
  --namespace microservices \
  --dry-run --debug

# Uninstall
helm uninstall microservices -n microservices
```

#### **Environment Configuration**

**.env** (default, in git):
- Public variables for local development
- Service hostnames (localhost vs. container names)
- Default image tag, profiles
- OTEL configuration

**.env.local** (git-ignored, for dev):
```bash
MYSQL_ROOT_PASSWORD=devpassword
CONFIG_SERVER_USER=admin
CONFIG_SERVER_PASSWORD=admin123
KC_BOOTSTRAP_ADMIN_PASSWORD=admin
SPRING_PROFILES_ACTIVE=default
```

**.env.prod** (git-ignored, for production):
```bash
MYSQL_ROOT_PASSWORD=<prod-password>
CONFIG_SERVER_USER=<prod-user>
CONFIG_SERVER_PASSWORD=<prod-password>
GIT_URI=https://github.com/your-repo/.config.git
GIT_USERNAME=<github-user>
GIT_TOKEN=<github-pat>
ENCRYPTION_KEY=<prod-encryption-key>
SPRING_PROFILES_ACTIVE=prod
KC_BOOTSTRAP_ADMIN_PASSWORD=<prod-admin-password>
```

**Loading Configuration in Docker Compose**:
```yaml
# Services load multiple env files (right-to-left priority)
env_file:
  - .env              # Base config
  - .env.${APP_ENV}   # Environment-specific overrides (dev/prod)
```

#### **Multi-Environment Setup**

**Environment Variables**:
```bash
export APP_ENV=local   # Loads .env and .env.local
export APP_ENV=prod    # Loads .env and .env.prod
```

**Docker Compose with Different Environments**:
```bash
# Development (localhost databases)
APP_ENV=local docker compose --env-file .env.local up

# Production (cloud databases, Git config)
APP_ENV=prod docker compose --env-file .env.prod up --build
```

**Database Host Mapping**:
- **Dev**: `jdbc:mysql://localhost:3307/accountsdb` (direct local access)
- **Prod (Docker)**: `jdbc:mysql://accountsdb:3306/accountsdb` (container network)
- **Prod (K8s)**: `jdbc:mysql://accountsdb-service.microservices.svc.cluster.local:3306/accountsdb` (DNS service discovery)

---

## 7. Configuration Management

### 7.1 ConfigServer Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Git Repo      │     │  ConfigServer   │     │  Client Service │
│  (/.config)     │────▶│    :8071        │────▶│  (accounts,etc) │
│                 │     │                 │     │                 │
│ accounts.yml    │     │ /{app}/{profile}│     │ application.yml │
│ accounts-prod.yml│    │                 │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### 7.2 Configuration Files Structure

**Location**: `.config/` directory (served by ConfigServer Git backend)

```
.config/
├── accounts.yml          # Accounts dev datasource config
├── accounts-prod.yml     # Accounts production datasource config
├── cards.yml             # Cards dev datasource config
├── cards-prod.yml        # Cards production datasource config
├── loans.yml             # Loans dev datasource config
└── loans-prod.yml        # Loans production datasource config
```

**Important**: These files contain **only** `spring.datasource` properties (username, password, URL). All other configuration (JPA, Flyway, Eureka, RabbitMQ, actuator) lives in each service's local `application.yml`.

### 7.3 Database Configuration by Profile

| Service | Default Profile (dev) | Prod Profile |
|---------|----------------------|--------------|
| Accounts | `jdbc:mysql://localhost:3306/accountsdb` | `jdbc:mysql://accountsdb:3306/accountsdb` |
| Cards | `jdbc:mysql://localhost:3306/cardsdb` | `jdbc:mysql://cardsdb:3306/cardsdb` |
| Loans | `jdbc:mysql://localhost:3306/loansdb` | `jdbc:mysql://loansdb:3306/loansdb` |

**The only difference between profiles is the database hostname**: `localhost` (dev, direct access) vs. Docker service name (prod, container networking).

### 7.4 Config Refresh Mechanism

**Manual Refresh**:
```bash
# POST to refresh endpoint
curl -X POST http://localhost:8080/actuator/refresh
```

**Automatic Refresh via RabbitMQ**:
1. Config change pushed to Git repository
2. Git webhook triggers ConfigServer `/monitor` endpoint
3. ConfigServer publishes refresh event to RabbitMQ
4. All services receive event and refresh configuration

### 7.5 Encryption/Decryption

**Encrypt Property**:
```bash
curl -X POST http://localhost:8071/encrypt -d "mySecretPassword"
# Returns encrypted value (e.g., 7fb9b5e4c8a8d9f2...)
```

**Use Encrypted Value**:
```yaml
# In config file
password: '{cipher}7fb9b5e4c8a8d9f2...'
```

**Decrypt Property**:
```bash
curl http://localhost:8071/decrypt -d "{cipher}7fb9b5e4c8a8d9f2..."
```

### 7.6 Profile Management

**Available Profiles**:
- `default` - Development configuration (MySQL on localhost)
- `prod` - Production configuration (MySQL on Docker service names)

**ConfigServer Profile Selection**:
- `git` (default/production): Remote Git repository backend
- `native` (dev fallback): Classpath-based (non-functional - directories don't exist)

**Activate Profile**:
```bash
# Command line
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Environment variable
export SPRING_PROFILES_ACTIVE=prod

# Docker Compose (.env file)
SPRING_PROFILES_ACTIVE=prod
```

---

## 8. Data Model

### 8.1 Entity Relationship Diagram

```
┌─────────────────────┐
│     CUSTOMER        │
├─────────────────────┤
│ PK customer_id      │
│    name             │
│    email            │
│    mobile_number    │
│    + audit fields   │
└──────────┬──────────┘
           │ 1:1
           │ (logical FK, no @ManyToOne)
           ▼
┌─────────────────────┐
│     ACCOUNTS        │
├─────────────────────┤
│ PK account_number   │
│    customer_id      │◀─── Logical FK (no DB constraint)
│    account_type     │
│    branch_address   │
│    communication_sw │◀─── Boolean: tracks async notification status
│    + audit fields   │
└─────────────────────┘

┌─────────────────────┐
│      CARDS          │
├─────────────────────┤
│ PK card_id          │
│    mobile_number    │◀─── Logical join key (cross-service)
│    card_number      │
│    card_type        │
│    total_limit      │
│    amount_used      │
│    available_amount │
│    + audit fields   │
└─────────────────────┘

┌─────────────────────┐
│      LOANS          │
├─────────────────────┤
│ PK loan_id          │
│    mobile_number    │◀─── Logical join key (cross-service)
│    loan_number      │
│    loan_type        │
│    total_loan       │
│    amount_paid      │
│    outstanding_amount│
│    + audit fields   │
└─────────────────────┘
```

### 8.2 Audit Fields

All entities extend `BaseEntity` with automatic auditing:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;
    
    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime updatedAt;
    
    @LastModifiedBy
    @Column(insertable = false)
    private String updatedBy;
}
```

**Auditor Provider** (each service):
```java
@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of("{SERVICE}_MS"); // e.g., "ACCOUNTS_MS", "CARDS_MS", "LOANS_MS"
    }
}
```

### 8.3 Database Migration

**Current State**: Flyway manages schema with `V1__init_schema.sql` per service. JPA `ddl-auto: none` (schema not auto-generated).

**Flyway Configuration** (all business services):
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

**Migration Scripts**:
- `accounts/src/main/resources/db/migration/V1__init_schema.sql` - Creates `customer` and `accounts` tables
- `cards/src/main/resources/db/migration/V1__init_schema.sql` - Creates `cards` table
- `loans/src/main/resources/db/migration/V1__init_schema.sql` - Creates `loans` table

---

## 9. API Reference

### 9.1 Common Endpoint Patterns

All business services follow this pattern:

| Method | Endpoint | Request | Response | Status Codes |
|--------|----------|---------|----------|--------------|
| POST | `/api/create` | DTO or `mobileNumber` param | `ResponseDto` | 201 (Created), 417 (Failed) |
| GET | `/api/fetch` | `?mobileNumber={}` (+ `eazybank-correlation-id` header for cards/loans) | Resource DTO | 200 (OK), 404 (Not Found) |
| PUT | `/api/update` | DTO | `ResponseDto` | 200 (OK), 417 (Failed) |
| DELETE | `/api/delete` | `?mobileNumber={}` | `ResponseDto` | 200 (OK), 404 (Not Found) |

### 9.2 Accounts-Specific Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/create` | Create customer + account (body: `CustomerDto`) |
| GET | `/api/fetch` | Fetch customer + account by mobile number |
| PUT | `/api/update` | Update customer + account details |
| DELETE | `/api/delete` | Delete account + customer |
| GET | `/api/fetchCustomerDetails` | **Aggregated view**: customer + account + cards + loans (requires `eazybank-correlation-id` header) |

### 9.3 Request/Response Examples

**Create Account**:
```http
POST http://localhost:8080/api/create
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "mobileNumber": "9939321212",
  "accountsDto": {
    "accountType": "Savings"
  }
}
```

**Response**:
```json
{
  "statusCode": "201",
  "statusMsg": "Account created successfully"
}
```

**Fetch Cards** (with correlation ID):
```http
GET http://localhost:9000/api/fetch?mobileNumber=9939321212
eazybank-correlation-id: abc-123-def
```

**Response**:
```json
{
  "mobileNumber": "9939321212",
  "cardNumber": "4532123456789012",
  "cardType": "Credit Card",
  "totalLimit": 100000,
  "amountUsed": 25000,
  "availableAmount": 75000
}
```

### 9.4 Validation Rules

| Field | Validation Pattern |
|-------|-------------------|
| Mobile Number | `(^$|[0-9]{10})` - Exactly 10 digits |
| Account Number | `(^$|[0-9]{10})` - Exactly 10 digits |
| Card Number | `(^$|[0-9]{12})` - Exactly 12 digits (**note**: actual generated card numbers are 16 digits - validation mismatch) |
| Loan Number | `(^$|[0-9]{12})` - Exactly 12 digits |
| Email | Valid email format |
| Name | 5-30 characters |

### 9.5 Error Response Format

```json
{
  "apiPath": "/api/create",
  "errorCode": "BAD_REQUEST",
  "errorMessage": "Validation failed",
  "errorTime": "2026-04-03T10:30:00.000"
}
```

**Validation Errors** (field-level):
```json
{
  "mobileNumber": "must match regexp (^[0-9]{10})",
  "email": "must be a valid email address"
}
```

---

## 10. Exception Handling

### 10.1 Exception Hierarchy

```
RuntimeException
├── ResourceNotFoundException          (@ResponseStatus(NOT_FOUND))
├── CustomerAlreadyExistsException     (@ResponseStatus(BAD_REQUEST))
├── AccountAlreadyExistsException      (defined but unused)
├── CardAlreadyExistsException         (@ResponseStatus(BAD_REQUEST))
├── LoanAlreadyExistsException         (@ResponseStatus(BAD_REQUEST))
└── GlobalExceptionHandler (handles all via @ControllerAdvice)
```

### 10.2 GlobalExceptionHandler Behavior

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    
    // Validation errors → Map<String, String> (field → message), HTTP 400
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(...)
    
    // Custom exceptions → ErrorResponseDto
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFound(...)  // HTTP 404
    
    @ExceptionHandler(CustomerAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleCustomerAlreadyExists(...)  // HTTP 400
    
    // Generic exceptions → ErrorResponseDto, HTTP 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(...)
}
```

### 10.3 HTTP Status Code Mapping

| Status | Code | Usage |
|--------|------|-------|
| 200 OK | `STATUS_200` | Successful fetch/update |
| 201 Created | `STATUS_201` | Successful creation |
| 400 Bad Request | N/A | Validation failures, duplicate resources |
| 404 Not Found | N/A | Resource not found |
| 417 Expectation Failed | `STATUS_417` | Business logic failures (update/delete) |
| 500 Internal Server Error | N/A | Generic unhandled exceptions |

### 10.4 Adding Custom Exceptions

```java
// 1. Create exception class
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(String.format("%s not found with the given input data %s : '%s'", 
            resourceName, fieldName, fieldValue));
    }
}

// 2. Register in GlobalExceptionHandler
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ErrorResponseDto> handleResourceNotFound(
    ResourceNotFoundException ex, HttpServletRequest request) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        request.getRequestURI(),
        HttpStatus.NOT_FOUND,
        ex.getMessage(),
        LocalDateTime.now()
    );
    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
}
```

---

## 11. Testing Guide

### 11.1 Test Classes Structure

```
src/test/java/
└── com/ggoutos/{service}/
    ├── {Service}ApplicationTests.java  # Context load test + main method validation
    ├── controller/
    │   └── {Service}ControllerTest.java  # Controller tests (@WebMvcTest)
    ├── service/impl/
    │   └── {Service}ServiceImplTest.java  # Service tests (@ExtendWith(MockitoExtension))
    └── entity/
        └── {Entity}Test.java  # Entity tests (getters/setters, equals, hashCode, toString)
```

### 11.2 Test Types

**Current Test Coverage** (Accounts, Cards, Loans, Gateway Services):

**Context Load Test** (all services):
```java
@SpringBootTest
class AccountsApplicationTests {
    @Test
    void contextLoads() {
        // Verifies Spring context initializes successfully
    }

    @Test
    void testMainMethod() {
        assertDoesNotThrow(() -> AccountsApplication.main(new String[]{}));
    }
}
```

**Gateway Filter Tests** (RequestTraceFilter, ResponseTraceFilter, FilterUtility):
- `RequestTraceFilterTest.java` - Tests correlation ID generation and propagation
  - Tests using existing correlation ID when present
  - Tests generating UUID when correlation ID missing
  - Uses @Nested and @DisplayName for organization
  - Uses StepVerifier for reactive Mono assertions
- `ResponseTraceFilterTest.java` - Tests response header injection
  - Verifies GlobalFilter bean creation
  - Validates correlation ID addition to response
- `FilterUtilityTest.java` - Tests header manipulation utilities
  - Tests getCorrelationId(), setCorrelationId(), setRequestHeader()
  - Uses @Nested classes for logical grouping

**Audit Tests**:
- `AuditAwareImplTest.java` - Tests auditor provider per service
  - Validates correct auditor string returned (e.g., "ACCOUNTS_MS")

**Controller Tests** (`@WebMvcTest`):
- `LoansControllerTest.java`, `CardsControllerTest.java` - Tests for business endpoints
  - Validates successful creation (201), fetch (200), update (200), delete (200)
  - Validates validation errors (400), not found (404), business failures (417)
  - Uses `@MockitoBean` for service mocking (Spring Boot 4.x pattern)
  - Organized with @Nested classes and @DisplayName annotations

**Exception Handler Tests** (`@WebMvcTest`):
- `GlobalExceptionHandlerTest.java` (multiple services) - Comprehensive exception handling tests
  - Tests ResourceNotFoundException (404)
  - Tests *AlreadyExistsException variants (400)
  - Tests validation errors (400)
  - Tests generic exceptions (500)
  - Tests ConstraintViolationException (400)
  - Uses @Nested for logical grouping of related test cases
  - Uses ObjectMapper for JSON assertions

**Service Tests** (Unit):
- `CustomersServiceImplTest.java` (Accounts) - Tests customer details aggregation
  - Validates successful customer details retrieval with cards/loans
  - Validates ResourceNotFoundException when customer/account not found
  - Validates correlation ID propagation to Feign clients
  - Uses `@ExtendWith(MockitoExtension)` with `@Mock` and `@InjectMocks`

**Entity Tests**:
- `CustomerTest.java`, `AccountsTest.java` - Entity validation
  - Getters/setters validation
  - `equals()` and `hashCode()` tests (based on primary key)
  - `toString()` validation
  - Entity inheritance tests (extends `BaseEntity`)
  - Constructor tests (no-args)

**Modern Test Patterns** (used across all tests):
- `@Nested` - Groups related tests into logical sections
- `@DisplayName` - Provides human-readable test descriptions
- `@ExtendWith(MockitoExtension.class)` - Unit test extension (no Spring context)
- `@WebMvcTest(ControllerClass.class)` - Controller slice test (Spring context, mocked services)
- `@SpringBootTest` - Full integration test (complete Spring context)
- `MockMvc` - Test REST endpoints without starting server
- `@MockitoBean` - Mock Spring beans in test context (Boot 4.x)
- `@Mock`, `@InjectMocks` - Mockito annotations for unit tests
- `StepVerifier` - Verify reactive Stream behavior (for gateway tests)

**Recommended Test Types** (not yet implemented):

**Repository Tests** (`@DataJpaTest`):
```java
@DataJpaTest
class AccountsRepositoryTest {
    @Autowired
    private AccountsRepository accountsRepository;
    
    @Test
    void testFindByCustomerId() {
        // Test repository methods
    }
}
```

**Controller Tests** (`@WebMvcTest`):
```java
@WebMvcTest(AccountsController.class)
class AccountsControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private IAccountsService accountsService;
    
    @Test
    void testCreateAccount() throws Exception {
        mockMvc.perform(post("/api/create")...)
               .andExpect(status().isCreated());
    }
}
```

**Service Tests** (Unit):
```java
@ExtendWith(MockitoExtension.class)
class AccountsServiceImplTest {
    @Mock
    private AccountsRepository accountsRepository;
    
    @InjectMocks
    private AccountsServiceImpl accountsService;
    
    @Test
    void testCreateAccount() {
        // Test service logic
    }
}
```

### 11.3 Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=AccountsApplicationTests

# With coverage (add jacoco-maven-plugin)
mvn clean test jacoco:report
```

### 11.4 Test Coverage Expectations

**Current State**: Comprehensive test coverage with context load tests, gateway filter tests, exception handler tests, audit tests, and basic entity tests across all services. Modern testing patterns (@Nested, @DisplayName) in use.

**Recommended Coverage Enhancements**:
- Repository layer tests (`@DataJpaTest`) with custom query validation
- Integration tests for cross-service communication (Feign clients)
- Stream/messaging integration tests (Spring Cloud Stream Test Binder)
- End-to-end API tests through gateway
- Performance tests for critical paths

**JaCoCo Code Coverage Threshold**: 80% minimum enforced at build time via `jacoco-maven-plugin`. Coverage report generated at `target/site/jacoco/index.html` after `mvn clean test jacoco:report`.

**Coverage by Layer** (Target):
- Services: 80%+ (business logic core)
- Controllers: 70%+ (endpoint mappings, validation)
- Repositories: 50%+ (custom queries)
- Exceptions: 100% (error handling paths)
- Gateway Filters: 80%+ (correlation ID generation/propagation)

---

## 12. Security

### 12.1 OAuth2 & Keycloak Authentication

**Keycloak Setup**:
- Runs on port 7080 (Docker Compose)
- Admin credentials: username `admin`, password `admin` (set via `KC_BOOTSTRAP_ADMIN_*`)
- Realm: `master`
- Protocol endpoint: `http://keycloak:8080/realms/master/protocol/openid-connect`

**JWT Configuration** (Gateway):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Keycloak JWK Set endpoint for JWT validation
          jwk-set-uri: "http://${KEYCLOAK_HOST:localhost}:${KEYCLOAK_PORT:7080}/realms/master/protocol/openid-connect/certs"
```

**Bearer Token Authentication**:
- All API requests must include OAuth2 bearer token in `Authorization` header
- Example: `Authorization: Bearer <jwt_token>`
- Tokens validated against Keycloak JWK set
- Invalid/expired tokens result in HTTP 401 Unauthorized

**Getting Tokens**:
```bash
# Request access token from Keycloak
curl -X POST \
  http://localhost:7080/realms/master/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=<client_id>&client_secret=<client_secret>'

# Use token in API requests
curl -H 'Authorization: Bearer <token>' http://localhost:8072/goutos/bank/accounts/api/fetch
```

### 12.2 ConfigServer Security

**Basic Authentication**:
```yaml
# application.yml (configserver)
spring:
  security:
    user:
      name: ${CONFIG_SERVER_USER}
      password: ${CONFIG_SERVER_PASSWORD}
```

**Security Filter Chain**:
- CSRF protection disabled (stateless API)
- `/actuator/health/**` publicly accessible (health checks)
- All other endpoints require authentication

### 12.3 API Security Considerations

**Current State**: OAuth2 JWT authentication enforced at gateway level via Keycloak resource server mode.

**Gateway Server**: Acts as OAuth2 resource server. Validates JWT tokens and forwards authenticated requests to backend services via load balancer.

**Backend Services**: Accept requests only from gateway (internal Docker network), requiring correlation ID header for tracing.

**Recommended Additions** (Future):
1. Role-based access control (RBAC) per endpoint
2. Mutual TLS (mTLS) between services
3. API rate limiting per client/token
4. OAuth2 scopes enforcement

### 12.4 Secrets Management

**Do NOT commit**:
- Database passwords
- Keycloak admin credentials
- API keys
- Encryption keys
- OAuth2 client secrets
- Git tokens
- JWT signing keys

**Use environment variables or Docker secrets**:
```yaml
# docker-compose.yml
services:
  accounts:
    environment:
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - KEYCLOAK_HOST=${KEYCLOAK_HOST}
  keycloak:
    environment:
      - KC_BOOTSTRAP_ADMIN_PASSWORD=${KC_ADMIN_PASSWORD}
```

**`.env.local`** (git-ignored) should contain local development secrets:
```bash
# .env.local
MYSQL_ROOT_PASSWORD=root123
CONFIG_SERVER_USER=admin
CONFIG_SERVER_PASSWORD=admin123
KC_BOOTSTRAP_ADMIN_PASSWORD=admin
```

**`.env.prod`** contains production secrets (must be secured in CI/CD pipeline, NOT in git).

---

## 13. Monitoring & Observability

### 13.1 Observability Stack Architecture

The platform implements a modern observability stack using the **Grafana + Loki + Prometheus + Tempo (GLPT)** pattern with automatic instrumentation:

```
┌─────────────────────────────────────────────────────────────────┐
│                    All Microservices                             │
│   [OpenTelemetry Javaagent auto-instrumentation enabled]        │
│                         │ │ │                                     │
│         ┌───────────────┼─┼─┼───────────────┐                   │
│         │               │ │ │               │                   │
│         ▼               ▼ ▼ ▼               ▼                   │
│     Traces          Metrics              Logs                   │
│   (via OTEL)      (via Micrometer)    (via Docker/Alloy)       │
│         │               │                   │                   │
│         └───────┬───────┴───────┬───────────┘                   │
│                 │               │                                │
│         ┌───────▼────┐   ┌──────▼────────┐                     │
│         │   Tempo    │   │ Prometheus/   │                      │
│         │  :4318     │   │ Loki          │                      │
│         │ (Traces)   │   │ :3100/:9090   │                      │
│         └───────┬────┘   └──────┬────────┘                      │
│                 │               │                                │
│                 └───────┬───────┘                                │
│                         ▼                                        │
│                    ┌─────────┐                                   │
│                    │ Grafana │                                   │
│                    │ :3000   │                                   │
│                    │ (UI/    │                                   │
│                    │Dashboards)                                  │
│                    └─────────┘                                   │
└─────────────────────────────────────────────────────────────────┘
```

**Components**:
- **Tempo** (port 3110, OTEL receiver 4318): Scalable distributed tracing backend for storing and querying traces
- **Prometheus** (port 9090): Time-series database for metrics collection from `/actuator/prometheus`
- **Loki** (port 3100): Log aggregation system for container logs via Alloy
- **Grafana** (port 3000): Unified visualization dashboard connecting to Tempo, Prometheus, and Loki
- **Alloy** (port 12345): Grafana Agent for collecting logs from Docker containers and sending to Loki
- **MinIO** (backend storage for Loki): S3-compatible object storage for log persistence

### 13.2 OpenTelemetry Tracing

**Automatic Instrumentation**:
```bash
# All services run with OTEL javaagent (from .env)
JAVA_TOOL_OPTIONS='-javaagent:/app/libs/opentelemetry-javaagent-2.26.1.jar'
OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318
```

**What's Automatically Instrumented**:
- HTTP requests/responses (Spring Web, WebFlux)
- Database calls (Hibernate, MySQL driver)
- Message queue operations (RabbitMQ)
- Service-to-service calls (Feign clients)
- Cache operations (Redis)

**Accessing Traces**:
1. Navigate to Grafana: http://localhost:3000
2. Select "Tempo" data source
3. Search by service name (`spring.application.name`), trace ID, or span status
4. View distributed trace waterfall with latency breakdown

### 13.3 Metrics & Alerting

**Prometheus Scrape Configuration**:
- Scrapes `/actuator/prometheus` endpoint from all services (10s interval)
- Stores 24-hour retention by default
- Accessible at http://localhost:9090

**Key Metrics**:
- `http_server_requests_seconds_*` - HTTP endpoint latency
- `jvm_memory_*` - JVM memory usage
- `resilience4j_circuitbreaker_*` - Circuit breaker states
- `db_connection_pool_*` - Database connection pool stats
- `spring_cloud_gateway_requests_*` - Gateway request metrics

**Custom Prometheus Queries in Grafana**:
```promql
# Average endpoint latency over 5 minutes
rate(http_server_requests_seconds_sum{service="accounts"}[5m]) / rate(http_server_requests_seconds_count{service="accounts"}[5m])

# Circuit breaker trips
increase(resilience4j_circuitbreaker_calls_total{state="closed_to_open"}[5m])

# Database connection pool exhaustion
max(db_pool_size - db_pool_active_connections)
```

### 13.4 Log Aggregation (Loki)

**Log Collection**:
- Alloy collects logs from all Docker containers via `/var/run/docker.sock`
- Logs tagged with `container` label for identification
- Sent to Loki write endpoint: `http://gateway:3100/loki/api/v1/push`
- Tenant ID: `tenant1`

**Accessing Logs**:
1. Navigate to Grafana: http://localhost:3000
2. Select "Loki" data source
3. Query by label selector: `{container="accounts-ms"}` or `{container="gateway-ms"}`
4. View logs with search and filtering

### 13.5 Distributed Tracing Pattern

**Correlation ID Propagation**:
1. **Gateway** receives request, generates/passes `eazybank-correlation-id` UUID header
2. **RequestTraceFilter** (pre-filter) ensures correlation ID is present
3. **Service-to-Service**: Accounts passes correlation ID to Feign calls (Cards/Loans)
4. **OpenTelemetry**: Adds correlation ID as `trace_id` in spans
5. **Logging**: Log pattern includes `%X{trace_id},%X{span_id}` for trace context

**Example Trace Flow**:
```
Gateway (trace_id: abc123)
  ├─ RequestTraceFilter [generates correlation ID]
  ├─ Route to Accounts service
  │   ├─ AccountsController [logs with trace_id]
  │   ├─ CustomersServiceImpl
  │   │   ├─ Feign call to Cards [passes trace_id header]
  │   │   │   ├─ CardsController
  │   │   │   └─ CardsServiceImpl
  │   │   ├─ Feign call to Loans [passes trace_id header]
  │   │   │   ├─ LoansController
  │   │   │   └─ LoansServiceImpl
  │   │   └─ Aggregate response
  │   └─ ResponseTraceFilter [adds trace_id to response header]
  └─ Client receives response with trace_id
```

### 13.6 Actuator Endpoints

| Endpoint | URL | Description | Access |
|----------|-----|-------------|--------|
| Health | `/actuator/health` | Application health status | Public |
| Readiness | `/actuator/health/readiness` | Used by Kubernetes/Docker probes | Public |
| Liveness | `/actuator/health/liveness` | Service liveness check | Public |
| Info | `/actuator/info` | Application information | Public |
| Metrics | `/actuator/metrics` | Available metrics list | Public |
| Prometheus | `/actuator/prometheus` | Prometheus-formatted metrics | Public |
| Refresh | `/actuator/refresh` | Refresh configuration | Unrestricted |
| BusRefresh | `/actuator/busrefresh` | Refresh via message bus | Unrestricted |
| Gateway Routes | `/actuator/gateway/routes` | Gateway route definitions | Unrestricted |
| Circuit Breakers | `/actuator/circuitbreakers` | Resilience4j status | Unrestricted |
| Shutdown | `/actuator/shutdown` | Graceful shutdown | Unrestricted |

### 13.7 Health Checks

**Readiness Probe** (used by Docker Compose & Kubernetes):
```bash
# Docker Compose health check
test: [ "CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1" ]
interval: 10s
timeout: 5s
retries: 5
start_period: 20s
```

**Health Check Response**:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "redis": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```


---

## 14. Troubleshooting

### 14.1 Common Issues

**Issue 1: ConfigServer Connection Failed**
```
Error: Could not locate PropertySource
```
**Solution**:
- Verify ConfigServer is running: `curl http://localhost:8071/actuator/health`
- Check `spring.cloud.config.uri` in `application.yml`
- Verify credentials if security enabled

**Issue 2: Database Connection Refused**
```
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
```
**Solution**:
- Verify MySQL is running: `docker ps | grep mysql`
- Check connection URL in config file
- Verify port (3306 dev, 3307/3308/3309 prod via Docker mapping)

**Issue 3: RabbitMQ Connection Failed**
```
org.springframework.amqp.AmqpConnectException
```
**Solution**:
- Verify RabbitMQ is running: `docker compose ps rabbit`
- Check `spring.rabbitmq.host` configuration
- Verify credentials (default: guest/guest)

**Issue 4: Port Already in Use**
```
Port 8080 is already in use
```
**Solution**:
```bash
# Find process using port (Windows)
netstat -ano | findstr :8070
# This shows the app using port 8080.
tasklist /FI "PID eq <PID>"
# Kill process <PID>
taskkill /PID <PID> /F

# Or change port in application.yml
server:
  port: 8081
```

### 14.2 Debug Mode Activation

**Enable Full Debug Logging**:
```yaml
logging:
  level:
    root: DEBUG
```

**Start with Debug Profile**:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=debug
```

### 14.3 Log Analysis

**Key Log Patterns**:
```
# Successful startup
Started {Service}Application in X.XXX seconds

# Config fetch
Located config server: http://configserver:8071

# Database connection
HikariPool-1 - Start completed.

# Error
ERROR ... Exception: ...
```

---

## 15. Extending the Platform

### 15.1 Adding a New Microservice

**Step-by-Step**:

```bash
# 1. Create module directory
mkdir investments
cd investments

# 2. Create pom.xml (copy from existing service, update artifactId)
# 3. Create package structure
mkdir -p src/main/java/com/ggoutos/investments/{controller,service/impl,entity,dto,mapper,repository,exception,audit,constants}
mkdir -p src/main/resources/{db/migration}
mkdir -p src/test/java/com/ggoutos/investments

# 4. Create main application class with @EnableJpaAuditing, @EnableFeignClients, @OpenAPIDefinition
# 5. Create application.yml (configserver client config)
# 6. Add config to .config/investments.yml and .config/investments-prod.yml
# 7. Update docker-compose.yml with new service
# 8. Add route to GatewayServer RouteLocator
# 9. Build and test
```

**Required Files**:
- `pom.xml` - Maven configuration (inherit from parent)
- `InvestmentsApplication.java` - Entry point
- `application.yml` - Bootstrap config
- `IInvestmentsService.java` / `InvestmentsServiceImpl.java` - Service layer
- `InvestmentsController.java` - REST endpoints
- `Investments.java` / `InvestmentsDto.java` - Entity and DTO
- `InvestmentsMapper.java` - Static mapper
- `InvestmentsRepository.java` - Data access
- `InvestmentsConstants.java` - Constants
- `AuditAwareImpl.java` - Auditor provider
- `GlobalExceptionHandler.java` - Exception handling
- `InvestmentsApplicationTests.java` - Test class
- `V1__init_schema.sql` - Flyway migration

### 15.2 Adding New Endpoints

**Pattern**:
```java
@RestController
@RequiredArgsConstructor
@Tag(name = "Investments", description = "Investment management APIs")
public class InvestmentsController {
    
    private final IInvestmentsService investmentsService;
    
    @PostMapping("/api/invest")
    @Operation(summary = "Create investment", description = "Create a new investment record")
    public ResponseEntity<ResponseDto> createInvestment(
            @Valid @RequestBody InvestmentsDto investmentsDto) {
        // Implementation
    }
}
```

### 15.3 Inter-Service Communication

**Using Feign Clients** (recommended for synchronous calls):
```java
@FeignClient(name = "investments")
public interface InvestmentsFeignClient {
    @GetMapping(value = "/api/fetch", consumes = "application/json")
    InvestmentsDto fetchInvestmentDetails(
        @RequestHeader(name = "eazybank-correlation-id", required = true) String correlationId,
        @RequestParam String mobileNumber);
}
```

**Using StreamBridge** (recommended for asynchronous events):
```java
@RequiredArgsConstructor
@Service
public class InvestmentsServiceImpl implements IInvestmentsService {
    private final StreamBridge streamBridge;
    
    private void publishInvestmentEvent(Investment investment) {
        var investmentMsgDto = new InvestmentMsgDto(investment.getId(), investment.getType(), ...);
        boolean sent = streamBridge.send("publishInvestment-out-0", investmentMsgDto);
        log.info("Event published: {}", sent);
    }
}
```

**Event Processing Pattern** (Spring Cloud Function):
```java
// In message service
@Configuration
public class MessageFunctions {
    @Bean
    public Function<InvestmentMsgDto, InvestmentMsgDto> processInvestment() {
        return msg -> {
            log.info("Processing investment: {}", msg);
            // Perform async operations (notifications, auditing, etc.)
            return msg;
        };
    }
}
```

### 15.4 Database Migration Process

**Flyway Migration Script** (`V1__create_investments_table.sql`):
```sql
CREATE TABLE investments (
    investment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(255) NOT NULL,
    investment_type VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
```

---

## 16. Known Issues & Technical Debt

### 16.1 Critical Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| SEC-001 | `.env.prod` contains plaintext credentials and sensitive configuration | Security risk | **Critical** | Open |
| SEC-002 | Keycloak OAuth2 not fully integrated into service-to-service communication | Weak inter-service auth | **Critical** | Open |
| BUG-001 | CardsDto validates 12-digit card numbers, but service generates 16-digit numbers | Validation failure on update | **High** | Open |
| BUG-002 | ~~No database volume mounts~~ Database volumes now present but MinIO persistence may be insufficient for production | Data persistence | **Medium** | Partial Fix |
| OPS-001 | Keycloak credentials exposed in Docker Compose (admin/admin) | Security risk | **High** | Open |

### 16.2 Code Quality Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| CQ-001 | `main` methods are package-private across all services (non-standard) | Inconsistency | Low | Open |
| CQ-002 | Cards/Loans services have `@EnableFeignClients` but no Feign clients defined | Dead configuration | Low | Open |
| CQ-003 | `CardsConstants.CREDIT` and `DEBIT` defined but unused | Dead code | Low | Open |
| CQ-004 | ~~Gateway response header typo: `X-Respose-Time`~~ Fixed to `X-Response-Time` | API correctness | Low | **Fixed** ✅ |
| CQ-005 | Loans `updateLoan()`/`deleteLoan()` always return `true` (417 path unreachable) | Dead code path | Low | Open |
| CQ-006 | Inconsistent filter definition pattern in gateway (`@Component` vs `@Configuration`+`@Bean`) | Style inconsistency | Low | Open |
| CQ-007 | ConfigServer native profile is non-functional (classpath directories don't exist) | Broken fallback | Medium | Open |
| CQ-008 | Message service default binder is Kafka, but RabbitMQ is commonly associated with platform | Confusing configuration | Low | Open |

### 16.3 Security & Reliability Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| SEC-003 | ConfigServer CSRF disabled | CSRF vulnerability (low risk for internal API) | Low | Open |
| SEC-004 | ~~Shutdown endpoints unrestricted~~ Now exposed but should be restricted in prod | Denial of service risk | Medium | Partial |
| SEC-005 | Weak encryption key in prod (`very_secret_key`) | Weak property encryption | Medium | Open |
| REL-001 | ~~No circuit breaker on Feign clients~~ Circuit breaker now enabled via Resilience4j | Cascading failures | Medium | **Fixed** ✅ |
| REL-002 | Random ID generators use `java.util.Random` (not `SecureRandom`, no collision check) | ID collisions at scale | Low | Open |
| OBS-001 | OTEL javaagent adds 10-15% startup latency | Performance | Low | Trade-off |

### 16.4 Configuration Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| CF-001 | Git profile hardcoded to `master` branch | Branch compatibility | Low | Open |
| CF-002 | ConfigServer only serves datasource credentials (not full config) | Limited centralization | Low | Open |
| CF-003 | ~~Gateway has no ConfigServer integration~~ Still true, gateway uses local config | Static configuration | Medium | Open |
| CF-004 | Multiple `.env*` files (`.env`, `.env.local`, `.env.prod`) can be confusing | Configuration management | Low | Open |

### 16.5 Test Coverage

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| TEST-001 | Only contextLoads tests exist across most services | No business logic coverage | **High** | Open |
| TEST-002 | No gateway filter tests | Untested tracing logic | Medium | Open |
| TEST-003 | No repository tests | Untested custom queries | Medium | Open |
| TEST-004 | JaCoCo enforces 80% minimum coverage | Strict but good for quality | Medium | **Added** ✅ |

### 16.6 Observability & Monitoring

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| OBS-001 | OpenTelemetry stack fully deployed but may require fine-tuning | Operational complexity | Low | **New** ✅ |
| OBS-002 | Loki storage via MinIO may fill quickly without retention policies | Disk usage | Medium | **New** ✅ |
| OBS-003 | No alerting rules configured in Prometheus | No notifications on issues | Medium | **New** ✅ |
| OBS-004 | Redis caching not leveraged in application code | Unused infrastructure | Low | **New** ✅ |

### 16.7 Event-Driven Messaging

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| EVT-001 | Message service functions lack error handling and don't implement retry logic | Failed notifications silently lost | **High** | Open |
| EVT-002 | No dead-letter queue (DLQ) configured for failed messages | Data loss on processing failures | **High** | Open |
| EVT-003 | Stream bindings use default group/concurrency settings | May not handle production load | Medium | Open |
| EVT-004 | Accounts `updateCommunicationStatus()` properly invoked via AccountsFunctions Consumer bean, but no error handling in consumer | Notification failures not tracked | Medium | Open |
| EVT-005 | No monitoring/alerting on message queue depth or lag | Invisible queue buildup | Medium | Open |
| EVT-006 | Message service binder defaults to Kafka (not RabbitMQ) - can be overridden via `SPRING_CLOUD_STREAM_DEFAULT_BINDER` | Confusing since RabbitMQ emphasized in docs | Low | Open |

### 16.8 Planned Improvements

- [x] Add comprehensive observability stack (Grafana, Loki, Prometheus, Tempo)
- [x] Implement OAuth2/Keycloak authentication
- [x] Add circuit breaker pattern (Resilience4j) for Feign clients
- [x] Add database volume mounts for Docker persistence
- [x] Fix card number validation mismatch (12 vs 16 digits) - Needs verification
- [x] Implement event-driven messaging (StreamBridge + Spring Cloud Stream) for async notifications
- [ ] Add role-based access control (RBAC) per endpoint
- [ ] Configure Prometheus alerting rules
- [ ] Add distributed rate limiting (Redis backend)
- [ ] Implement mTLS between services
- [ ] Add comprehensive test coverage (target: 80%)
- [ ] Secure shutdown endpoints or remove them
- [ ] Move secrets to external secrets manager (HashiCorp Vault, AWS Secrets Manager)
- [ ] Add Keycloak client/realm configuration in code/IaC
- [ ] Reduce OTEL javaagent overhead
- [ ] Add error handling and retry logic to Message service functions
- [ ] Implement dead-letter queue (DLQ) for failed message processing
- [ ] Switch Message service default binder from Kafka to RabbitMQ for consistency

---

## Appendices

### A. Glossary

| Term | Definition |
|------|------------|
| **JPA** | Java Persistence API - ORM specification |
| **Hibernate** | JPA implementation |
| **ConfigServer** | Centralized configuration service |
| **RabbitMQ** | Message broker for config refresh |
| **Jib** | Docker image building tool by Google |
| **Buildpacks** | OCI image creation framework |
| **GraalVM** | High-performance JDK distribution |
| **Actuator** | Spring Boot production-ready features |

### B. Acronyms

- **DTO**: Data Transfer Object
- **JPA**: Java Persistence API
- **ORM**: Object-Relational Mapping
- **REST**: Representational State Transfer
- **API**: Application Programming Interface
- **JWT**: JSON Web Token
- **RBAC**: Role-Based Access Control
- **CORS**: Cross-Origin Resource Sharing
- **ELK**: Elasticsearch, Logstash, Kibana

### C. Reference Links

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Jib Maven Plugin](https://github.com/GoogleContainerTools/jib)
- [GraalVM Native Image](https://www.graalvm.org/latest/docs/getting-started/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Docker Compose Reference](https://docs.docker.com/compose/)

---

*Last Updated: 2026-04-20*
*Version: 4.1*
*Major Changes: Added event-driven messaging service (StreamBridge + Spring Cloud Stream + RabbitMQ), Message module for async email/SMS notifications, AccountsMsgDto for event payloads, documented inter-service async communication patterns, added known issues for event-driven messaging*
