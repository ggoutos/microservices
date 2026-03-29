# AGENTS.md - AI Agent Guidance for EazyBank Microservices

## Project Overview

**EazyBank Microservices** is a Java-based microservices architecture using Spring Boot 4.0.4. Each service is independently deployable with its own H2 in-memory database, API documentation, and testing suite. Services: `accounts` (port 8080), `cards` (port 9000), `loans` (port 8090).

## Architecture Patterns

### Service Structure
Each microservice follows this standardized layout:
```
service-name/
├── src/main/java/com/ggoutos/{service}/
│   ├── {Service}Application.java          # Main entry point with @SpringBootApplication + @OpenAPIDefinition
│   ├── controller/                        # REST endpoints
│   ├── service/                           # I{ServiceName}Service (interface-first pattern)
│   │   └── impl/                          # Implementation classes
│   ├── entity/                            # JPA entities (extend BaseEntity for auditing)
│   ├── dto/                               # Data transfer objects
│   ├── mapper/                            # Static mapper utilities (NOT MapStruct)
│   ├── repository/                        # JpaRepository interfaces
│   ├── exception/                         # Custom exceptions + GlobalExceptionHandler
│   ├── audit/                             # Auditing implementation (e.g., AuditAwareImpl)
│   └── constants/                         # Immutable constants class
├── Dockerfile                             # Multi-stage build (accounts only currently)
├── compose.yaml                           # Docker Compose (accounts only currently)
└── pom.xml                                # Maven configuration
```

### Key Design Decisions

**Interface-First Services**: Services expose `IAccountsService` interface, implementations in `impl/`. Always inject via interface type.

**JPA Auditing**: All entities extend `BaseEntity` with `@MappedSuperclass` and `@EntityListeners(AuditingEntityListener.class)`. Auditor bean (e.g., `AuditAwareImpl`) must be registered as `@Component` with matching name in `@EnableJpaAuditing(auditorAwareRef = "...")`.

**Manual Mapping**: Use static mapper methods (e.g., `AccountsMapper.mapToAccountsDto()`) instead of external libraries. Pattern: `mapToType(source, target)` where target is instantiated by caller.

**Static Constants**: Immutable constants class with private constructor prevents instantiation. HTTP status codes (201, 200, 417) are Strings, not enums.

**H2 In-Memory Database**: Development uses H2 (JDBC: `jdbc:h2:mem:testdb`). Enable H2 console in `application.yml`. Schema auto-created via JPA `ddl-auto: update`.

## Critical Workflows

### Build & Run
```bash
# Build individual service
cd accounts          # or cards, loans
mvn clean install    # Full build with tests
mvn spring-boot:run  # Start service (uses port from application.yml)
mvn test             # Run tests only

# Service ports: accounts (8080), cards (9000), loans (8090)
```

### Docker
```bash
# Build and run with Docker Compose (accounts service)
cd accounts
docker compose up --build

# Multi-stage Docker build (defined in accounts/Dockerfile):
# Stage 1: Maven 3.9 + Amazon Corretto JDK 25 → builds JAR
# Stage 2: jlink creates minimal custom JRE
# Stage 3: Alpine 3.23 final image — minimal footprint
```

