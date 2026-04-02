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
| **accounts** | 8080 | Customer accounts management |
| **cards** | 9000 | Credit/debit cards management |
| **loans** | 8090 | Loan management |
| **rabbitmq** | 5672, 15672 | Message broker (config refresh) |
| **accountsdb** | 3307 | MySQL database (accounts) |
| **cardsdb** | 3309 | MySQL database (cards) |
| **loansdb** | 3308 | MySQL database (loans) |

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

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/create?mobileNumber={}` | Create resource |
| GET | `/api/fetch?mobileNumber={}` | Fetch by mobile number |
| PUT | `/api/update` | Update resource (body) |
| DELETE | `/api/delete?mobileNumber={}` | Delete by mobile number |

### URLs

| Service | Swagger UI | H2 Console | Actuator Health | Eureka Registration |
|---------|------------|------------|-----------------|---------------------|
| accounts | http://localhost:8080/swagger-ui.html | N/A | http://localhost:8080/actuator/health | ✅ (client) |
| cards | http://localhost:9000/swagger-ui.html | N/A | http://localhost:9000/actuator/health | ✅ (client) |
| loans | http://localhost:8090/swagger-ui.html | N/A | http://localhost:8090/actuator/health | ✅ (client) |
| configserver | N/A | N/A | http://localhost:8071/actuator/health | ❌ (standalone) |
| eurekaserver | http://localhost:8070 | N/A | http://localhost:8070/actuator/health | ✅ (self) |

**Note:** All services now use MySQL databases (H2 deprecated). Feign clients enable inter-service communication from Accounts to Cards/Loans services.

---

## 2. Architecture Overview

### System Context

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     EazyBank Microservices Platform                       │
│                                                                           │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐             │
│  │   Accounts   │     │    Cards     │     │    Loans     │             │
│  │   :8080      │     │    :9000     │     │    :8090     │             │
│  │   (MySQL)    │     │   (MySQL)    │     │   (MySQL)    │             │
│  │   [Feign]    │────▶│   [Client]   │     │   [Client]   │             │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘             │
│         │                    │                    │                      │
│         └────────────────────┼────────────────────┘                      │
│                              │                                           │
│                     ┌────────▼────────┐         ┌──────────────┐        │
│                     │  ConfigServer   │         │  Eureka      │        │
│                     │     :8071       │         │  Server      │        │
│                     │   (Git/Native)  │         │  :8070       │        │
│                     └────────┬────────┘         └──────────────┘        │
│                              │                                           │
│                     ┌────────▼────────┐                                 │
│                     │    RabbitMQ     │                                 │
│                     │   :5672/:15672  │                                 │
│                     └─────────────────┘                                 │
└──────────────────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

- **Microservices Pattern**: Each service independently deployable with dedicated database
- **Service Discovery**: Eureka Server for service registration and discovery
- **Centralized Configuration**: Spring Cloud Config Server with Git backend (prod) / Native fallback (dev)
- **Event-Driven Config Refresh**: RabbitMQ message bus for distributed configuration updates
- **Database-per-Service**: MySQL for all services (separate ports: 3307, 3308, 3309)
- **API-First Design**: OpenAPI/Swagger documentation on all business services
- **Interface-First Services**: Service layer exposes interfaces, implementations in `impl/` subpackage
- **Declarative REST Clients**: Feign clients for inter-service communication (Accounts → Cards/Loans)

### Cross-Service Communication

**Current State**: Accounts service has Feign clients to communicate with Cards and Loans services.

```java
// Feign Client example
@FeignClient(name = "cards")
public interface CardsFeignClient {
    @GetMapping("/api/fetch?mobileNumber={mobileNumber}")
    CardsDto fetchCardDetails(@PathVariable("mobileNumber") String mobileNumber);
}

@FeignClient(name = "loans")
public interface LoansFeignClient {
    @GetMapping("/api/fetch?mobileNumber={mobileNumber}")
    LoansDto fetchLoanDetails(@PathVariable("mobileNumber") String mobileNumber);
}
```

**Usage in Service Layer**:
```java
@RequiredArgsConstructor
@Service
public class AccountsServiceImpl implements IAccountsService {
    private final CardsFeignClient cardsFeignClient;
    private final LoansFeignClient loansFeignClient;
    
