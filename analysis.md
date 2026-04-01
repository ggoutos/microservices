# Comprehensive Analysis: EazyBank Microservices Project

> **Enhanced Documentation** - Comprehensive guide for developing, building, and deploying the EazyBank microservices platform.


## Table of Contents

1. [Technology Stack with Versions](#1-technology-stack-with-versions)
2. [Services Overview](#2-services-overview)
3. [Database Schemas](#3-database-schemas)
4. [Configuration Server Details](#4-configuration-server-details)
5. [Build Commands](#5-build-commands)
6. [API Documentation Endpoints](#6-api-documentation-endpoints)
7. [Actuator Endpoints](#7-actuator-endpoints)
8. [Docker Infrastructure](#8-docker-infrastructure)
9. [Package Structure Pattern](#9-package-structure-pattern)
10. [Identified Patterns](#10-identified-patterns)
11. [Inconsistencies & Issues Found](#11-inconsistencies--issues-found)
12. [AI Assistant Configurations](#12-ai-assistant-configurations)
13. [Recommended Project Structure for AGENTS2.md](#13-recommended-project-structure-for-agents2md)
14. [Summary Statistics](#14-summary-statistics)

---

## 1. Technology Stack with Versions

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
| **H2 Database** | Runtime | In-memory dev database (deprecated - all services now use MySQL) |
| **Lombok** | Included | Boilerplate reduction |
| **SpringDoc OpenAPI** | 3.0.2 | API documentation (Swagger UI) |
| **Spring Cloud Config** | Included | Centralized configuration |
| **Spring Cloud Bus** | Included | Config refresh via RabbitMQ |
| **RabbitMQ** | 4.2.5-management | Message broker for config refresh |
| **GraalVM Native** | Included | Native image compilation |
| **Jib Maven Plugin** | 3.5.1 | Container image building |
| **Docker** | Latest | Containerization |

---

## 2. Services Overview

### 2.1 ConfigServer (Port 8071)

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
- Git-backed configuration with fallback to native (classpath:/shared, classpath:/config)
- RabbitMQ integration for distributed config refresh (`/actuator/busrefresh`)
- Encryption support via `ENCRYPTION_KEY` environment variable
- Health checks with readiness/liveness probes
- Actuator endpoints: `health`, `info`, `refresh`, `busrefresh`

### 2.2 Accounts Service (Port 8080)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.accounts` |
| **Main Class** | `AccountsApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, :3307 prod) |
| **Docker Image** | `ggoutos/accounts:jib` |
| **Custom Dockerfile** | Yes (multi-stage: JVM + Native) |

**Entities:**
- `Customer` (customer_id PK, name, email, mobile_number)
- `Accounts` (account_number PK, customer_id FK, account_type, branch_address)

**Relationship:** One-to-one (Customer → Accounts via customer_id)

### 2.3 Cards Service (Port 9000)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.cards` |
| **Main Class** | `CardsApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, :3308 prod) |
| **Docker Image** | `ggoutos/cards:jib` |

**Entities:**
- `Cards` (card_id PK, mobile_number, card_number, card_type, total_limit, amount_used, available_amount)

### 2.4 Loans Service (Port 8090)

| Property | Value |
|----------|-------|
| **Package** | `com.ggoutos.loans` |
| **Main Class** | `LoansApplication.java` |
| **Annotations** | `@SpringBootApplication`, `@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")`, `@OpenAPIDefinition` |
| **Database** | MySQL (localhost:3306 dev, :3309 prod) |
| **Docker Image** | `ggoutos/loans:jib` |

**Entities:**
- `Loans` (loan_id PK, mobile_number, loan_number, loan_type, total_loan, amount_paid, outstanding_amount)

---

## 3. Database Schemas

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

## 4. Configuration Server Details

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
| `cards-prod.yml` | prod | `jdbc:mysql://localhost:3308/cardsdb` |
| `loans.yml` | default | `jdbc:mysql://localhost:3306/loansdb` |
| `loans-prod.yml` | prod | `jdbc:mysql://localhost:3309/loansdb` |

### Security Configuration
- **Username:** `${CONFIG_SERVER_USER}` (env var)
- **Password:** `${CONFIG_SERVER_PASSWORD}` (env var)
- **Encryption Key:** `${ENCRYPTION_KEY}` (env var)
- **Endpoints Protected:** All except `/actuator/health/**`

### Config Refresh Flow
1. Git push triggers webhook to `/monitor` endpoint (via `spring-cloud-config-monitor`)
2. ConfigServer publishes refresh event to RabbitMQ
3. All services listen on message bus and refresh config via `/actuator/busrefresh`

---

## 5. Build Commands

### Development (Local)
```bash
# Build all services
mvn clean install

# Run individual service
cd accounts && mvn spring-boot:run
cd cards && mvn spring-boot:run
cd loans && mvn spring-boot:run
cd configserver && mvn spring-boot:run

# Run tests only
mvn test
```

### Docker Images

#### Option 1: Jib (All Services - Recommended)
```bash
cd {service}
mvn compile jib:dockerBuild
# Creates: ggoutos/{service}:jib
```

#### Option 2: Buildpacks (All Services)
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

#### Option 4: Native Image (GraalVM - All Services)
```bash
mvn -Pnative native:compile
```

### Docker Compose (Full Stack)
```bash
cd .docker
docker compose up --build          # Dev (default profile)
docker compose --env-file .env.prod up --build  # Production profile
```

---

## 6. API Documentation Endpoints

| Service | Port | Swagger UI | OpenAPI JSON |
|---------|------|------------|--------------|
| ConfigServer | 8071 | N/A (no REST API) | N/A |
| Accounts | 8080 | `http://localhost:8080/swagger-ui.html` | `/v3/api-docs` |
| Cards | 9000 | `http://localhost:9000/swagger-ui.html` | `/v3/api-docs` |
| Loans | 8090 | `http://localhost:8090/swagger-ui.html` | `/v3/api-docs` |

### API Endpoint Patterns (All Services)

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

## 7. Actuator Endpoints

| Service | Port | Health | Info | Refresh | BusRefresh |
|---------|------|--------|------|---------|------------|
| ConfigServer | 8071 | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |
| Accounts | 8080 | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |
| Cards | 9000 | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |
| Loans | 8090 | `/actuator/health` | `/actuator/info` | `/actuator/refresh` | `/actuator/busrefresh` |

**Health Check Probes:**
- Readiness: Includes RabbitMQ connectivity check
- Liveness: Basic application alive check

---

## 8. Docker Infrastructure

### Docker Compose Services (`.docker/docker-compose.yml`)

| Service | Image | Port | Dependencies |
|---------|-------|------|--------------|
| `rabbit` | `rabbitmq:4.2.5-management-alpine` | 5672, 15672 | None |
| `configserver` | `${CONFIGSERVER_IMAGE}` | 8071 | rabbit |
| `accounts` | `${ACCOUNTS_IMAGE}` | 8080 | configserver |
| `cards` | `${CARDS_IMAGE}` | 9000 | configserver |
| `loans` | `${LOANS_IMAGE}` | 8090 | configserver |

**Resource Limits (All Microservices):**
- CPU: 0.50 cores
- Memory: 512M

**Environment Variables (`.env`):**
```env
CONFIGSERVER_IMAGE=ggoutos/configserver:jib
ACCOUNTS_IMAGE=ggoutos/accounts:jib
CARDS_IMAGE=ggoutos/cards:jib
LOANS_IMAGE=ggoutos/loans:jib
SPRING_PROFILES_ACTIVE=default
SPRING_RABBITMQ_HOST=rabbit
SPRING_CLOUD_CONFIG_URI=http://configserver:8071
```

---

## 9. Package Structure Pattern (Per Service)

```
{service}/
├── src/main/java/com/ggoutos/{service}/
│   ├── {Service}Application.java          # Main entry point
│   ├── controller/                        # REST controllers (1 per service)
│   ├── service/
│   │   ├── I{Service}Service.java         # Interface
│   │   └── impl/
│   │       └── {Service}ServiceImpl.java  # Implementation
│   ├── entity/
│   │   ├── BaseEntity.java                # @MappedSuperclass with audit fields
│   │   └── {Entity}.java                  # JPA entities
│   ├── dto/
│   │   ├── {Entity}Dto.java               # Data transfer objects
│   │   ├── ResponseDto.java               # Success response
│   │   └── ErrorResponseDto.java          # Error response
│   ├── mapper/
│   │   └── {Entity}Mapper.java            # Static mapping utilities
│   ├── repository/
│   │   └── {Entity}Repository.java        # JpaRepository interfaces
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java    # @ControllerAdvice
│   │   ├── ResourceNotFoundException.java # Custom exception
│   │   └── {Entity}AlreadyExistsException.java
│   ├── audit/
│   │   └── AuditAwareImpl.java            # AuditorAware<String> implementation
│   └── constants/
│       └── {Service}Constants.java        # Immutable constants class
├── src/main/resources/
│   └── application.yml                    # Bootstrap config (configserver connection)
├── src/test/java/
│   └── **/{Service}ApplicationTests.java  # Context load tests
├── Dockerfile                             # Accounts only (multi-stage)
└── pom.xml                                # Service-specific dependencies
```

---

## 10. Identified Patterns

### Naming Conventions
- **Packages:** `com.ggoutos.{service}` (lowercase)
- **Interfaces:** `I{Service}Service` (capital I prefix)
- **Implementations:** `{Service}ServiceImpl` in `impl/` subpackage
- **DTOs:** `{Entity}Dto` suffix
- **Mappers:** `{Entity}Mapper` with static methods `mapTo{Type}(source, target)`
- **Constants:** `{Service}Constants` with private constructor
- **Exceptions:** `{Resource}NotFoundException`, `{Entity}AlreadyExistsException`
- **Audit Component:** `AuditAwareImpl` with bean name `"auditAwareImpl"`

### Exception Handling Pattern
```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    // Validation errors → Map<String, String> (field → message)
    // Custom exceptions → ErrorResponseDto (apiPath, errorCode, errorMessage, errorTime)
    // Generic exceptions → ErrorResponseDto with INTERNAL_SERVER_ERROR
}
```

### Response Format Pattern
```java
// Success
ResponseDto(statusCode: "201"|"200", statusMsg: "Message")

// Error
ErrorResponseDto(apiPath, errorCode: HttpStatus, errorMessage, errorTime: LocalDateTime)
```

### HTTP Status Code Pattern (String Constants)
```java
STATUS_201 = "201"  // Created
STATUS_200 = "200"  // OK
STATUS_417 = "417"  // Expectation Failed (custom business logic failures)
// Note: 500 is commented out - handled generically
```

### Auditing Pattern
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {
    @CreatedDate @Column(updatable = false)
    private LocalDateTime createdAt;
    @CreatedBy @Column(updatable = false)
    private String createdBy;
    @LastModifiedDate @Column(insertable = false)
    private LocalDateTime updatedAt;
    @LastModifiedBy @Column(insertable = false)
    private String updatedBy;
}

@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {
    public Optional<String> getCurrentAuditor() {
        return Optional.of("{SERVICE}_MS");  // e.g., "ACCOUNTS_MS"
    }
}
```

### Mapper Pattern (Manual, NOT MapStruct)
```java
public class {Entity}Mapper {
    // DTO ← Entity
    public static {Entity}Dto mapTo{Entity}Dto({Entity} entity, {Entity}Dto dto) {
        // set dto fields from entity
        return dto;
    }
    
    // Entity ← DTO
    public static {Entity} mapTo{Entity}({Entity}Dto dto, {Entity} entity) {
        // set entity fields from dto
        return entity;
    }
}
```

**Usage Pattern:**
```java
Dto dto = Mapper.mapToDto(entity, new Dto());  // Caller instantiates target
```

---

## 11. Inconsistencies & Issues Found

### 11.1 Critical Issues

1. **Duplicate Test Class in Loans Service:**
   - `loans/src/test/java/com/eazybytes/loans/LoansApplicationTests.java` (old package)
   - `loans/src/test/java/com/ggoutos/loans/LoansApplicationTests.java` (correct package)
   - **Action:** Remove the `com.eazybytes` version

2. **Loans Controller Uses Deprecated `@AllArgsConstructor`:**
   - Uses Lombok's `@AllArgsConstructor` instead of `@RequiredArgsConstructor`
   - Other services use `@RequiredArgsConstructor` with `final` fields
   - **Action:** Standardize to `@RequiredArgsConstructor` with `final` fields

3. **Loans Entity Missing `@Table` Annotation:**
   - `Loans.java` doesn't specify table name explicitly
   - `Cards.java` has `@Table(name = "cards")`
   - **Action:** Add `@Table(name = "loans")` for consistency

4. **Cards Controller Missing `@PostMapping` Annotation:**
   - `createCard()` method lacks `@PostMapping("/create")` annotation
   - Other services have explicit mapping
   - **Action:** Add `@PostMapping("/create")` annotation

### 11.2 Configuration Inconsistencies

5. **H2 References in AGENTS.md but MySQL in Config:**
   - Documentation mentions H2 for cards/loans dev
   - All `application.yml` files configure MySQL dialect
   - **Action:** Update documentation to reflect MySQL-only strategy

6. **External Docs URL Mismatch:**
   - `LoansApplication.java` externalDocs says "Cards microservice" instead of "Loans"
   - **Action:** Fix copy-paste error

7. **Git Profile Hardcoded as "master":**
   - ConfigServer uses `default-label: master`
   - Modern Git default is `main`
   - **Action:** Consider updating to `main` or document requirement

### 11.3 Security Concerns

8. **Empty Security Credentials in `.env`:**
   - `CONFIG_SERVER_PASSWORD=`, `CONFIG_SERVER_USER=`, `ENCRYPTION_KEY=` are blank
   - **Action:** Document requirement to set these in `.env.local` (git-ignored)

9. **CSRF Disabled in ConfigServer:**
   - `http.csrf(AbstractHttpConfigurer::disable)`
   - Acceptable for internal service but should be documented
   - **Action:** Add security documentation

### 11.4 Docker/Deployment Issues

10. **Loans Service Has Hardcoded JVM Options:**
    ```yaml
    - JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=1 -Xss256k
    ```
    - Other services don't have this
    - **Action:** Either apply to all services or document why loans needs it

11. **Accounts Dockerfile Build Context Issue:**
    - Dockerfile copies `src ./src` but Maven needs `pom.xml` first
    - Build may fail due to incorrect layer ordering
    - **Action:** Verify Dockerfile build works correctly

### 11.5 Code Quality Issues

12. **CardsServiceImpl Uses `@Slf4j` but Others Don't:**
    - Only Cards service has logging
    - **Action:** Either add logging to all services or remove from Cards for consistency

13. **Inconsistent `@Valid` Usage:**
    - Accounts: `@Valid @RequestBody CustomerDto`
    - Cards: `@Valid @RequestParam String mobileNumber` (on create)
    - Loans: No `@Valid` on create (only mobileNumber param)
    - **Action:** Standardize validation approach

14. **Magic Number in Card Number Generation:**
    ```java
    long cardNumber = 1000000000000000L + random.nextLong(9000000000000000L);
    ```
    - Should be in constants
    - **Action:** Move to `CardsConstants`

---

## 12. AI Assistant Configurations

### 12.1 `.ai/` Directory (Local AI Stack)
- **Purpose:** Docker-based local AI coding assistant
- **Components:**
  - Ollama (port 11434): LLM runtime with GPU support
  - Aider: AI coding agent
  - Continue.dev: IntelliJ plugin config
- **Models:**
  - Agent: `qwen2.5-coder:7b-instruct-q4_K_M`
  - Autocomplete: `qwen2.5-coder:3b`
  - Reasoning: `qwen2.5:7b-instruct`

### 12.2 `.aiassistant/rules/AGENTS.md`
- **Purpose:** JetBrains AI Assistant rules file
- **Content:** Points to root `/AGENTS.md`

### 12.3 Root `AGENTS.md`
- **Purpose:** GitHub Copilot + general AI agent guidance
- **Content:** Comprehensive project documentation and patterns

---

## 13. Recommended Project Structure for AGENTS2.md

Based on this analysis, here's the recommended structure for an enhanced `AGENTS2.md`:

```markdown
# AGENTS2.md - Enhanced AI Agent Guidance for EazyBank Microservices

## 1. Quick Reference Card
- Service ports table
- Build commands cheat sheet
- Docker commands cheat sheet
- API endpoint patterns

## 2. Architecture Deep Dive
- System context diagram (C4 model)
- Container diagram
- Component diagram per service
- Data flow diagrams

## 3. Technology Stack
- Complete dependency tree with versions
- Why each technology was chosen
- Alternatives considered

## 4. Service Specifications
For each service (configserver, accounts, cards, loans):
- Purpose and responsibilities
- Package structure
- Key classes with descriptions
- Database schema
- API endpoints with examples
- Configuration properties

## 5. Development Workflows
- Local development setup (step-by-step)
- Debugging guide
- Testing strategies
- Code style guidelines

## 6. Build & Deployment
- Maven build lifecycle explanation
- Docker image building (all 4 methods)
- Docker Compose orchestration
- Production deployment checklist
- Environment variables reference

## 7. Configuration Management
- ConfigServer architecture
- Git vs Native backend
- Config refresh mechanism
- Encryption/decryption guide
- Profile management

## 8. Data Model
- ER diagrams per service
- Entity relationship documentation
- Audit fields explanation
- Migration strategies

## 9. API Documentation
- OpenAPI/Swagger usage
- Request/response examples
- Error handling patterns
- Validation rules

## 10. Exception Handling
- Exception hierarchy
- GlobalExceptionHandler behavior
- Custom exception guidelines
- HTTP status code mapping

## 11. Testing Guide
- Unit test patterns
- Integration test setup
- Test containers usage (if applicable)
- Coverage expectations

## 12. Security
- Authentication/authorization
- ConfigServer security
- API security considerations
- Secrets management

## 13. Monitoring & Observability
- Actuator endpoints
- Health checks
- Metrics collection
- Logging configuration

## 14. Troubleshooting
- Common issues and solutions
- Debug mode activation
- Log analysis guide
- Performance tuning

## 15. Extending the Platform
- Adding a new microservice (step-by-step)
- Adding new endpoints
- Database migration process
- Inter-service communication patterns

## 16. Known Issues & Technical Debt
- Current inconsistencies (from section 11 above)
- Planned improvements
- Migration paths

## 17. Appendices
- Glossary of terms
- Acronyms
- Reference links
- Team contacts
```

---

## 14. Summary Statistics

| Metric | Value |
|--------|-------|
| **Total Services** | 4 (configserver, accounts, cards, loans) |
| **Total Modules** | 4 Maven modules |
| **Lines of Code (Est.)** | ~2,000-3,000 (excluding generated code) |
| **Test Coverage** | Minimal (contextLoads tests only) |
| **API Endpoints per Service** | 4 (create, fetch, update, delete) |
| **Database Tables** | 5 (customer, accounts, cards, loans) |
| **Docker Images** | 4 service images + RabbitMQ |
| **Configuration Files** | 6 (2 per service: default + prod) |

---

This comprehensive analysis provides a complete understanding of the EazyBank microservices architecture, ready for creating an enhanced `AGENTS2.md` documentation file.