[//]: # (### AI Development Environment)

[//]: # (The `.ai/` directory contains a local AI coding assistant setup:)

[//]: # (- **Ollama** &#40;port 11434&#41;: LLM runtime with GPU support)

[//]: # (- **Aider**: AI coding agent wired to Ollama)

[//]: # (- Configured via `AGENT_MODEL`, `AUTOCOMPLETE_MODEL`, `REASONING_MODEL` env vars)

### Debugging
- Services log to stdout; watch for Hibernate SQL output (enabled via `show-sql: true`)
- H2 console at `http://localhost:{PORT}/h2-console` (accounts: 8080, cards: 9000, loans: 8090)
- OpenAPI (Swagger) docs at `http://localhost:{PORT}/swagger-ui.html`
- Actuator health endpoint: `http://localhost:{PORT}/actuator/health`

### Common Patterns to Apply

**Error Handling**: All exceptions caught by `GlobalExceptionHandler` with `@ControllerAdvice`. Custom exceptions (`ResourceNotFoundException`, `CustomerAlreadyExistsException`) return `ErrorResponseDto` with timestamp. Validation errors return `Map<String, String>` with field names as keys.

**DTO Validation**: Use Jakarta `@Valid` on request bodies. Validation annotations: `@Pattern(regexp = "...")` for strings, standard Bean Validation constraints. Messages passed to global handler.

**Response Format**: Success responses use `ResponseDto(statusCode, message)`. Status codes are String constants (e.g., `AccountsConstants.STATUS_201`).

**Repository Queries**: Extend `JpaRepository<Entity, ID>`. Spring generates CRUD methods; add custom query methods as needed (e.g., `findByMobileNumber(String mobileNumber)`).

## Dependencies & Versions

- **Java**: 25 (module system enabled)
- **Spring Boot**: 4.0.4 (all services, via starter-parent)
- **Spring Data JPA**: Included via starter-parent
- **Validation**: spring-boot-starter-validation (Jakarta constraints)
- **API Docs**: springdoc-openapi-starter-webmvc-ui v3.0.2
- **Actuator**: spring-boot-starter-actuator (health checks, metrics)
- **Dev Tools**: Lombok (annotation processing), DevTools (hot reload), H2 database

## Data Model

### Accounts Service
```sql
CUSTOMER: customer_id (PK), name, email, mobile_number + audit fields
ACCOUNTS: account_number (PK), customer_id (FK), account_type, branch_address + audit fields
```
Relationship: one Customer → one Account (linked by `customer_id`)

### Cards Service
```sql
CARDS: card_id (PK), mobile_number, card_number, card_type,
       total_limit, amount_used, available_amount + audit fields
```

### Loans Service
```sql
LOANS: loan_id (PK), mobile_number, loan_number, loan_type,
       total_loan, amount_paid, outstanding_amount + audit fields
```

**Audit fields on all tables**: `created_at`, `created_by`, `updated_at`, `updated_by` — populated automatically by `AuditingEntityListener`. Mobile number is the cross-service join key.

## API Endpoints (all services)

| Method | Endpoint       | Description                              |
|--------|----------------|------------------------------------------|
| POST   | `/api/create`  | Create resource (mobile number as param) |
| GET    | `/api/fetch`   | Fetch by mobile number (query param)     |
| PUT    | `/api/update`  | Update resource (request body)           |
| DELETE | `/api/delete`  | Delete by mobile number (query param)    |

## Integration Points

### Cross-Service Communication
Currently **none** — services are fully isolated with independent databases. Mobile number is the shared identifier across services. When adding inter-service calls, inject `RestTemplate` or `WebClient` as Spring-managed beans.

### Entity Relationships
- `Customer` and `Accounts` linked via `customer_id` (one-to-one in current impl)
- `Cards` and `Loans` reference customers via `mobile_number` only (no FK constraints cross-service)

## Critical Files to Know

| File                          | Purpose                                                    |
|-------------------------------|------------------------------------------------------------|
| `{Service}Application.java`   | Entry point; defines API metadata via `@OpenAPIDefinition` |
| `GlobalExceptionHandler.java` | Centralized error handling; extend for new exception types |
| `BaseEntity.java`             | Auditing template; all entities inherit audit fields       |
| `{Service}Constants.java`     | HTTP status codes and business constants                   |
| `application.yml`             | Database URL, JPA config, server port                      |
| `Dockerfile`                  | Multi-stage Docker build (accounts service)                |
| `compose.yaml`                | Docker Compose config (accounts service)                   |

[//]: # (| `.ai/docker-compose.yml`      | Local AI dev environment &#40;Ollama + Aider&#41;                  |)

## Before Adding New Features

1. **Interface First**: Create `INewFeatureService` before implementation
2. **Entity Before DTO**: Define JPA entity extending `BaseEntity`, then DTO for serialization
3. **Constants**: Add HTTP status codes to constants class
4. **Exception Handling**: Add custom exceptions and register in `GlobalExceptionHandler`
5. **Documentation**: Update `@Tag` and `@Operation` OpenAPI annotations on new endpoints
6. **Auditing**: Ensure entity has audit fields; verify `AuditAwareImpl` provides auditor name
7. **Docker**: If containerizing a new service, model `Dockerfile` and `compose.yaml` after accounts

## Testing Conventions

- Test classes: `{Service}ApplicationTests.java` in `src/test/java` (e.g., `AccountsApplicationTests`, `CardsApplicationTests`, `LoansApplicationTests`)
- Current coverage: minimal — each service has a `contextLoads()` test only
- Use Spring Test fixtures: `@SpringBootTest`, `@DataJpaTest` for repositories, `@WebMvcTest` for controllers
- Available test starters: `actuator-test`, `data-jpa-test`, `validation-test`, `webmvc-test`
- Mock external services; test auditing with mocked `AuditorAware`