    // Use Feign clients for inter-service calls
    public CustomerDto getCompleteCustomerData(String mobileNumber) {
        CardsDto cards = cardsFeignClient.fetchCardDetails(mobileNumber);
        LoansDto loans = loansFeignClient.fetchLoanDetails(mobileNumber);
        // ... aggregate data
    }
}
```

---

## 3. Technology Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 25 | Runtime & compilation (module system enabled) |
| **Maven** | 3.9+ | Build automation (multi-module project) |
| **Spring Boot** | 4.0.5 | Application framework |
| **Spring Cloud** | 2025.1.1 | Microservices patterns (Config, Bus) |
| **Spring Data JPA** | Included | Data persistence with Hibernate |
| **Flyway** | Included | Database migration tool |

### Dependencies (Managed in Parent POM)

```xml
<!-- Core Starters -->
spring-boot-starter-webmvc          # REST APIs
spring-boot-starter-validation      # Jakarta Bean Validation
spring-boot-starter-actuator        # Health checks, metrics, monitoring
spring-boot-starter-data-jpa        # Data persistence with Hibernate

<!-- Spring Cloud -->
spring-cloud-starter-config         # Centralized configuration
spring-cloud-starter-bus-amqp       # Config refresh via RabbitMQ
spring-cloud-starter-netflix-eureka-client  # Service discovery
spring-cloud-starter-openfeign      # Declarative REST clients

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

<!-- Docker Plugins -->
jib-maven-plugin                    # v3.5.1 - Container image building
spring-boot-maven-plugin            # Buildpacks image creation
native-maven-plugin                 # GraalVM native compilation
```

### Why These Technologies

- **Spring Boot 4.0.5**: Latest stable with Jakarta EE 10 support, improved performance
- **Spring Cloud 2025.1.1**: Compatible with Boot 4.0.5, provides Config Server, Bus, Eureka, and Feign patterns
- **Java 25**: Latest LTS with enhanced pattern matching, records, and virtual threads support
- **Flyway**: Schema version control and migration management for production databases
- **Eureka**: Service discovery and registration for dynamic microservice environments
- **Feign**: Declarative REST clients for simplified inter-service communication
- **Jib**: Fast, reproducible Docker builds without Docker daemon dependency
- **GraalVM Native**: Sub-second startup, reduced memory footprint for production

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
- Self-preservation mode for production resilience
- Health checks with readiness/liveness probes
- Actuator endpoints: `health`, `info`

---

### 4.2 Utils Module

**Purpose**: Shared DTOs and utility classes used across all microservices.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.utils` |
| Type | Shared library (JAR) |
| Dependencies | None (pure data classes) |

**Shared DTOs**:
- `CustomerDto.java` - Customer data transfer object
- `AccountsDto.java` - Account data transfer object
- `CardsDto.java` - Card data transfer object
- `LoansDto.java` - Loan data transfer object
- `ResponseDto.java` - Standard success response
- `ErrorResponseDto.java` - Standard error response

---

### 4.3 ConfigServer

