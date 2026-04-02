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
6. [Build Commands](#6-build-commands)
7. [API Documentation Endpoints](#7-api-documentation-endpoints)
8. [Actuator Endpoints](#8-actuator-endpoints)
9. [Docker Infrastructure](#9-docker-infrastructure)
10. [Package Structure Pattern](#10-package-structure-pattern)
11. [Identified Patterns](#11-identified-patterns)
12. [Critical Issues Found](#12-critical-issues-found)
13. [Code Quality Issues](#13-code-quality-issues)
14. [Security Concerns](#14-security-concerns)
15. [AI Assistant Configurations](#15-ai-assistant-configurations)
16. [Summary Statistics](#16-summary-statistics)
17. [Recommendations](#17-recommendations)

---

## 1. Executive Summary

### Project Overview
The EazyBank microservices platform is a banking application built with Spring Boot 4.0.5, Spring Cloud 2025.1.1, and Java 25. It consists of **6 Maven modules**: utils (shared library), eurekaserver (service discovery), configserver (centralized configuration), accounts, cards, and loans (business services).

### Architecture Highlights
- **Service Discovery**: Eureka Server for dynamic service registration
- **Centralized Configuration**: Config Server with Git backend (production) and native fallback (development)
- **Inter-Service Communication**: Feign clients (Accounts → Cards/Loans)
- **Database Strategy**: MySQL for all services (H2 deprecated)
- **Database Migration**: Flyway for schema version control
- **Containerization**: Docker images via Jib, Buildpacks, or custom Dockerfiles

### Current State Assessment
- ✅ **Strengths**: Modern tech stack, clean separation of concerns, comprehensive Docker orchestration
- ⚠️ **Issues**: Duplicate test files, inconsistent Lombok usage, missing `@Table` annotations
- 🔧 **In Progress**: Test coverage expansion, documentation updates

---

## 2. Technology Stack with Versions

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 25 | Runtime & compilation |
| **Maven** | 3.9+ | Build automation |
| **Spring Boot** | 4.0.5 | Application framework |
| **Spring Cloud** | 2025.1.1 | Microservices patterns |
| **Spring Data JPA** | Included | Data persistence |
| **Spring Security** | Included | Config server security |
| **Hibernate ORM** | Included | JPA implementation |
| **MySQL Connector** | Runtime | MySQL database driver |
| **Lombok** | Included | Boilerplate reduction |
| **SpringDoc OpenAPI** | 3.0.2 | API documentation (Swagger UI) |
| **Spring Cloud Config** | Included | Centralized configuration |
| **Spring Cloud Bus** | Included | Config refresh via RabbitMQ |
| **Spring Cloud Netflix Eureka** | Included | Service discovery |
| **Spring Cloud OpenFeign** | Included | Declarative REST clients |
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

**Key Features:**
- Service registration and discovery
- Self-preservation mode for production resilience
- Health checks with readiness/liveness probes
- Actuator endpoints: `health`, `info`

---

### 3.2 Utils Module

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.utils` |
| **Type** | Shared library (JAR) |
| **Dependencies** | None (pure data classes) |

**Shared DTOs:**
- `CustomerDto.java` - Customer data transfer object
- `AccountsDto.java` - Account data transfer object
- `CardsDto.java` - Card data transfer object
- `LoansDto.java` - Loan data transfer object
- `ResponseDto.java` - Standard success response
- `ErrorResponseDto.java` - Standard error response

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
| **Config Backend** | Git (prod) / Native classpath (dev fallback) |

**Key Features:**
- Git-backed configuration with fallback to native
- RabbitMQ integration for distributed config refresh
- Encryption support via `ENCRYPTION_KEY` environment variable
- Health checks with readiness/liveness probes

---

### 3.4 Accounts Service (Port 8080)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.accounts` |
| **Main Class** | `AccountsApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, :3307 prod) |
| **Docker Image** | `ggoutos/accounts:jib` |
| **Custom Dockerfile** | Yes (multi-stage: JVM + Native) |
| **Feign Clients** | Cards, Loans |

**Entities:**
- `Customer` (customer_id PK, name, email, mobile_number)
- `Accounts` (account_number PK, customer_id FK, account_type, branch_address)

**Relationship:** One-to-one (Customer → Accounts via customer_id)

---

### 3.5 Cards Service (Port 9000)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.cards` |
| **Main Class** | `CardsApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, :3309 prod) |
| **Docker Image** | `ggoutos/cards:jib` |

**Entities:**
- `Cards` (card_id PK, mobile_number, card_number, card_type, total_limit, amount_used, available_amount)

---

### 3.6 Loans Service (Port 8090)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.loans` |
| **Main Class** | `LoansApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing`, `@EnableFeignClients`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, :3308 prod) |
| **Docker Image** | `ggoutos/loans:jib` |

**Entities:**
- `Loans` (loan_id PK, mobile_number, loan_number, loan_type, total_loan, amount_paid, outstanding_amount)

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
    updated_by VARCHAR(255),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);
```

### Cards Service (MySQL)
```sql
CREATE TABLE cards (
    card_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(255),
    card_number VARCHAR(255),
    card_type VARCHAR(255),
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
| `git` (prod) | Git Repository | `${GIT_URI}` with `/.config` search path |
| `native` (dev fallback) | Classpath | `classpath:/shared`, `classpath:/config` |

### Configuration Files Served
| File | Profile | Database URL |
|------|---------|--------------|
| `accounts.yml` | default | `jdbc:mysql://localhost:3306/accountsdb` |
| `accounts-prod.yml` | prod | `jdbc:mysql://localhost:3307/accountsdb` |
| `cards.yml` | default | `jdbc:mysql://localhost:3306/cardsdb` |
| `cards-prod.yml` | prod | `jdbc:mysql://localhost:3309/cardsdb` |
| `loans.yml` | default | `jdbc:mysql://localhost:3306/loansdb` |
| `loans-prod.yml` | prod | `jdbc:mysql://localhost:3308/loansdb` |

### Security Configuration
- **Username:** `${CONFIG_SERVER_USER}` (env var)
- **Password:** `${CONFIG_SERVER_PASSWORD}` (env var)
- **Encryption Key:** `${ENCRYPTION_KEY}` (env var)
- **Endpoints Protected:** All except `/actuator/health/**`

### Config Refresh Flow
1. Git push triggers webhook to `/monitor` endpoint
2. ConfigServer publishes refresh event to RabbitMQ
3. All services listen on message bus and refresh config via `/actuator/busrefresh`

---

## 6. Build Commands

### Development (Local)
```bash
# Build all services
mvn clean install

# Run individual service
cd accounts && mvn spring-boot:run

# Run tests only
mvn test
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

## 7. API Documentation Endpoints

| Service | Port | Swagger UI | OpenAPI JSON |
|---------|------|------------|--------------|
| Eureka Server | 8070 | N/A | N/A |
| ConfigServer | 8071 | N/A | N/A |
| Accounts | 8080 | `http://localhost:8080/swagger-ui.html` | `/v3/api-docs` |
| Cards | 9000 | `http://localhost:9000/swagger-ui.html` | `/v3/api-docs` |
| Loans | 8090 | `http://localhost:8090/swagger-ui.html` | `/v3/api-docs` |

### API Endpoint Patterns (All Business Services)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/create` | Create resource | DTO / mobileNumber param | `ResponseDto` |
| GET | `/api/fetch` | Fetch by mobile number | `?mobileNumber={}` | Resource DTO |
| PUT | `/api/update` | Update resource | DTO | `ResponseDto` |
| DELETE | `/api/delete` | Delete by mobile number | `?mobileNumber={}` | `ResponseDto` |

### Validation Rules
- **Mobile Number:** 10 digits (`regexp = "(^$|[0-9]{10})"`)
- **Account Number:** 10 digits
- **Card Number:** 12 digits
- **Loan Number:** 12 digits
- **Email:** Valid email format
- **Name:** 5-30 characters

---

## 8. Actuator Endpoints

| Service | Health | Info | Refresh | BusRefresh |
|---------|--------|------|---------|------------|
| Eureka Server | `/actuator/health` | `/actuator/info` | N/A | N/A |
| ConfigServer | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |
| Accounts | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |
| Cards | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |
| Loans | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |

**Health Check Probes:**
- Readiness: Includes RabbitMQ connectivity check
- Liveness: Basic application alive check

---

## 9. Docker Infrastructure

### Docker Compose Services

| Service | Image | Port | Dependencies |
|---------|-------|------|--------------|
| `rabbit` | `rabbitmq:4.2.5-management-alpine` | 5672, 15672 | None |
| `eurekaserver` | `ggoutos/eurekaserver:jib` | 8070 | rabbit |
| `configserver` | `ggoutos/configserver:jib` | 8071 | rabbit |
| `accountsdb` | `mysql:lts` | 3307 | None |
| `cardsdb` | `mysql:lts` | 3309 | None |
| `loansdb` | `mysql:lts` | 3308 | None |
| `accounts` | `ggoutos/accounts:jib` | 8080 | configserver, eurekaserver, accountsdb |
| `cards` | `ggoutos/cards:jib` | 9000 | configserver, eurekaserver, cardsdb |
| `loans` | `ggoutos/loans:jib` | 8090 | configserver, eurekaserver, loansdb |

**Resource Limits (All Microservices):**
- CPU: 0.50 cores
- Memory: 512M

---

## 10. Package Structure Pattern

```
{service}/
├── src/main/java/com/ggoutos/{service}/
│   ├── {Service}Application.java
│   ├── controller/
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
│   └── application.yml
├── src/test/java/
│   └── **/{Service}ApplicationTests.java
├── Dockerfile (Accounts only)
└── pom.xml
```

---

## 11. Identified Patterns

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
}
```

### Mapper Pattern (Manual)
```java
public class {Entity}Mapper {
    public static {Entity}Dto mapTo{Entity}Dto({Entity} entity, {Entity}Dto dto) {
        // set dto fields from entity
        return dto;
    }
}
```

---

## 12. Critical Issues Found

### 12.1 Duplicate Test Class in Loans Service
**Severity**: HIGH

**Issue:**
- `loans/src/test/java/com/eazybytes/loans/LoansApplicationTests.java` (old package - legacy)
- `loans/src/test/java/com/ggoutos/loans/LoansApplicationTests.java` (correct package)

**Action Required:** Remove `com.eazybytes.loans.LoansApplicationTests.java`

---

### 12.2 Loans Controller Uses `@AllArgsConstructor`
**Severity**: MEDIUM

**Issue:**
- `LoansController.java` uses `@AllArgsConstructor` instead of `@RequiredArgsConstructor`
- Other services use `@RequiredArgsConstructor` with `final` fields for constructor injection

**Action Required:** Standardize to `@RequiredArgsConstructor` with `final` fields

---

### 12.3 Loans Entity Missing `@Table` Annotation
**Severity**: LOW

**Issue:**
- `Loans.java` doesn't specify table name explicitly
- `Cards.java` has `@Table(name = "cards")`

**Action Required:** Add `@Table(name = "loans")` for consistency

---

### 12.4 Copy-Paste Error in LoansApplication
**Severity**: LOW

**Issue:**
- `LoansApplication.java` externalDocs description says "Cards microservice" instead of "Loans"

**Action Required:** Fix documentation text

---

## 13. Code Quality Issues

### 13.1 Inconsistent Lombok Annotations
- Cards service entities use `@RequiredArgsConstructor`
- Accounts service entities use `@RequiredArgsConstructor`
- Loans service entities use `@AllArgsConstructor`

**Recommendation:** Standardize all entities to `@RequiredArgsConstructor`

---

### 13.2 CardsServiceImpl Uses `@Slf4j`
- Only Cards service has logging with `@Slf4j`
- Other services don't have logging

**Recommendation:** Either add logging to all services or remove from Cards for consistency

---

### 13.3 Inconsistent `@Valid` Usage
- Accounts: `@Valid @RequestBody CustomerDto`
- Cards: `@Valid @RequestParam String mobileNumber`
- Loans: No `@Valid` on create

**Recommendation:** Standardize validation approach across all services

---

### 13.4 Magic Number in Card Number Generation
```java
long cardNumber = 1000000000000000L + random.nextLong(9000000000000000L);
```

**Recommendation:** Move constants to `CardsConstants`

---

## 14. Security Concerns

### 14.1 Empty Security Credentials in `.env`
**Risk**: HIGH

The `.docker/.env` file has blank credentials with a note to set them in `.env.local`:
```env
#MYSQL_ROOT_PASSWORD=
#CONFIG_SERVER_PASSWORD=
#CONFIG_SERVER_USER=
#ENCRYPTION_KEY=
```

**Recommendation:** Ensure `.env.local` is in `.gitignore` and document setup requirements

---

### 14.2 CSRF Disabled in ConfigServer
**Risk**: LOW (internal service)

```java
http.csrf(AbstractHttpConfigurer::disable)
```

**Recommendation:** Document this as acceptable for internal services

---

## 15. AI Assistant Configurations

### 15.1 `.ai/` Directory (Local AI Stack)
- **Purpose:** Docker-based local AI coding assistant
- **Components:**
  - Ollama (port 11434): LLM runtime with GPU support
  - Aider: AI coding agent
  - Continue.dev: IntelliJ plugin config
- **Models:**
  - Agent: `qwen2.5-coder:7b-instruct-q4_K_M`
  - Autocomplete: `qwen2.5-coder:3b`
  - Reasoning: `qwen2.5:7b-instruct`

### 15.2 `.aiassistant/rules/AGENTS.md`
- **Purpose:** JetBrains AI Assistant rules file
- **Content:** Points to root `/AGENTS.md`

### 15.3 Root `AGENTS.md`
- **Purpose:** GitHub Copilot + general AI agent guidance
- **Content:** Comprehensive project documentation and patterns

---

## 16. Summary Statistics

| Metric | Value |
|--------|-------|
| **Total Maven Modules** | 6 (utils, eurekaserver, configserver, accounts, cards, loans) |
| **Business Services** | 3 (accounts, cards, loans) |
| **Infrastructure Services** | 2 (eurekaserver, configserver) |
| **Shared Libraries** | 1 (utils) |
| **Database Tables** | 5 (customer, accounts, cards, loans) |
| **API Endpoints per Service** | 4 (create, fetch, update, delete) |
| **Docker Images** | 6 service images + RabbitMQ + MySQL |
| **Configuration Files** | 6 (2 per business service: default + prod) |
| **Test Coverage** | Minimal (contextLoads tests only) |
| **Lines of Code (Est.)** | ~3,000-4,000 (excluding generated code) |

---

## 17. Recommendations

### Immediate Actions (High Priority)
1. ✅ **Remove duplicate test file** - Delete `com.eazybytes.loans.LoansApplicationTests.java`
2. ✅ **Fix LoansController** - Change `@AllArgsConstructor` to `@RequiredArgsConstructor`
3. ✅ **Add `@Table` annotation** - Add to `Loans.java` entity
4. ✅ **Fix copy-paste error** - Update `LoansApplication.java` externalDocs

### Short-Term Improvements (Medium Priority)
5. **Standardize Lombok usage** - Use `@RequiredArgsConstructor` consistently
6. **Add comprehensive tests** - Target 80% coverage for services
7. **Document security setup** - Add `.env.local` setup instructions
8. **Add logging consistently** - Either add to all services or remove from Cards

### Long-Term Enhancements (Low Priority)
9. **Implement JWT authentication** - Add security to business services
10. **Add API rate limiting** - Protect against abuse
11. **Implement distributed tracing** - Sleuth/Zipkin for observability
12. **Add circuit breaker pattern** - Resilience4j for fault tolerance
13. **Implement service mesh** - Consider Istio for production

---

**Document End**
