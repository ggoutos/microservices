# AGENTS.md - AI Agent Guidance for EazyBank Microservices

## Project Overview

**EazyBank Microservices** is a Java-based microservices architecture using Spring Boot 4.0+. Each service is independently deployable with its own database, API documentation, and testing suite. Current services: `accounts` (port 8080), `cards` (port 9000), `loans` (port 8090).

## Architecture Patterns

### Service Structure
Each microservice follows this standardized layout:
```
service-name/
├── src/main/java/com/ggoutos/{service}/
│   ├── {Service}Application.java          # Main entry point with @SpringBootApplication
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
└── pom.xml                                # Maven configuration
```

### Key Design Decisions

**Interface-First Services**: Services expose `IAccountsService` interface, implementations in `impl/`. Always inject via interface type.

**JPA Auditing**: All entities extend `BaseEntity` with `@MappedSuperclass` and `@EntityListeners(AuditingEntityListener.class)`. Auditor bean (e.g., `AuditAwareImpl`) must be registered as `@Component` with matching name in `@EnableJpaAuditing(auditorAwareRef = "...")`.

**Manual Mapping**: Use static mapper methods (e.g., `AccountsMapper.mapToAccountsDto()`) instead of external libraries. Pattern: `mapToType(source, target)` where target is instantiated by caller.

**Static Constants**: Immutable constants class with private constructor prevents instantiation. HTTP status codes (201, 200, 417) are Strings, not enums.

**H2 In-Memory Database**: Development uses H2 (JDBC: `jdbc:h2:mem:testdb`). Enable H2 console in `application.yml`. Schema auto-created via `schema.sql` on startup.

## Critical Workflows

### Build & Run
```bash
# Build individual service
cd accounts
mvn clean install           # Full build with tests
mvn spring-boot:run         # Start service (uses port from application.yml)
mvn test                    # Run tests only

# Service ports: accounts (8080), cards (9000), loans (8090)
```

### Debugging
- Services log to stdout; watch for Hibernate SQL output (enabled via `show-sql: true`)
- H2 console accessible at `http://localhost:{PORT}/h2-console` (e.g., accounts: 8080, cards: 9000, loans: 8090)
- OpenAPI (Swagger) docs at `http://localhost:{PORT}/swagger-ui.html`
- Actuator health endpoint: `http://localhost:{PORT}/actuator/health`

### Common Patterns to Apply

**Error Handling**: All exceptions caught by `GlobalExceptionHandler` with `@ControllerAdvice`. Custom exceptions (`ResourceNotFoundException`, `CustomerAlreadyExistsException`) return `ErrorResponseDto` with timestamp. Validation errors return `Map<String, String>` with field names as keys.

**DTO Validation**: Use Jakarta `@Valid` on request bodies. Validation annotations: `@Pattern(regexp = "...")` for strings, standard Bean Validation constraints. Messages passed to global handler.

**Response Format**: Success responses use `ResponseDto(statusCode, message)`. Status codes are String constants (e.g., `AccountsConstants.STATUS_201`).

**Repository Queries**: Extend `JpaRepository<Entity, ID>`. Spring generates CRUD methods; add custom query methods as needed (e.g., `findByMobileNumber(String mobileNumber)`).

## Dependencies & Versions

- **Java**: 25 (module system enabled)
- **Spring Boot**: 4.0.4 (all services)
- **Spring Data JPA**: Included via starter-parent
- **Validation**: spring-boot-starter-validation (Jakarta constraints)
- **API Docs**: springdoc-openapi-starter-webmvc-ui v3.0.2
- **Actuator**: spring-boot-starter-actuator (for metrics, health checks)
- **Dev Tools**: Lombok, DevTools (for hot reload), H2 database

## Integration Points

### Cross-Service Communication
*(Future expansion)* Services will integrate via REST clients or messaging. When adding inter-service calls, inject `RestTemplate` or `WebClient` (Spring-managed beans).

### Entity Relationships
- `Customer` and `Accounts` linked via `customer_id` (one-to-many)
- Both tables auto-audited with `created_at`, `created_by`, `updated_at`, `updated_by`

## Critical Files to Know

| File                          | Purpose                                                    |
|-------------------------------|------------------------------------------------------------|
| `{Service}Application.java`   | Entry point; defines API metadata via `@OpenAPIDefinition` |
| `GlobalExceptionHandler.java` | Centralized error handling; extend for new exception types |
| `BaseEntity.java`             | Auditing template; all entities inherit audit fields       |
| `{Service}Constants.java`     | HTTP status codes and business constants                   |
| `application.yml`             | Database URL, JPA config, server port                      |
| `schema.sql`                  | Initial database schema; updated on `ddl-auto: update`     |

## Before Adding New Features

1. **Interface First**: Create `INewFeatureService` before implementation
2. **Entity Before DTO**: Define JPA entity extending `BaseEntity`, then DTO for serialization
3. **Constants**: Add HTTP status codes to constants class
4. **Exception Handling**: Add custom exceptions and register in `GlobalExceptionHandler`
5. **Documentation**: Update `@Tag` and `@Operation` OpenAPI annotations on new endpoints
6. **Auditing**: Ensure entity has audit fields; verify `AuditAwareImpl` provides auditor name

## Testing Conventions

- Test classes: `{Service}ApplicationTests.java` in `src/test/java` (e.g., `AccountsApplicationTests`, `CardsApplicationTests`, `LoansApplicationTests`)
- Use Spring Test fixtures: `@SpringBootTest`, `@DataJpaTest` for repositories
- Mock external services; test auditing with mocked `AuditorAware`
- Each service has a minimal test that validates context loads: `contextLoads()` test method

