# Comprehensive Analysis: EazyBank Microservices Project

> **Project Analysis Report** - Current state, architecture, issues, and recommendations for the EazyBank microservices platform.

**Last Updated**: 2026-04-03
**Project Version**: 0.0.1-SNAPSHOT
**Status**: Active Development

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
12. [Identified Patterns](#12-identified-patterns)
13. [Critical Issues Found](#13-critical-issues-found)
14. [Code Quality Issues](#14-code-quality-issues)
15. [Security Concerns](#15-security-concerns)
16. [AI Assistant Configurations](#16-ai-assistant-configurations)
17. [Summary Statistics](#17-summary-statistics)
18. [Recommendations](#18-recommendations)

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

### Current State Assessment
- ✅ **Strengths**: Modern tech stack (Spring Boot 4.0.5, Java 25), clean separation of concerns, comprehensive Docker orchestration, Flyway schema management, API gateway with tracing
- ⚠️ **Issues**: Card number validation mismatch (12 vs 16 digits), no database persistence in Docker, exposed credentials in `.env.prod`, minimal test coverage
- 🔧 **Resolved**: Duplicate test files removed, LoansController standardized to `@RequiredArgsConstructor`, `@Table` annotation added to Loans entity, copy-paste errors fixed

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

## 12. Identified Patterns

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

## 13. Critical Issues Found

### 13.1 Card Number Validation Mismatch
**Severity**: HIGH

**Issue:**
- `CardsDto.cardNumber` has `@Pattern(regexp = "(^$|[0-9]{12})")` - expects 12 digits
- `CardsServiceImpl.generateCardNumber()` generates 16-digit numbers: `1000000000000000L + random.nextLong(9000000000000000L)`
- This means any card created by the service will have a card number that **fails validation** when sent back in an update request

**Action Required:** Either change DTO validation to `{16}` or change generation to produce 12-digit numbers

---

### 13.2 No Database Persistence in Docker
**Severity**: HIGH

**Issue:**
- No volume mounts defined for any of the three MySQL database services
- All data is stored in container writable layers
- Running `docker compose down` destroys all database contents

**Action Required:** Add named volumes to docker-compose.yml:
```yaml
volumes:
  accounts-data:
  cards-data:
  loans-data:
```
And mount them:
```yaml
accountsdb:
  volumes:
    - accounts-data:/var/lib/mysql
```

---

### 13.3 Exposed Credentials in `.env.prod`
**Severity**: CRITICAL

**Issue:**
The `.docker/.env.prod` file contains plaintext secrets:
- `MYSQL_ROOT_PASSWORD=root`
- `CONFIG_SERVER_PASSWORD='config123!@#'`
- `GIT_TOKEN=github_pat_11ACG2LGA00ggt7Qacsn9k_...` (real GitHub PAT)
- `ENCRYPTION_KEY=very_secret_key`

**Action Required:** Move secrets to `.env.local` (git-ignored) or use Docker secrets / external secrets manager

---

## 14. Code Quality Issues

### 14.1 Package-Private `main` Methods
**All services** declare `main` as `static void main(String[] args)` instead of `public static void main(String[] args)`. This works but is non-standard.

---

### 14.2 Dead Feign Configuration
Cards and Loans services have `@EnableFeignClients` annotation and `spring-cloud-starter-openfeign` dependency, but **no Feign client interfaces are defined**. This is dead configuration.

---

### 14.3 Unused Constants
`CardsConstants.CREDIT` and `CardsConstants.DEBIT` are defined but never referenced in any code.

---

### 14.4 Gateway Header Typo
Response header `"X-Respose-Time"` should be `"X-Response-Time"` (missing 'n').

---

### 14.5 Unreachable Code Paths in Loans Service
`LoansServiceImpl.updateLoan()` and `deleteLoan()` always return `true`. The 417 (Expectation Failed) branches in `LoansController` are unreachable.

---

### 14.6 Inconsistent Filter Definition in Gateway
- `RequestTraceFilter` uses `@Component` implementing `GlobalFilter`
- `ResponseTraceFilter` uses `@Configuration` class with `@Bean` returning `GlobalFilter`

Both patterns are functionally equivalent but stylistically inconsistent.

---

### 14.7 ConfigServer Native Profile Non-Functional
The native profile's `search-locations` (`classpath:/shared`, `classpath:/config`) point to directories that **do not exist** in the configserver module. If activated, ConfigServer will serve no configuration.

---

### 14.8 Random ID Generation Issues
- **Accounts:** Uses `java.util.Random` (not `SecureRandom`), no collision check
- **Cards:** Uses `java.util.Random`, creates new instance per call (not thread-safe singleton), no collision check
- **Loans:** Uses `new Random().nextInt(900000000)` - narrow range (100000000000 to 100899999999), no collision check

---

## 15. Security Concerns

### 15.1 No Authentication on Gateway or Business Services
**Risk**: CRITICAL

The gateway operates as an **unauthenticated reverse proxy**. Any client that can reach port 8072 can route requests to any backend microservice. Business services are also directly accessible on their individual ports.

**Recommendation:** Implement JWT authentication at the gateway level with token validation and passthrough.

---

### 15.2 Shutdown Endpoints Unrestricted
**Risk**: MEDIUM

All services expose `/actuator/shutdown` with `access: unrestricted`. Anyone can shut down any service without authentication.

**Recommendation:** Either remove the shutdown endpoint or protect it with authentication.

---

### 15.3 Weak Encryption Key
**Risk**: MEDIUM

`ENCRYPTION_KEY=very_secret_key` in `.env.prod` is not suitable for production use.

**Recommendation:** Use a strong, randomly generated 256-bit key.

---

### 15.4 CSRF Disabled in ConfigServer
**Risk**: LOW (internal service)

```java
http.csrf(AbstractHttpConfigurer::disable)
```

**Recommendation:** Document this as acceptable for internal stateless APIs.

---

## 16. AI Assistant Configurations

### 16.1 `.ai/` Directory (Local AI Stack)
- **Purpose:** Docker-based local AI coding assistant
- **Components:**
  - Ollama (port 11434): LLM runtime with GPU support
  - Aider: AI coding agent
  - Continue.dev: IntelliJ plugin config
- **Models:**
  - Agent: `qwen2.5-coder:7b-instruct-q4_K_M`
  - Autocomplete: `qwen2.5-coder:3b`
  - Reasoning: `qwen2.5:7b-instruct`

### 16.2 `.aiassistant/rules/AGENTS.md`
- **Purpose:** JetBrains AI Assistant rules file
- **Content:** Points to root `/AGENTS.md`

### 16.3 Root `AGENTS.md`
- **Purpose:** GitHub Copilot + general AI agent guidance
- **Content:** Comprehensive project documentation and patterns

---

## 17. Summary Statistics

| Metric | Value |
|--------|-------|
| **Total Maven Modules** | 7 (utils, eurekaserver, gatewayserver, configserver, accounts, cards, loans) |
| **Business Services** | 3 (accounts, cards, loans) |
| **Infrastructure Services** | 3 (eurekaserver, configserver, gatewayserver) |
| **Shared Libraries** | 1 (utils) |
| **Database Tables** | 4 (customer, accounts, cards, loans) |
| **API Endpoints per Business Service** | 4 (create, fetch, update, delete) |
| **Gateway Routes** | 3 (accounts, cards, loans) |
| **Docker Compose Services** | 10 (3 DBs + 3 infra + 3 business + 1 RabbitMQ) |
| **Configuration Files** | 6 (2 per business service: default + prod) |
| **Flyway Migrations** | 3 (V1__init_schema.sql per business service) |
| **Test Coverage** | Minimal (contextLoads tests only) |
| **Lines of Code (Est.)** | ~4,000-5,000 (excluding generated code) |

---

## 18. Recommendations

### Immediate Actions (Critical/High Priority)
1. 🔴 **Move secrets out of `.env.prod`** - Use `.env.local` (git-ignored) or external secrets manager
2. 🔴 **Fix card number validation mismatch** - Align DTO validation (`@Pattern`) with actual generated card numbers (16 digits)
3. 🔴 **Add database volume mounts** - Prevent data loss on container removal
4. 🟡 **Add authentication to gateway** - JWT validation at the edge

### Short-Term Improvements (Medium Priority)
5. **Add comprehensive test coverage** - Target 80% for services, 70% for controllers
6. **Secure or remove shutdown endpoints** - Prevent unauthorized service termination
7. **Strengthen encryption key** - Use a proper 256-bit random key
8. **Add circuit breaker to Feign clients** - Resilience4j for fault tolerance
9. **Fix ConfigServer native profile** - Create classpath directories or remove native profile
10. **Standardize filter definitions in gateway** - Use consistent pattern for all filters

### Long-Term Enhancements (Low Priority)
11. **Implement distributed tracing** - Micrometer Tracing + Zipkin/Jaeger for full trace aggregation
12. **Add API rate limiting** - Protect against abuse at gateway level
13. **Add centralized logging** - ELK stack or similar for log aggregation
14. **Standardize `main` method visibility** - Make all `main` methods `public static void`
15. **Remove dead Feign configuration** - Either add Feign clients to Cards/Loans or remove `@EnableFeignClients`
16. **Use `SecureRandom` for ID generation** - Cryptographically secure random number generation
17. **Add gateway ConfigServer integration** - Centralize gateway configuration

---

**Document End**
