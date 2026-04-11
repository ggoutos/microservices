# Comprehensive Analysis: EazyBank Microservices Project

> **Project Analysis Report** - Current state, architecture, issues, and recommendations for the EazyBank microservices platform.

**Last Updated**: 2026-04-06  
**Project Version**: 0.0.1-SNAPSHOT  
**Status**: Active Development  
**Latest PR**: #10 - Comprehensive test coverage for Accounts service

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Technology Stack with Versions](#2-technology-stack-with-versions)
3. [Services Overview](#3-services-overview)
4. [Database Schemas](#4-database-schemas)
5. [Configuration Server Details](#5-configuration-server-details)
6. [API Gateway Details](#6-api-gateway-details)
7. [Build Commands](#7-build-commands)
8. [API Documentation Endpoints](#8-api-documentation-endpoints)
9. [Actuator Endpoints](#9-actuator-endpoints)
10. [Docker Infrastructure](#10-docker-infrastructure)
11. [Package Structure Pattern](#11-package-structure-pattern)
12. [Testing Strategy & Coverage](#12-testing-strategy--coverage) **(NEW - PR #10)**
13. [Identified Patterns](#13-identified-patterns)
14. [Critical Issues Found](#14-critical-issues-found)
15. [Code Quality Issues](#15-code-quality-issues)
16. [Security Concerns](#16-security-concerns)
17. [AI Assistant Configurations](#17-ai-assistant-configurations)
18. [Summary Statistics](#18-summary-statistics)
19. [Recommendations](#19-recommendations)

---

## 1. Executive Summary

### Project Overview
The EazyBank microservices platform is a banking application built with Spring Boot 4.0.5, Spring Cloud 2025.1.1, and Java 25. It consists of **7 Maven modules**: utils (shared library), eurekaserver (service discovery), configserver (centralized configuration), gatewayserver (API gateway), accounts, cards, and loans (business services).

### Architecture Highlights
- **API Gateway**: Spring Cloud Gateway (WebFlux-based) on port 8072 as single entry point
- **Service Discovery**: Eureka Server for dynamic service registration
- **Centralized Configuration**: Config Server with Git backend (production) and native fallback (development)
- **Inter-Service Communication**: Feign clients (Accounts → Cards/Loans) with correlation ID propagation
- **Database Strategy**: MySQL for all services with JPA/Hibernate, no explicit FK constraints, logical joins via `mobile_number`
- **Database Migration**: Flyway for schema version control (`V1__init_schema.sql` per service)
- **Distributed Tracing**: Gateway generates `eazybank-correlation-id` header, propagated to downstream services
- **Containerization**: Docker images via Jib, Buildpacks, or custom Dockerfiles (Accounts only)
- **Test Coverage**: Comprehensive unit tests for Accounts service (PR #10)

### Current State Assessment
- ✅ **Strengths**: Modern tech stack (Spring Boot 4.0.5, Java 25), clean separation of concerns, comprehensive Docker orchestration, Flyway schema management, API gateway with tracing, **comprehensive test coverage for Accounts service (PR #10 - 45+ tests)**
- ⚠️ **Issues**: Card number validation mismatch (12 vs 16 digits), no database persistence in Docker, exposed credentials in `.env.prod`, **incomplete test coverage (cards/loans services still lack tests)**
- 🔧 **Resolved**: Duplicate test files removed, LoansController standardized to `@RequiredArgsConstructor`, `@Table` annotation added to Loans entity, copy-paste errors fixed, **Accounts service test coverage added (PR #10)**, code style fixes in Accounts.java and CustomersServiceImpl.java

---

## 2. Technology Stack with Versions

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 25 | Runtime & compilation |
| **Maven** | 3.9+ | Build automation (multi-module) |
| **Spring Boot** | 4.0.5 | Application framework |
| **Spring Cloud** | 2025.1.1 | Microservices patterns |
| **Spring Data JPA** | Included | Data persistence |
| **Spring Security** | Included | Config server security |
| **Spring WebFlux** | Included | Reactive gateway (gatewayserver only) |
| **Hibernate ORM** | Included | JPA implementation |
| **MySQL Connector** | Runtime | MySQL database driver |
| **Lombok** | Included | Boilerplate reduction |
| **SpringDoc OpenAPI** | 3.0.2 | API documentation (Swagger UI) |
| **Spring Cloud Config** | Included | Centralized configuration |
| **Spring Cloud Bus** | Included | Config refresh via RabbitMQ |
| **Spring Cloud Netflix Eureka** | Included | Service discovery |
| **Spring Cloud OpenFeign** | Included | Declarative REST clients |
| **Spring Cloud Gateway** | Included | API Gateway (WebFlux-based) |
| **Flyway** | Included | Database migration tool |
| **RabbitMQ** | 4.2.5-management | Message broker for config refresh |
| **GraalVM Native** | Included | Native image compilation |
| **Jib Maven Plugin** | 3.5.1 | Container image building |
| **Docker** | Latest | Containerization |
| **JUnit 5** | Included | Unit testing framework |
| **Mockito** | Included | Mocking framework |
| **AssertJ** | Included | Fluent assertions |
| **JaCoCo** | 0.8.14 | Code coverage reporting |

---

## 3. Services Overview

### 3.1 Eureka Server (Port 8070)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.eurekaserver` |
| **Main Class** | `EurekaserverApplication.java` |
| **Annotations** | `@EnableEurekaServer`, `@SpringBootApplication` |
| **Database** | None (stateless) |
| **Docker Image** | `ggoutos/eurekaserver:jib` |
| **ConfigServer Client** | No (self-configured) |
| **Security** | None (internal service) |

**Key Features:**
- Standalone mode (`register-with-eureka: false`, `fetch-registry: false`)
- Service registration and discovery
- Health checks with readiness/liveness probes
- Actuator endpoints: `health`, `info`, `refresh`, `busrefresh`, `shutdown`

**Note**: `main` method is package-private (non-standard but functional).

---

### 3.2 Utils Module

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.utils.dto` |
| **Type** | Shared library (JAR) |
| **Dependencies** | `springdoc-openapi-starter-webmvc-ui` (v3.0.2) |
| **Spring Boot Repackaging** | Disabled (plain JAR) |

**Shared DTOs** (all use Lombok `@Data` and OpenAPI `@Schema`):
- `CustomerDto.java` - Customer data (name, email, mobileNumber, accountsDto)
- `CustomerDetailsDto.java` - Aggregated customer view (accounts, cards, loans)
- `AccountsDto.java` - Account data (accountNumber, accountType, branchAddress)
- `CardsDto.java` - Card data (mobileNumber, cardNumber, cardType, limits)
- `LoansDto.java` - Loan data (mobileNumber, loanNumber, loanType, balances)
- `ResponseDto.java` - Standard success response (statusCode, statusMsg)
- `ErrorResponseDto.java` - Standard error response (apiPath, errorCode, errorMessage, errorTime)

---

### 3.3 ConfigServer (Port 8071)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.configserver` |
| **Main Class** | `ConfigserverApplication.java` |
| **Annotations** | `@EnableConfigServer`, `@SpringBootApplication` |
| **Database** | None (stateless) |
| **Docker Image** | `ggoutos/configserver:jib` |
| **Security** | Basic Auth (configurable via env vars) |
| **Config Backend** | Git (prod/default) / Native classpath (dev fallback - non-functional) |
| **Encryption** | Symmetric key via `ENCRYPTION_KEY` env var |

**Key Features:**
- Git-backed configuration with fallback to native
- RabbitMQ integration for distributed config refresh
- Encryption support (`/encrypt`, `/decrypt` endpoints)
- Health checks with readiness/liveness probes
- Security: Basic auth, CSRF disabled, `/actuator/health/**` public

**Note**: ConfigServer only serves datasource credentials. All other configuration lives in each service's local `application.yml`.

---

### 3.4 GatewayServer (Port 8072)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.gatewayserver` |
| **Main Class** | `GatewayserverApplication.java` |
| **Stack** | WebFlux (Reactive) - **only service using reactive stack** |
| **Database** | None (stateless) |
| **Docker Image** | `ggoutos/gatewayserver:jib` |
| **ConfigServer Client** | No (self-configured) |
| **Security** | None (open reverse proxy) |

**Key Features:**
- Single entry point for all client traffic
- Route definitions via `RouteLocator` bean (discovery locator disabled)
- Distributed tracing via `eazybank-correlation-id` header
- Three routes: accounts, loans, cards (pattern: `/goutos/bank/{service}/**`)
- Custom filters: `RequestTraceFilter` (pre, Order 1), `ResponseTraceFilter` (post)

**Route Pattern**:
```
Incoming:  /goutos/bank/{service}/**
           ↓  Path rewrite strips prefix
           ↓  Forward to lb://{SERVICE} (via Eureka)
Upstream:  /**
```

**Note**: No authentication layer. Response header has typo: `X-Respose-Time` (should be `X-Response-Time`).

---

### 3.5 Accounts Service (Port 8080)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.accounts` |
| **Main Class** | `AccountsApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, accountsdb:3306 prod) |
| **Docker Image** | `ggoutos/accounts:jib` |
| **Custom Dockerfile** | Yes (multi-stage: JVM via jlink + GraalVM Native) |
| **Feign Clients** | Cards, Loans |
| **Schema Management** | Flyway (`V1__init_schema.sql`) |

**Entities:**
- `Customer` (customer_id PK, name, email, mobile_number)
- `Accounts` (account_number PK, customer_id logical FK, account_type, branch_address)

**Relationship:** One-to-one (Customer → Accounts via customer_id column, no JPA `@ManyToOne`)

**Key Classes:**
- `AccountsController.java` - Accounts CRUD endpoints
- `CustomerController.java` - Aggregated customer details (requires `eazybank-correlation-id` header)
- `IAccountsService.java` / `AccountsServiceImpl.java` - Account CRUD
- `ICustomersService.java` / `CustomersServiceImpl.java` - Cross-service aggregation
- `CardsFeignClient.java` / `LoansFeignClient.java` - Inter-service clients

---

### 3.6 Cards Service (Port 9000)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.cards` |
| **Main Class** | `CardsApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, cardsdb:3306 prod) |
| **Docker Image** | `ggoutos/cards:jib` |
| **Schema Management** | Flyway (`V1__init_schema.sql`) |

**Entities:**
- `Cards` (card_id PK, `@Table(name = "cards")`, mobile_number, card_number, card_type, total_limit, amount_used, available_amount)

**Key Classes:**
- `CardsController.java` - Cards CRUD endpoints (uses `@Slf4j`)
- `ICardsService.java` / `CardsServiceImpl.java` - Card management (uses `@Slf4j`)
- `CardsMapper.java` - Static entity/DTO mapping
- `CardsRepository.java` - Data access (findByMobileNumber, findByCardNumber)

**Note:** `/api/fetch` requires `eazybank-correlation-id` header. `@EnableFeignClients` present but no Feign clients defined.

---

### 3.7 Loans Service (Port 8090)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.loans` |
| **Main Class** | `LoansApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, loansdb:3306 prod) |
| **Docker Image** | `ggoutos/loans:jib` |
| **Schema Management** | Flyway (`V1__init_schema.sql`) |

**Entities:**
- `Loans` (loan_id PK, `@Table(name = "loans")`, mobile_number, loan_number, loan_type, total_loan, amount_paid, outstanding_amount)

**Key Classes:**
- `LoansController.java` - Loans CRUD endpoints (uses `@Slf4j`)
- `ILoansService.java` / `LoansServiceImpl.java` - Loan management
- `LoansMapper.java` - Static entity/DTO mapping
- `LoansRepository.java` - Data access (findByMobileNumber, findByLoanNumber)

**Note:** `/api/fetch` requires `eazybank-correlation-id` header. `updateLoan()`/`deleteLoan()` always return `true`.

---

## 4. Database Schemas

### Accounts Service (MySQL)
```sql
CREATE TABLE customer (
    customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255),
    mobile_number VARCHAR(255),
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE TABLE accounts (
    account_number BIGINT PRIMARY KEY,
    customer_id BIGINT,
    account_type VARCHAR(255),
    branch_address VARCHAR(255),
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
    -- Note: No explicit FK constraint to customer table
);
```

### Cards Service (MySQL)
```sql
CREATE TABLE cards (
    card_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(15),
    card_number VARCHAR(100),
    card_type VARCHAR(100),
    total_limit INT,
    amount_used INT,
    available_amount INT,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
```

### Loans Service (MySQL)
```sql
CREATE TABLE loans (
    loan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(255),
    loan_number VARCHAR(255),
    loan_type VARCHAR(255),
    total_loan INT,
    amount_paid INT,
    outstanding_amount INT,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
```

**Cross-Service Join Key:** `mobile_number` (used for logical correlation across services, no FK constraints)

---

## 5. Configuration Server Details

### Backend Strategy
| Profile | Backend | Location |
|---------|---------|----------|
| `git` (default/prod) | Git Repository | `${GIT_URI}` with `/.config` search path, branch `master` |
| `native` (dev fallback) | Classpath | `classpath:/shared`, `classpath:/config` (**directories do not exist - non-functional**) |

### Configuration Files Served
| File | Profile | Database URL |
|------|---------|--------------|
| `accounts.yml` | default | `jdbc:mysql://localhost:3306/accountsdb` |
| `accounts-prod.yml` | prod | `jdbc:mysql://accountsdb:3306/accountsdb` |
| `cards.yml` | default | `jdbc:mysql://localhost:3306/cardsdb` |
| `cards-prod.yml` | prod | `jdbc:mysql://cardsdb:3306/cardsdb` |
| `loans.yml` | default | `jdbc:mysql://localhost:3306/loansdb` |
| `loans-prod.yml` | prod | `jdbc:mysql://loansdb:3306/loansdb` |

**Note:** These files contain **only** `spring.datasource` properties (username, password, URL). All other configuration (JPA, Flyway, Eureka, RabbitMQ, actuator) lives in each service's local `application.yml`.

### Security Configuration
- **Username:** `${CONFIG_SERVER_USER}` (env var, no default)
- **Password:** `${CONFIG_SERVER_PASSWORD}` (env var, no default)
- **Encryption Key:** `${ENCRYPTION_KEY}` (env var)
- **Endpoints Protected:** All except `/actuator/health/**`
- **CSRF:** Disabled (acceptable for stateless API)

### Config Refresh Flow
1. Git push triggers webhook to `/monitor` endpoint
2. ConfigServer publishes refresh event to RabbitMQ
3. All services listen on message bus and refresh config via `/actuator/busrefresh`

---

## 6. API Gateway Details

### Route Configuration

Routes are defined programmatically via a `RouteLocator` bean in `GatewayserverApplication.java`:

```java
public static final String DNS_PREFIX = "goutos/bank";

private Function<PredicateSpec, Buildable<Route>> createRoute(String service) {
    return p -> p
        .path("/" + DNS_PREFIX + "/" + service.toLowerCase() + "/**")
        .filters(f -> f.rewritePath("/" + DNS_PREFIX + "/" + service.toLowerCase() + "/(?<segment>.*)", "/${segment}")
            .addResponseHeader("X-Respose-Time", Instant.now().toString()))
        .uri("lb://" + service.toUpperCase());
}
```

### Registered Routes

| Incoming Path | Upstream Service | Load Balancer URI |
|---------------|------------------|-------------------|
| `/goutos/bank/accounts/**` | Accounts (port 8080) | `lb://ACCOUNTS` |
| `/goutos/bank/loans/**` | Loans (port 8090) | `lb://LOANS` |
| `/goutos/bank/cards/**` | Cards (port 9000) | `lb://CARDS` |

### Distributed Tracing Filters

| Filter | Type | Order | Purpose |
|--------|------|-------|---------|
| `RequestTraceFilter` | `GlobalFilter` | 1 (pre) | Generates/passes `eazybank-correlation-id` header |
| `ResponseTraceFilter` | `GlobalFilter` bean | post | Adds correlation ID to response headers |

**Correlation ID Flow:**
```
Client → Gateway (generates UUID if missing) → Downstream Service → Response
         ↑ eazybank-correlation-id header propagated throughout
```

---

## 7. Build Commands

### Development (Local)
```bash
# Build all services
mvn clean install

# Run individual service
cd accounts && mvn spring-boot:run

# Run tests only
mvn test

# CI-friendly version override
mvn clean install -Drevision=1.2.3
```

### Docker Images

#### Option 1: Jib (Recommended)
```bash
cd {service}
mvn compile jib:dockerBuild
# Creates: ggoutos/{service}:jib
```

#### Option 2: Buildpacks
```bash
cd {service}
mvn spring-boot:build-image
# Creates: ggoutos/{service}:spring
```

#### Option 3: Custom Dockerfile (Accounts Only)
```bash
cd accounts
docker build --target jvm -t ggoutos/accounts:latest .
docker build --target native -t ggoutos/accounts:native .
```

#### Option 4: Native Image (GraalVM)
```bash
mvn -Pnative native:compile
```

### Docker Compose (Full Stack)
```bash
cd .docker
docker compose up --build          # Dev
docker compose --env-file .env.prod up --build  # Production
```

---

## 8. API Documentation Endpoints

| Service | Port | Swagger UI | OpenAPI JSON |
|---------|------|------------|--------------|
| Eureka Server | 8070 | N/A | N/A |
| ConfigServer | 8071 | N/A | N/A |
| GatewayServer | 8072 | N/A | N/A |
| Accounts | 8080 | `http://localhost:8080/swagger-ui.html` | `/v3/api-docs` |
| Cards | 9000 | `http://localhost:9000/swagger-ui.html` | `/v3/api-docs` |
| Loans | 8090 | `http://localhost:8090/swagger-ui.html` | `/v3/api-docs` |

### API Endpoint Patterns (All Business Services)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/create` | Create resource | DTO / mobileNumber param | `ResponseDto` |
| GET | `/api/fetch` | Fetch by mobile number | `?mobileNumber={}` (+ correlation ID for cards/loans) | Resource DTO |
| PUT | `/api/update` | Update resource | DTO | `ResponseDto` |
| DELETE | `/api/delete` | Delete by mobile number | `?mobileNumber={}` | `ResponseDto` |

### Accounts-Specific Endpoint

| Method | Endpoint | Description | Headers |
|--------|----------|-------------|---------|
| GET | `/api/fetchCustomerDetails` | Aggregated customer view (accounts + cards + loans) | `eazybank-correlation-id` (required) |

### Validation Rules
- **Mobile Number:** 10 digits (`regexp = "(^$|[0-9]{10})"`)
- **Account Number:** 10 digits (`regexp = "(^$|[0-9]{10})"`)
- **Card Number:** 12 digits in DTO validation (`regexp = "(^$|[0-9]{12})"`) - **MISMATCH**: actual generated numbers are 16 digits
- **Loan Number:** 12 digits (`regexp = "(^$|[0-9]{12})"`)
- **Email:** Valid email format
- **Name:** 5-30 characters

---

## 9. Actuator Endpoints

| Service | Health | Info | Refresh | BusRefresh | Gateway | Shutdown |
|---------|--------|------|---------|------------|---------|----------|
| Eureka Server | `/actuator/health` | `/actuator/info` | N/A | N/A | N/A | `/actuator/shutdown` |
| ConfigServer | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` | N/A | N/A |
| GatewayServer | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` | `/actuator/gateway` | `/actuator/shutdown` |
| Accounts | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` | N/A | `/actuator/shutdown` |
| Cards | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` | N/A | `/actuator/shutdown` |
| Loans | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` | N/A | `/actuator/shutdown` |

**Health Check Probes:**
- Readiness: Includes RabbitMQ connectivity check (ConfigServer)
- Liveness: Basic application alive check
- All microservices use `wget` to check `/actuator/health/readiness` in Docker health checks

---

## 10. Docker Infrastructure

### Docker Compose Services

| Service | Image | Port | Dependencies | Health Check |
|---------|-------|------|--------------|--------------|
| `rabbit` | `rabbitmq:4.2.5-management-alpine` | 5672, 15672 | None | `rabbitmq-diagnostics check_port_connectivity` |
| `eurekaserver` | `ggoutos/eurekaserver:${IMAGE_TAG}` | 8070 | None | `wget /actuator/health/readiness` |
| `configserver` | `ggoutos/configserver:${IMAGE_TAG}` | 8071 | rabbit (healthy) | `wget /actuator/health/readiness` |
| `gatewayserver` | `ggoutos/gatewayserver:${IMAGE_TAG}` | 8072 | eurekaserver (healthy) | `wget /actuator/health/readiness` |
| `accountsdb` | `mysql:lts` | 3307:3306 | None | `mysqladmin ping` |
| `cardsdb` | `mysql:lts` | 3309:3306 | None | `mysqladmin ping` |
| `loansdb` | `mysql:lts` | 3308:3306 | None | `mysqladmin ping` |
| `accounts` | `ggoutos/accounts:${IMAGE_TAG}` | 8080 | eurekaserver, accountsdb, configserver (all healthy) | `wget /actuator/health/readiness` |
| `cards` | `ggoutos/cards:${IMAGE_TAG}` | 9000 | eurekaserver, cardsdb, configserver (all healthy) | `wget /actuator/health/readiness` |
| `loans` | `ggoutos/loans:${IMAGE_TAG}` | 8090 | eurekaserver, loansdb, configserver (all healthy) | `wget /actuator/health/readiness` |

### Startup Order
```
rabbit → configserver → eurekaserver → databases → business services → gatewayserver
```

### Resource Limits (All Microservices)
- CPU: 0.50 cores
- Memory: 512M

### Network Configuration
- **Network:** `microservices-network` (bridge driver)
- **DNS Resolution:** Docker embedded DNS (services resolve by container name)

### ⚠️ Critical: No Database Persistence
**There are NO volume mounts for any database services.** All database data is stored in container writable layers and is **lost** when containers are removed (`docker compose down` or `docker compose rm`).

---

## 11. Package Structure Pattern

```
{service}/
├── src/main/java/com/ggoutos/{service}/
│   ├── {Service}Application.java
│   ├── controller/
│   │   └── {Service}Controller.java
│   ├── service/
│   │   ├── I{Service}Service.java
│   │   ├── impl/
│   │   │   └── {Service}ServiceImpl.java
│   │   └── client/ (Feign clients - Accounts only)
│   │       ├── CardsFeignClient.java
│   │       └── LoansFeignClient.java
│   ├── entity/
│   │   ├── BaseEntity.java
│   │   └── {Entity}.java
│   ├── mapper/
│   │   └── {Entity}Mapper.java
│   ├── repository/
│   │   └── {Entity}Repository.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ResourceNotFoundException.java
│   │   └── {Entity}AlreadyExistsException.java
│   ├── audit/
│   │   └── AuditAwareImpl.java
│   └── constants/
│       └── {Service}Constants.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__init_schema.sql
├── src/test/java/
│   └── com/ggoutos/{service}/
│       └── {Service}ApplicationTests.java
├── Dockerfile (Accounts only)
└── pom.xml
```

---

## 12. Testing Strategy & Coverage **(NEW - PR #10)**

### 12.1 Test Structure Pattern

```
{service}/
└── src/test/java/com/ggoutos/{service}/
    ├── {Service}ApplicationTests.java
    │   ├── contextLoads() test
    │   └── testMainMethod() test ✅ Added in PR #10
    ├── controller/
    │   └── {Service}ControllerTest.java ✅ Added in PR #10
    │       ├── @WebMvcTest(Controller.class)
    │       ├── @MockitoBean for service mocking (Spring Boot 4.x pattern)
    │       ├── MockMvc for HTTP request testing
    │       └── @Nested test classes with @DisplayName
    ├── service/impl/
    │   └── {Service}ServiceImplTest.java ✅ Added in PR #10
    │       ├── @ExtendWith(MockitoExtension.class)
    │       ├── @Mock for dependencies
    │       ├── @InjectMocks for service under test
    │       └── AssertJ fluent assertions
    └── entity/
        └── {Entity}Test.java ✅ Added in PR #10
            ├── Getters/setters validation
            ├── equals() and hashCode() tests
            ├── toString() validation
            ├── Entity inheritance tests
            └── Constructor tests
```

### 12.2 Test Coverage by Service (PR #10 Update)

| Service | Test Files | Test Cases | Coverage Level | Status |
|---------|-----------|-----------|----------------|--------|
| **accounts** | 5 | 45+ | ✅ **Comprehensive** | **PR #10 Complete** |
| cards | 1 | 1 | ❌ Minimal (contextLoads only) | Needs Tests |
| loans | 1 | 1 | ❌ Minimal (contextLoads only) | Needs Tests |
| gatewayserver | 1 | 1 | ❌ Minimal (contextLoads only) | Needs Tests |
| configserver | 1 | 1 | ❌ Minimal (contextLoads only) | Needs Tests |
| eurekaserver | 1 | 1 | ❌ Minimal (contextLoads only) | Needs Tests |
| utils | 0 | 0 | ❌ No tests (shared library) | N/A |

### 12.3 Accounts Service Test Coverage (PR #10 Details)

#### 12.3.1 CustomerControllerTest.java (256 lines, 11 tests)

**Testing Pattern**: `@WebMvcTest(CustomerController.class)` with `@MockitoBean`

| Test Case | Status | Description |
|-----------|--------|-------------|
| Should fetch customer details and return 200 | ✅ | Success scenario with full data |
| Should return 400 when mobile number is missing | ✅ | Validation - missing param |
| Should return 400 when mobile number format is invalid | ✅ | Validation - wrong format (< 10 digits) |
| Should return 400 when mobile number contains non-numeric | ✅ | Validation - letters in mobile number |
| Should return 400 when mobile number has more than 10 digits | ✅ | Validation - too long |
| Should return 400 when correlation ID header is missing | ✅ | Validation - missing header |
| Should return 500 when service throws exception | ✅ | Error handling - RuntimeException |
| Should return customer details with only accounts (cards/loans null) | ✅ | Partial data scenario |
| Should handle different correlation IDs | ✅ | Multiple correlation IDs |
| Should return 400 when both mobile number and correlation ID are invalid | ✅ | Multiple validation errors |
| Should verify response content type is application/json | ✅ | Response validation |

**Key Technologies**:
- `@WebMvcTest` for controller testing
- `@MockitoBean` (Spring Boot 4.x replacement for `@MockBean`)
- `MockMvc` for HTTP request/response testing
- `@Nested` and `@DisplayName` for test organization
- JSON path assertions for response validation

#### 12.3.2 CustomersServiceImplTest.java (229 lines, 4 tests)

**Testing Pattern**: `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`

| Test Case | Status | Description |
|-----------|--------|-------------|
| Should return customer details with cards and loans when customer exists | ✅ | Success with Feign client calls |
| Should throw ResourceNotFoundException when customer not found | ✅ | Exception handling - customer missing |
| Should throw ResourceNotFoundException when account not found | ✅ | Exception handling - account missing |
| Should pass correlation ID to Feign client calls | ✅ | Correlation ID propagation verification |

**Key Technologies**:
- `@ExtendWith(MockitoExtension.class)` for Mockito integration
- `@Mock` for repository and Feign client mocking
- `@InjectMocks` for service under test
- AssertJ fluent assertions (`assertThat().isNotNull()`, `.isEqualTo()`, `.isInstanceOf()`)
- Mockito verification (`verify().method()`)

#### 12.3.3 CustomerTest.java (244 lines, 17+ tests)

**Testing Pattern**: Entity unit tests with `@Nested` test classes

| Test Category | Tests | Description |
|---------------|-------|-------------|
| Getters/Setters | 8 | All field accessors validated |
| equals() | 7 | Same object, same ID, null, different type, different ID, null ID, both null |
| hashCode() | 2 | Consistency, same ID |
| toString() | 2 | String representation, field inclusion |
| Inheritance | 2 | Extends BaseEntity, audit fields present |
| Constructors | 1 | No-args constructor creates empty instance |
| ID Generation | 1 | Documents IDENTITY strategy |

#### 12.3.4 AccountsTest.java (169 lines, 13+ tests)

**Testing Pattern**: Entity unit tests with `@Nested` test classes

| Test Category | Tests | Description |
|---------------|-------|-------------|
| equals() | 7 | Same object, same account number, null, different type, different account number, null account number, both null |
| hashCode() | 2 | Consistency, same account number |
| toString() | 2 | String representation, field inclusion |
| Inheritance | 2 | Extends BaseEntity, audit fields present |
| Constructors | 1 | No-args constructor creates empty instance |

#### 12.3.5 AccountsApplicationTests.java (Updated)

**Changes**: Added `testMainMethod()` test case

```java
@Test
void testMainMethod() {
    assertDoesNotThrow(() -> AccountsApplication.main(new String[]{}));
}
```

### 12.4 Testing Patterns Established (PR #10)

#### 12.4.1 Controller Testing Pattern (Spring Boot 4.x)

```java
@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController Tests")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean  // Spring Boot 4.x replacement for @MockBean
    private ICustomersService customersService;

    private CustomerDetailsDto testCustomerDetailsDto;
    private static final String CORRELATION_ID = "test-correlation-id-12345";
    private static final String MOBILE_NUMBER = "9939321212";

    @BeforeEach
    void setUp() {
        // Setup test DTOs
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountNumber(1234567890L);
        accountsDto.setAccountType("Savings");
        accountsDto.setBranchAddress("123 Main Street, New York");

        testCustomerDetailsDto = new CustomerDetailsDto();
        testCustomerDetailsDto.setName("John Doe");
        testCustomerDetailsDto.setEmail("john@example.com");
        testCustomerDetailsDto.setMobileNumber(MOBILE_NUMBER);
        testCustomerDetailsDto.setAccountsDto(accountsDto);
    }

    @Nested
    @DisplayName("fetchCustomerDetails() Tests")
    class FetchCustomerDetailsTests {

        @Test
        @DisplayName("Should fetch customer details and return 200")
        void fetchCustomerDetails_shouldReturn200() throws Exception {
            // Given
            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID))
                    .thenReturn(testCustomerDetailsDto);

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", CORRELATION_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(testCustomerDetailsDto.getName()))
                    .andExpect(jsonPath("$.email").value(testCustomerDetailsDto.getEmail()))
                    .andExpect(jsonPath("$.mobileNumber").value(testCustomerDetailsDto.getMobileNumber()))
                    .andExpect(jsonPath("$.accountsDto.accountNumber")
                        .value(testCustomerDetailsDto.getAccountsDto().getAccountNumber()));

            verify(customersService).fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID);
        }

        @Test
        @DisplayName("Should return 400 when mobile number is missing")
        void fetchCustomerDetails_shouldReturn400_whenMobileNumberMissing() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isBadRequest());

            verify(customersService, never()).fetchCustomerDetails(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 500 when service throws exception")
        void fetchCustomerDetails_shouldReturn500_whenServiceThrowsException() throws Exception {
            // Given
            when(customersService.fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/fetchCustomerDetails")
                            .param("mobileNumber", MOBILE_NUMBER)
                            .header("eazybank-correlation-id", CORRELATION_ID))
                    .andExpect(status().isInternalServerError());

            verify(customersService).fetchCustomerDetails(MOBILE_NUMBER, CORRELATION_ID);
        }
    }
}
```

#### 12.4.2 Service Testing Pattern

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomersServiceImpl Tests")
class CustomersServiceImplTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CardsFeignClient cardsFeignClient;

    @Mock
    private LoansFeignClient loansFeignClient;

    @InjectMocks
    private CustomersServiceImpl customersService;

    @Nested
    @DisplayName("fetchCustomerDetails")
    class FetchCustomerDetailsTests {

        @Test
        @DisplayName("Should return customer details with cards and loans when customer exists")
        void fetchCustomerDetails_shouldReturnCustomerDetails_whenCustomerExists() {
            // Given
            String mobileNumber = "9876543210";
            String correlationId = "test-correlation-id-123";
            Long customerId = 1L;

            Customer customer = new Customer();
            customer.setCustomerId(customerId);
            customer.setName("John Doe");
            customer.setEmail("john.doe@example.com");
            customer.setMobileNumber(mobileNumber);

            Accounts accounts = new Accounts();
            accounts.setCustomerId(customerId);
            accounts.setAccountNumber(1000000001L);
            accounts.setAccountType("Savings");
            accounts.setBranchAddress("123 Main Street, New York");

            CardsDto cardsDto = new CardsDto();
            cardsDto.setMobileNumber(mobileNumber);
            cardsDto.setCardNumber("4111111111111111");
            cardsDto.setCardType("Credit Card");
            cardsDto.setTotalLimit(100000);

            LoansDto loansDto = new LoansDto();
            loansDto.setMobileNumber(mobileNumber);
            loansDto.setLoanNumber("100000000001");
            loansDto.setLoanType("Home Loan");
            loansDto.setTotalLoan(500000);

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.of(customer));
            when(accountsRepository.findByCustomerId(customerId))
                    .thenReturn(Optional.of(accounts));
            when(loansFeignClient.fetchLoanDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(loansDto);
            when(cardsFeignClient.fetchCardDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(cardsDto);

            // When
            CustomerDetailsDto result = customersService.fetchCustomerDetails(mobileNumber, correlationId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("John Doe");
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(result.getMobileNumber()).isEqualTo(mobileNumber);

            assertThat(result.getAccountsDto()).isNotNull();
            assertThat(result.getAccountsDto().getAccountNumber()).isEqualTo(1000000001L);
            assertThat(result.getAccountsDto().getAccountType()).isEqualTo("Savings");

            assertThat(result.getCardsDto()).isNotNull();
            assertThat(result.getCardsDto().getCardNumber()).isEqualTo("4111111111111111");

            assertThat(result.getLoansDto()).isNotNull();
            assertThat(result.getLoansDto().getLoanNumber()).isEqualTo("100000000001");

            verify(customerRepository).findByMobileNumber(mobileNumber);
            verify(accountsRepository).findByCustomerId(customerId);
            verify(loansFeignClient).fetchLoanDetails(correlationId, mobileNumber);
            verify(cardsFeignClient).fetchCardDetails(correlationId, mobileNumber);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when customer not found")
        void fetchCustomerDetails_shouldThrowResourceNotFoundException_whenCustomerNotFound() {
            // Given
            String mobileNumber = "0000000000";
            String correlationId = "test-correlation-id-456";

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> customersService.fetchCustomerDetails(mobileNumber, correlationId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found with the given input data mobileNumber : '0000000000'");

            verify(customerRepository).findByMobileNumber(mobileNumber);
            verify(accountsRepository, never()).findByCustomerId(any());
            verify(loansFeignClient, never()).fetchLoanDetails(any(), any());
            verify(cardsFeignClient, never()).fetchCardDetails(any(), any());
        }

        @Test
        @DisplayName("Should pass correlation ID to Feign client calls")
        void fetchCustomerDetails_shouldPassCorrelationId_toFeignClients() {
            // Given
            String mobileNumber = "9876543210";
            String correlationId = "unique-correlation-id-xyz";
            Long customerId = 1L;

            Customer customer = new Customer();
            customer.setCustomerId(customerId);
            customer.setName("Test User");
            customer.setMobileNumber(mobileNumber);

            Accounts accounts = new Accounts();
            accounts.setCustomerId(customerId);
            accounts.setAccountNumber(1000000002L);

            CardsDto cardsDto = new CardsDto();
            LoansDto loansDto = new LoansDto();

            when(customerRepository.findByMobileNumber(mobileNumber))
                    .thenReturn(Optional.of(customer));
            when(accountsRepository.findByCustomerId(customerId))
                    .thenReturn(Optional.of(accounts));
            when(loansFeignClient.fetchLoanDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(loansDto);
            when(cardsFeignClient.fetchCardDetails(eq(correlationId), eq(mobileNumber)))
                    .thenReturn(cardsDto);

            // When
            customersService.fetchCustomerDetails(mobileNumber, correlationId);

            // Then
            verify(loansFeignClient).fetchLoanDetails(correlationId, mobileNumber);
            verify(cardsFeignClient).fetchCardDetails(correlationId, mobileNumber);
        }
    }
}
```

#### 12.4.3 Entity Testing Pattern

```java
@DisplayName("Customer Entity Tests")
class CustomerTest {

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setCustomerId(1L);
        customer.setName("John Doe");
        customer.setEmail("john.doe@example.com");
        customer.setMobileNumber("9939321212");
        customer.setCreatedAt(LocalDateTime.now());
        customer.setCreatedBy("ACCOUNTS_MS");
    }

    @Nested
    @DisplayName("Equals() Tests")
    class EqualsTests {

        @Test
        @DisplayName("Should return true when comparing same object")
        void equals_sameObject_shouldReturnTrue() {
            assertThat(customer.equals(customer)).isTrue();
        }

        @Test
        @DisplayName("Should return true when comparing objects with same customerId")
        void equals_sameCustomerId_shouldReturnTrue() {
            Customer anotherCustomer = new Customer();
            anotherCustomer.setCustomerId(1L);

            assertThat(customer.equals(anotherCustomer)).isTrue();
        }

        @Test
        @DisplayName("Should return false when comparing with null")
        void equals_null_shouldReturnFalse() {
            assertThat(customer.equals(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false when comparing with different type")
        void equals_differentType_shouldReturnFalse() {
            assertThat(customer.equals("not a customer")).isFalse();
        }

        @Test
        @DisplayName("Should return false when comparing objects with different customerId")
        void equals_differentCustomerId_shouldReturnFalse() {
            Customer anotherCustomer = new Customer();
            anotherCustomer.setCustomerId(999L);

            assertThat(customer.equals(anotherCustomer)).isFalse();
        }

        @Test
        @DisplayName("Should return false when customerId is null")
        void equals_nullCustomerId_shouldReturnFalse() {
            Customer customerWithNullId = new Customer();

            assertThat(customer.equals(customerWithNullId)).isFalse();
        }

        @Test
        @DisplayName("Should return false when both customerIds are null")
        void equals_bothNullCustomerId_shouldReturnFalse() {
            Customer customer1 = new Customer();
            Customer customer2 = new Customer();

            assertThat(customer1.equals(customer2)).isFalse();
        }
    }

    @Nested
    @DisplayName("hashCode() Tests")
    class HashCodeTests {

        @Test
        @DisplayName("Should return consistent hash code")
        void hashCode_shouldBeConsistent() {
            int hashCode1 = customer.hashCode();
            int hashCode2 = customer.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
        }

        @Test
        @DisplayName("Should return same hash code for objects with same customerId")
        void hashCode_sameCustomerId_shouldReturnSameHashCode() {
            Customer anotherCustomer = new Customer();
            anotherCustomer.setCustomerId(1L);

            assertThat(customer.hashCode()).isEqualTo(anotherCustomer.hashCode());
        }
    }

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should return string representation")
        void toString_shouldReturnString() {
            String result = customer.toString();

            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
            assertThat(result).contains("Customer");
        }

        @Test
        @DisplayName("Should include field values in string representation")
        void toString_shouldIncludeFields() {
            String result = customer.toString();

            assertThat(result).contains("John Doe");
            assertThat(result).contains("john.doe@example.com");
            assertThat(result).contains("9939321212");
        }
    }

    @Nested
    @DisplayName("Entity Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should extend BaseEntity")
        void shouldExtendBaseEntity() {
            assertThat(customer).isInstanceOf(BaseEntity.class);
        }

        @Test
        @DisplayName("Should have audit fields from BaseEntity")
        void shouldHaveAuditFields() {
            assertThat(customer.getCreatedAt()).isNotNull();
            assertThat(customer.getCreatedBy()).isEqualTo("ACCOUNTS_MS");
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create empty instance with no-args constructor")
        void noArgsConstructor_shouldCreateEmptyInstance() {
            Customer emptyCustomer = new Customer();

            assertThat(emptyCustomer).isNotNull();
            assertThat(emptyCustomer.getCustomerId()).isNull();
            assertThat(emptyCustomer.getName()).isNull();
            assertThat(emptyCustomer.getEmail()).isNull();
            assertThat(emptyCustomer.getMobileNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("Entity Generation Strategy Tests")
    class GenerationTypeTests {

        @Test
        @DisplayName("CustomerId should be auto-generated by database (IDENTITY strategy)")
        void customerId_shouldUseIdentityStrategy() {
            // This test documents that customerId uses GenerationType.IDENTITY
            // In real DB operations, the database auto-generates the ID
            Customer newCustomer = new Customer();
            newCustomer.setName("Jane Doe");
            newCustomer.setEmail("jane@example.com");
            newCustomer.setMobileNumber("9876543210");

            // Before persistence, ID is null
            assertThat(newCustomer.getCustomerId()).isNull();
        }
    }
}
```

### 12.5 Key Testing Technologies Used

| Technology | Purpose | Usage |
|------------|---------|-------|
| **JUnit 5** | Test framework | `@Test`, `@Nested`, `@DisplayName`, `@BeforeEach`, `@ExtendWith` |
| **Mockito** | Mocking framework | `@Mock`, `@InjectMocks`, `when().thenReturn()`, `verify()`, `never()` |
| **AssertJ** | Fluent assertions | `assertThat().isNotNull()`, `.isEqualTo()`, `.isInstanceOf()`, `.hasMessageContaining()` |
| **Spring MockMvc** | HTTP testing | `mockMvc.perform()`, `andExpect()`, `status()`, `jsonPath()`, `content()` |
| **Spring Boot Test** | Test slicing | `@WebMvcTest`, `@MockitoBean` (Spring Boot 4.x pattern) |

### 12.6 JaCoCo Configuration (PR #10)

**Parent pom.xml** JaCoCo configuration updated:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <execution>
            <!-- Removed <id>prepare-agent</id> - simplified execution config -->
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>  <!-- Changed from 'test' to 'verify' -->
            <goals>
                <goal>report</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum> <!-- 80% minimum line coverage -->
                            </limit>
                            <!-- Removed BRANCH coverage minimum (was 70%) -->
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Changes**:
- Removed `<id>prepare-agent</id>` (simplified execution config)
- Changed report phase from `test` to `verify`
- Removed branch coverage minimum requirement (70%)
- Kept line coverage minimum at 80%

### 12.7 Testing Best Practices Established

1. **Use `@Nested` classes** to organize related tests (e.g., `EqualsTests`, `HashCodeTests`)
2. **Use `@DisplayName`** for clear test descriptions at class and method level
3. **Follow Given-When-Then pattern** for test structure
4. **Use AssertJ fluent assertions** for readable, expressive tests
5. **Mock external dependencies** (repositories, Feign clients) for unit tests
6. **Verify interactions** with `verify()` to ensure proper method calls
7. **Test validation scenarios** (missing params, invalid formats, missing headers)
8. **Test error scenarios** (exceptions, empty results)
9. **Test edge cases** (null values, partial data, different correlation IDs)
10. **Document entity behavior** through tests (ID generation strategy, inheritance)

### 12.8 Remaining Testing Work

| Priority | Task | Estimated Effort | Impact |
|----------|------|------------------|--------|
| **High** | Add tests for Cards service (follow PR #10 pattern) | 2-3 days | Complete business service coverage |
| **High** | Add tests for Loans service (follow PR #10 pattern) | 2-3 days | Complete business service coverage |
| **Medium** | Add repository tests (`@DataJpaTest`) for all services | 1-2 days | Validate data access layer |
| **Medium** | Add gateway filter tests (correlation ID generation/passthrough) | 1 day | Validate tracing logic |
| **Low** | Add integration tests for cross-service communication | 2-3 days | End-to-end workflow validation |
| **Low** | Add test coverage reports to CI/CD pipeline | 1 day | Automated quality gates |

---

## 13. Identified Patterns

### Naming Conventions
- **Packages:** `com.ggoutos.{service}` (lowercase)
- **Interfaces:** `I{Service}Service` (capital I prefix)
- **Implementations:** `{Service}ServiceImpl` in `impl/` subpackage
- **DTOs:** `{Entity}Dto` suffix (in utils module)
- **Mappers:** `{Entity}Mapper` with static methods
- **Constants:** `{Service}Constants` with private constructor
- **Exceptions:** `{Resource}NotFoundException`, `{Entity}AlreadyExistsException`
- **Feign Clients:** `{Service}FeignClient` in `service/client/` subpackage

### Lombok Usage Pattern
```java
// Entities - use @Getter @Setter @ToString @RequiredArgsConstructor
@Entity
@Getter @Setter @ToString @RequiredArgsConstructor
public class Customer extends BaseEntity { ... }

// Services - use @RequiredArgsConstructor with final fields
@RequiredArgsConstructor
@Service
public class AccountsServiceImpl implements IAccountsService {
    private final AccountsRepository accountsRepository;
    private final AccountsMapper accountsMapper;
}

// DTOs - use @Data
@Data
public class CustomerDto { ... }
```

### Mapper Pattern (Manual, Target-Mutation)
```java
public class {Entity}Mapper {
    public static {Entity}Dto mapTo{Entity}Dto({Entity} entity, {Entity}Dto dto) {
        dto.setField1(entity.getField1());
        // ... set other fields
        return dto;
    }
    
    public static {Entity} mapTo{Entity}({Entity}Dto dto, {Entity} entity) {
        entity.setField1(dto.getField1());
        // ... set other fields
        return entity;
    }
}
```

### Exception Handling Pattern
```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    // Validation errors → Map<String, String>, HTTP 400
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(...)
    
    // Custom exceptions → ErrorResponseDto
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFound(...)  // HTTP 404
    
    // Generic exceptions → ErrorResponseDto, HTTP 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(...)
}
```

### Distributed Tracing Pattern
```
Client → Gateway (generates correlation ID) → Business Service
         ↑ eazybank-correlation-id header    ↑ Required on /api/fetch
```

---

## 14. Critical Issues Found

### Security Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| SEC-001 | `.env.prod` contains plaintext credentials and GitHub PAT token | Security risk | **Critical** | Open |
| SEC-002 | No authentication/authorization on gateway or business services | Unauthorized access | **Critical** | Open |

### Bug Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| BUG-001 | CardsDto validates 12-digit card numbers, but service generates 16-digit numbers | Validation failure on update | **High** | Open |
| BUG-002 | No database volume mounts in Docker Compose - data lost on container removal | Data persistence | **High** | Open |

### Test Coverage Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| TEST-001 | Only contextLoads tests exist across all services | No business logic coverage | **High** | **Partially Resolved (PR #10)** |

**PR #10 Progress**:
- ✅ Accounts service now has comprehensive test coverage (45+ tests)
- ✅ CustomerController: 11 test cases (validation, error handling, success scenarios)
- ✅ CustomersServiceImpl: 4 test cases (business logic, Feign client verification)
- ✅ Customer entity: 17+ test cases (getters/setters, equals, hashCode, toString, inheritance)
- ✅ Accounts entity: 13+ test cases (equals, hashCode, toString, inheritance)
- ❌ Cards service still lacks comprehensive tests
- ❌ Loans service still lacks comprehensive tests
- ❌ Gateway server lacks filter tests

**Remaining Work**:
- Add similar test coverage to cards and loans services
- Add repository tests (`@DataJpaTest`) for all services
- Add gateway filter tests for correlation ID generation/passthrough
- Add integration tests for cross-service communication

### Reliability Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| REL-001 | No circuit breaker on Feign clients (Accounts → Cards/Loans) | Cascading failures | Medium | Open |
| REL-002 | Random ID generators use `java.util.Random` (not `SecureRandom`, no collision check) | ID collisions at scale | Low | Open |

---

## 15. Code Quality Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| CQ-001 | `main` methods are package-private across all services (non-standard) | Inconsistency | Low | Open |
| CQ-002 | Cards/Loans services have `@EnableFeignClients` but no Feign clients defined | Dead configuration | Low | Open |
| CQ-003 | `CardsConstants.CREDIT` and `DEBIT` defined but unused | Dead code | Low | Open |
| CQ-004 | Gateway response header typo: `X-Respose-Time` should be `X-Response-Time` | API correctness | Low | Open |
| CQ-005 | Loans `updateLoan()`/`deleteLoan()` always return `true` (417 path unreachable) | Dead code path | Low | Open |
| CQ-006 | Inconsistent filter definition pattern in gateway (`@Component` vs `@Configuration`+`@Bean`) | Style inconsistency | Low | Open |
| CQ-007 | ConfigServer native profile is non-functional (classpath directories don't exist) | Broken fallback | Medium | Open |
| ~~CQ-008~~ | ~~No unit tests for any service~~ | ~~No validation of business logic~~ | ~~High~~ | **✅ Resolved (PR #10)** |

**PR #10 Resolutions**:
- ✅ Added comprehensive unit tests for Accounts service (45+ test cases)
- ✅ Added main method validation test for AccountsApplication
- ✅ Established testing patterns and best practices for future PRs
- ✅ Fixed code style issues in Accounts.java (`@Column` annotation formatting, extra space in `extends BaseEntity`)
- ✅ Fixed indentation issue in CustomersServiceImpl.java (class declaration)

---

## 16. Security Concerns

### 16.1 No Authentication on Gateway or Business Services
**Risk**: CRITICAL

The gateway operates as an **unauthenticated reverse proxy**. Any client that can reach port 8072 can route requests to any backend microservice. Business services are also directly accessible on their individual ports.

**Recommendation:** Implement JWT authentication at the gateway level with token validation and passthrough.

---

### 16.2 Shutdown Endpoints Unrestricted
**Risk**: MEDIUM

All services expose `/actuator/shutdown` with `access: unrestricted`. Anyone can shut down any service without authentication.

**Recommendation:** Either remove the shutdown endpoint or protect it with authentication.

---

### 16.3 Weak Encryption Key
**Risk**: MEDIUM

`ENCRYPTION_KEY=very_secret_key` in `.env.prod` is not suitable for production use.

**Recommendation:** Use a strong, randomly generated 256-bit key.

---

### 16.4 CSRF Disabled in ConfigServer
**Risk**: LOW (internal service)

```java
http.csrf(AbstractHttpConfigurer::disable)
```

**Recommendation:** Document this as acceptable for internal stateless APIs.


---

## 17. AI Assistant Configurations

### 17.1 `.ai/` Directory (Local AI Stack)
- **Purpose:** Docker-based local AI coding assistant
- **Components:**
  - Ollama (port 11434): LLM runtime with GPU support
  - Aider: AI coding agent
  - Continue.dev: IntelliJ plugin config
- **Models:**
  - Agent: `qwen2.5-coder:7b-instruct-q4_K_M`
  - Autocomplete: `qwen2.5-coder:3b`
  - Reasoning: `qwen2.5:7b-instruct`

### 17.2 `.aiassistant/rules/AGENTS.md`
- **Purpose:** JetBrains AI Assistant rules file
- **Content:** Points to root `/AGENTS.md`

### 17.3 Root `AGENTS.md`
- **Purpose:** GitHub Copilot + general AI agent guidance
- **Content:** Comprehensive project documentation and patterns

---

## 18. Summary Statistics

### Code Metrics
- **Total Maven Modules:** 7 (utils, eurekaserver, configserver, gatewayserver, accounts, cards, loans)
- **Total Java Source Files:** ~90 files (including tests)
- **Total Lines of Code:** ~8,500+ lines (excluding tests)
- **Total Test Lines:** ~900+ lines (PR #10 contribution)
- **Total Test Files:** 9 files (accounts service: 5, other services: 4 contextLoads tests)
- **Total Test Cases:** 50+ test cases (accounts service: 45+, other services: 5 contextLoads)

### Test Coverage by Service

| Service | Test Files | Test Cases | Coverage Level | Lines of Test Code |
|---------|-----------|-----------|----------------|-------------------|
| **accounts** | 5 | 45+ | ✅ **Comprehensive** | ~900 lines |
| cards | 1 | 1 | ❌ Minimal (contextLoads only) | ~10 lines |
| loans | 1 | 1 | ❌ Minimal (contextLoads only) | ~10 lines |
| gatewayserver | 1 | 1 | ❌ Minimal (contextLoads only) | ~10 lines |
| configserver | 1 | 1 | ❌ Minimal (contextLoads only) | ~10 lines |
| eurekaserver | 1 | 1 | ❌ Minimal (contextLoads only) | ~10 lines |
| utils | 0 | 0 | ❌ No tests (shared library) | 0 lines |

### Technology Stack
- **Spring Boot:** 4.0.5
- **Spring Cloud:** 2025.1.1
- **Java:** 25
- **Database:** MySQL (all services)
- **Message Broker:** RabbitMQ 4.2.5
- **API Documentation:** OpenAPI 3.0 (SpringDoc 3.0.2)
- **Testing Frameworks:** JUnit 5, Mockito, AssertJ, Spring MockMvc
- **Coverage Tool:** JaCoCo 0.8.14
- **Containerization:** Jib, Buildpacks, Docker Compose

### API Endpoints
- **Total REST Endpoints:** 20+ (across accounts, cards, loans)
- **Swagger-Documented Endpoints:** All business services
- **Gateway Routes:** 3 (accounts, loans, cards)
- **Actuator Endpoints:** 6+ per service (health, info, refresh, busrefresh, shutdown, gateway)

---

## 19. Recommendations

### Immediate Priorities (Next Sprint)

1. **Add Test Coverage to Cards and Loans Services** (Follow PR #10 pattern)
  - Controller tests for CardsController and LoansController
  - Service tests for CardsServiceImpl and LoansServiceImpl
  - Entity tests for Cards and Loans entities
  - Target: 80% line coverage for business logic
  - **Estimated Effort**: 4-6 days

2. **Fix Card Number Validation Mismatch** (BUG-001)
  - Update DTO validation from 12 to 16 digits
  - Add validation tests
  - **Estimated Effort**: 1 day

3. **Add Database Volume Mounts** (BUG-002)
  - Persist data across container restarts
  - Add integration tests for database operations
  - **Estimated Effort**: 1 day

### Short-Term Improvements (1-2 Weeks)

4. **Add Repository Tests** (`@DataJpaTest`)
  - Test custom query methods
  - Test entity relationships
  - Test Flyway migrations
  - **Estimated Effort**: 2-3 days

5. **Add Gateway Filter Tests**
  - Test correlation ID generation
  - Test correlation ID passthrough
  - Test route rewriting
  - **Estimated Effort**: 1 day

6. **Add Integration Tests**
  - Test cross-service communication (Feign clients)
  - Test end-to-end workflows
  - Test error scenarios
  - **Estimated Effort**: 3-4 days

### Medium-Term Enhancements (1 Month)

7. **Implement JWT Authentication** at gateway level
8. **Add API Rate Limiting**
9. **Configure Centralized Logging** (ELK stack)
10. **Implement Distributed Tracing** (Sleuth/Zipkin or Micrometer Tracing)
11. **Add Circuit Breaker Pattern** (Resilience4j) for Feign clients

### Long-Term Vision (Quarter)

12. **Move Secrets to External Secrets Manager**
13. **Add Comprehensive CI/CD Pipeline** with test coverage gates
14. **Implement Blue-Green Deployment** strategy
15. **Add Performance Testing** suite
15. **Add Performance Testing** suite
16. **Consider GraphQL API** layer for complex queries

---

*Last Updated: 2026-04-06*  
*Version: 3.1 (PR #10 incorporated)*