**Purpose**: Centralized configuration management with dynamic refresh capability.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.configserver` |
| Port | 8071 |
| Database | None (stateless) |
| Docker Image | `ggoutos/configserver:jib` |

**Key Classes**:
- `ConfigserverApplication.java` - Entry point with `@EnableConfigServer`
- `SecurityConfig.java` - Basic auth configuration

**Configuration Backends**:
- **Git (production)**: Remote Git repository with `/.config` search path
- **Native (dev fallback)**: `classpath:/shared`, `classpath:/config`

**Security**:
- Basic authentication (username/password via env vars)
- `/actuator/health/**` publicly accessible
- All other endpoints protected

---

### 4.4 Accounts Service

**Purpose**: Customer accounts and relationship management.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.accounts` |
| Port | 8080 |
| Database | MySQL (dev: `localhost:3306`, prod: `localhost:3307`) |
| Docker Image | `ggoutos/accounts:jib` |
| Custom Dockerfile | Yes (multi-stage JVM + Native) |
| Feign Clients | Cards, Loans |

**Entities**:
- `Customer` - Customer information (PK: `customer_id`)
- `Accounts` - Account details (PK: `account_number`, FK: `customer_id`)

**Relationship**: One-to-one (Customer → Accounts via `customer_id`)

**Key Classes**:
- `AccountsApplication.java` - Entry point with `@EnableJpaAuditing`, `@EnableFeignClients`
- `AccountsController.java` - REST endpoints for accounts
- `CustomerController.java` - REST endpoints for customers
- `IAccountsService.java` / `AccountsServiceImpl.java` - Service layer
- `ICustomersService.java` / `CustomersServiceImpl.java` - Customer service layer
- `AccountsMapper.java` / `CustomerMapper.java` - Static entity/DTO mapping
- `AccountsRepository.java` / `CustomerRepository.java` - Data access
- `AuditAwareImpl.java` - Auditor provider (`"ACCOUNTS_MS"`)
- `AccountsConstants.java` - HTTP status codes and business constants
- `CardsFeignClient.java` / `LoansFeignClient.java` - Inter-service clients

---

### 4.5 Cards Service

**Purpose**: Credit/debit card management and limits tracking.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.cards` |
| Port | 9000 |
| Database | MySQL (dev: `localhost:3306`, prod: `localhost:3309`) |
| Docker Image | `ggoutos/cards:jib` |

**Entities**:
- `Cards` - Card details and limits (PK: `card_id`)

**Key Classes**:
- `CardsApplication.java` - Entry point with `@EnableJpaAuditing`, `@EnableFeignClients`
- `CardsController.java` - REST endpoints
- `ICardsService.java` / `CardsServiceImpl.java` - Service layer
- `CardsMapper.java` - Static entity/DTO mapping
- `CardsRepository.java` - Data access
- `AuditAwareImpl.java` - Auditor provider (`"CARDS_MS"`)
- `CardsConstants.java` - HTTP status codes and business constants

---

### 4.6 Loans Service

**Purpose**: Loan management and payment tracking.

| Property | Value |
|----------|-------|
| Package | `com.ggoutos.loans` |
| Port | 8090 |
| Database | MySQL (dev: `localhost:3306`, prod: `localhost:3308`) |
| Docker Image | `ggoutos/loans:jib` |

**Entities**:
- `Loans` - Loan details and balances (PK: `loan_id`)

**Key Classes**:
- `LoansApplication.java` - Entry point with `@EnableJpaAuditing`, `@EnableFeignClients`
- `LoansController.java` - REST endpoints
- `ILoansService.java` / `LoansServiceImpl.java` - Service layer
- `LoansMapper.java` - Static entity/DTO mapping
- `LoansRepository.java` - Data access
- `AuditAwareImpl.java` - Auditor provider (`"LOANS_MS"`)
- `LoansConstants.java` - HTTP status codes and business constants

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

**Lombok Usage**:
```java
// Service classes - use @RequiredArgsConstructor with final fields
@RequiredArgsConstructor
@Service
public class AccountsServiceImpl implements IAccountsService {
    private final AccountsRepository accountsRepository;
    private final AccountsMapper accountsMapper;
}

// DTOs - use @Data or @Builder
@Data
public class CustomerDto { ... }

// Entities - use @Data or explicit getters/setters
@Entity
@Getter @Setter @ToString @RequiredArgsConstructor
public class Customer extends BaseEntity { ... }
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
```

### 6.2 Docker Image Building

All services support **four** build methods:

#### Method 1: Jib (Recommended)
```bash
cd {service}
mvn compile jib:dockerBuild
# Creates: ggoutos/{service}:jib
```

**Advantages**: Fast, reproducible, no Docker daemon required, layered builds

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

**Advantages**: Full control over image layers, optimized for production

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
```

### 6.4 Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `CONFIGSERVER_IMAGE` | `ggoutos/configserver:jib` | ConfigServer Docker image |
| `ACCOUNTS_IMAGE` | `ggoutos/accounts:jib` | Accounts service image |
| `CARDS_IMAGE` | `ggoutos/cards:jib` | Cards service image |
| `LOANS_IMAGE` | `ggoutos/loans:jib` | Loans service image |
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile |
| `RABBITMQ_HOST` | `rabbit` | RabbitMQ hostname |
| `CONFIG_SERVER_HOST` | `http://configserver:8071` | ConfigServer URL |
| `CONFIG_SERVER_USER` | *(required)* | ConfigServer basic auth username |
| `CONFIG_SERVER_PASSWORD` | *(required)* | ConfigServer basic auth password |
| `ENCRYPTION_KEY` | *(required for encryption)* | Symmetric encryption key |

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

**Location**: `.config/` directory (served by ConfigServer native profile)

```
.config/
├── accounts.yml          # Accounts dev configuration
├── accounts-prod.yml     # Accounts production configuration
├── cards.yml             # Cards dev configuration
├── cards-prod.yml        # Cards production configuration
├── loans.yml             # Loans dev configuration
└── loans-prod.yml        # Loans production configuration
```

### 7.3 Config Refresh Mechanism

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

**Enable Bus Refresh**:
```yaml
# In application.yml
spring:
  cloud:
    bus:
      refresh:
        enabled: true
```

### 7.4 Encryption/Decryption

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

### 7.5 Profile Management

**Available Profiles**:
- `default` - Development configuration (H2 for cards/loans, MySQL localhost for accounts)
- `prod` - Production configuration (MySQL on separate ports for all services)

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
           │
           ▼
┌─────────────────────┐
│     ACCOUNTS        │
├─────────────────────┤
│ PK account_number   │
│ FK customer_id      │
│    account_type     │
│    branch_address   │
│    + audit fields   │
└─────────────────────┘

┌─────────────────────┐
│      CARDS          │
├─────────────────────┤
│ PK card_id          │
│    mobile_number    │◀─── Logical join key
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
│    mobile_number    │◀─── Logical join key
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

**Auditor Provider**:
```java
@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of("{SERVICE}_MS"); // e.g., "ACCOUNTS_MS"
    }
}
```

### 8.3 Database Migration

**Current State**: Schema auto-created via JPA `ddl-auto: update`

**For Production Migrations**:
1. Use Flyway or Liquibase (not currently configured)
2. Add migration scripts to `src/main/resources/db/migration`
3. Configure in `application.yml`:
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## 9. API Reference

### 9.1 Common Endpoint Patterns

All business services follow this pattern:

| Method | Endpoint | Request | Response | Status Codes |
|--------|----------|---------|----------|--------------|
| POST | `/api/create` | DTO or `mobileNumber` param | `ResponseDto` | 201 (Created), 417 (Failed) |
| GET | `/api/fetch` | `?mobileNumber={}` | Resource DTO | 200 (OK), 404 (Not Found) |
| PUT | `/api/update` | DTO | `ResponseDto` | 200 (OK), 417 (Failed) |
| DELETE | `/api/delete` | `?mobileNumber={}` | `ResponseDto` | 200 (OK), 404 (Not Found) |

### 9.2 Request/Response Examples

**Create Account**:
```http
POST /api/create?mobileNumber=9939321212
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "accountType": "Savings",
  "branchAddress": "123 Main St"
}
```

**Response**:
```json
{
  "statusCode": "201",
  "statusMsg": "Account created successfully"
}
```

**Fetch Cards**:
```http
GET /api/fetch?mobileNumber=9939321212
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

### 9.3 Validation Rules

| Field | Validation Pattern |
|-------|-------------------|
| Mobile Number | `(^$|[0-9]{10})` - Exactly 10 digits |
| Account Number | `(^$|[0-9]{10})` - Exactly 10 digits |
| Card Number | `(^$|[0-9]{12})` - Exactly 12 digits |
| Loan Number | `(^$|[0-9]{12})` - Exactly 12 digits |
| Email | Valid email format |
| Name | 5-30 characters |

### 9.4 Error Response Format

```json
{
  "apiPath": "/api/create",
  "errorCode": "BAD_REQUEST",
  "errorMessage": "Validation failed",
  "errorTime": "2026-04-01T10:30:00.000"
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
├── ResourceNotFoundException
├── CustomerAlreadyExistsException
├── AccountAlreadyExistsException
├── CardAlreadyExistsException
├── LoanAlreadyExistsException
└── GlobalExceptionHandler (handles all via @ControllerAdvice)
```

### 10.2 GlobalExceptionHandler Behavior

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    
    // Validation errors → Map<String, String> (field → message)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(...)
    
    // Custom exceptions → ErrorResponseDto
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFound(...)
    
    // Generic exceptions → ErrorResponseDto with INTERNAL_SERVER_ERROR
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(...)
}
```

### 10.3 HTTP Status Code Mapping

| Status | Code | Usage |
|--------|------|-------|
| 200 OK | `STATUS_200` | Successful fetch/update |
| 201 Created | `STATUS_201` | Successful creation |
| 404 Not Found | N/A | Resource not found (handled by Spring) |
| 417 Expectation Failed | `STATUS_417` | Business logic failures |
| 500 Internal Server Error | N/A | Generic exceptions |

### 10.4 Adding Custom Exceptions

```java
// 1. Create exception class
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// 2. Register in GlobalExceptionHandler
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ErrorResponseDto> handleResourceNotFound(
    ResourceNotFoundException ex) {
    ErrorResponseDto errorResponse = new ErrorResponseDto(
        request.getDescription(false),
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
    └── {Service}ApplicationTests.java  # Context load test
```

### 11.2 Test Types

**Context Load Test** (Current coverage):
```java
@SpringBootTest
class AccountsApplicationTests {
    @Test
    void contextLoads() {
        // Verifies Spring context initializes successfully
    }
}
```

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

**Current State**: Minimal (contextLoads tests only)

**Recommended Coverage**:
- Services: 80%+ (business logic)
- Controllers: 70%+ (endpoint mappings)
- Repositories: 50%+ (custom queries)
- Exceptions: 100% (error handling)

---

## 12. Security

### 12.1 ConfigServer Security

**Basic Authentication**:
```yaml
# application.yml (configserver)
spring:
  security:
    user:
      name: ${CONFIG_SERVER_USER}
      password: ${CONFIG_SERVER_PASSWORD}
```

**Environment Variables** (set in `.env.local` - git-ignored):
```env
CONFIG_SERVER_USER=admin
CONFIG_SERVER_PASSWORD=securePassword123
ENCRYPTION_KEY=mySecretEncryptionKey
```

### 12.2 API Security Considerations

**Current State**: No authentication/authorization on business services

**Recommended Additions**:
1. JWT-based authentication
2. Role-based access control (RBAC)
3. API rate limiting
4. CORS configuration

### 12.3 Secrets Management

**Do NOT commit**:
- Database passwords
- API keys
- Encryption keys
- OAuth credentials

**Use environment variables or Docker secrets**:
```yaml
# docker-compose.yml
services:
  accounts:
    environment:
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
    secrets:
      - db_password
```

---

## 13. Monitoring & Observability

### 13.1 Actuator Endpoints

| Endpoint | URL | Description |
|----------|-----|-------------|
| Health | `/actuator/health` | Application health status |
| Info | `/actuator/info` | Application information |
| Refresh | `/actuator/refresh` | Refresh configuration |
| BusRefresh | `/actuator/busrefresh` | Refresh via message bus |

### 13.2 Health Checks

**Readiness Probe** (includes RabbitMQ):
```yaml
# application.yml
management:
  health:
    readinessstate:
      enabled: true
    livenessstate:
      enabled: true
  endpoint:
    health:
      probes:
        enabled: true
```

**Response**:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### 13.3 Metrics Collection

**Available Metrics**:
- JVM memory usage
- HTTP request counts and timing
- Database connection pool stats
- RabbitMQ connection stats

**Prometheus Format**:
```bash
curl http://localhost:8080/actuator/prometheus
```

### 13.4 Logging Configuration

**Default Log Format**:
```
2026-04-01T10:30:00.000+03:00  INFO 12345 --- [accounts] [           main] c.g.accounts.AccountsApplication : Started AccountsApplication
```

**Enable Debug Logging**:
```yaml
logging:
  level:
    root: INFO
    com.ggoutos: DEBUG
    org.springframework.cloud.config: DEBUG
    org.hibernate.SQL: DEBUG
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
- Verify port (3306 dev, 3307/3308/3309 prod)

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
# Find process using port (Linux/Mac)
lsof -i :8080
# Kill process
kill -9 <PID>

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
mkdir -p src/main/resources
mkdir -p src/test/java/com/ggoutos/investments

# 4. Create main application class
# 5. Create application.yml (configserver client config)
# 6. Add config to .config/investments.yml
# 7. Update docker-compose.yml
# 8. Build and test
```

**Required Files**:
- `pom.xml` - Maven configuration
- `InvestmentsApplication.java` - Entry point with `@EnableJpaAuditing`, `@OpenAPIDefinition`
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

**Using RestTemplate**:
```java
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountsService {
    private final RestTemplate restTemplate;
    
    public CustomerDto getCustomerDetails(String mobileNumber) {
        return restTemplate.getForObject(
            "http://localhost:8080/api/fetch?mobileNumber=" + mobileNumber,
            CustomerDto.class
        );
    }
}
```

**Using WebClient** (Reactive):
```java
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountsService {
    private final WebClient webClient;
    
    public Mono<CustomerDto> getCustomerDetails(String mobileNumber) {
        return webClient.get()
            .uri("/api/fetch?mobileNumber={mobileNumber}", mobileNumber)
            .retrieve()
            .bodyToMono(CustomerDto.class);
    }
}
```

### 15.4 Database Migration Process

**Using Flyway**:
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**Migration Script** (`V1__create_investments_table.sql`):
```sql
CREATE TABLE investments (
    investment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(255),
    investment_type VARCHAR(255),
    amount INT,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);
```

---

## 16. Known Issues & Technical Debt

### 16.1 Critical Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| TC-001 | Duplicate test class in loans service (`com.eazybytes` vs `com.ggoutos`) | Build conflicts | High | Open |
| TC-002 | H2 references in documentation but MySQL in config | Confusion | Medium | Open |
| TC-003 | Empty security credentials in `.env` | Security risk | High | Open |

### 16.2 Code Quality Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| CQ-001 | Loans controller uses `@AllArgsConstructor` instead of `@RequiredArgsConstructor` | Inconsistency | Low | Open |
| CQ-002 | Loans entity missing `@Table` annotation | Potential naming issues | Low | Open |
| CQ-003 | Cards controller missing `@PostMapping` annotation | Implicit mapping | Low | Open |
| CQ-004 | Inconsistent `@Valid` usage across services | Validation gaps | Medium | Open |
| CQ-005 | Magic numbers in card number generation | Maintainability | Low | Open |
| CQ-006 | CardsServiceImpl uses `@Slf4j` but others don't | Inconsistency | Low | Open |

### 16.3 Configuration Issues

| ID | Issue | Impact | Priority | Status |
|----|-------|--------|----------|--------|
| CF-001 | Git profile hardcoded as "master" | Branch compatibility | Low | Open |
| CF-002 | External docs URL mismatch in LoansApplication | Documentation accuracy | Low | Open |
| CF-003 | Loans service has hardcoded JVM options | Inconsistency | Low | Open |

### 16.4 Planned Improvements

- [ ] Add comprehensive test coverage (target: 80%)
- [ ] Implement JWT authentication
- [ ] Add API rate limiting
- [ ] Configure Flyway/Liquibase for database migrations
- [ ] Add centralized logging (ELK stack)
- [ ] Implement distributed tracing (Sleuth/Zipkin)
- [ ] Add circuit breaker pattern (Resilience4j)
- [ ] Implement service discovery (Eureka/Consul)

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

*Last Updated: 2026-04-01*
*Version: 2.0*
