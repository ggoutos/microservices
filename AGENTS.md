# EazyBank Microservices Platform - Project Context

> Minimal reference document for AI-assisted development. Contains essential project structure, conventions, and key patterns.

---

## Tech Stack

| Technology | Version |
|------------|---------|
| Java | 25 |
| Spring Boot | 4.0.5 |
| Spring Cloud | 2025.1.1 |
| Build Tool | Maven (multi-module) |
| Container | Jib / Buildpacks / GraalVM |

---

## Project Structure

```
microservices/
├── pom.xml                    # Parent POM (manages all modules)
├── utils/                     # Shared DTOs library (not a Spring Boot app)
├── message/                   # Event-driven messaging service
├── eurekaserver/              # Service discovery
├── configserver/              # Centralized configuration
├── gatewayserver/             # API Gateway (WebFlux/Reactive)
├── accounts/                  # Customer accounts management
├── cards/                     # Credit/debit cards management
└── loans/                     # Loan management
```

---

## Service Ports

| Service | Port | Exposed |
|---------|------|---------|
| gatewayserver | 8072 | ✅ (main entry point) |
| eurekaserver | 8070 | ✅ |
| configserver | 8071 | ✅ |
| accounts | 8080 | ❌ (internal) |
| loans | 8090 | ❌ (internal) |
| cards | 9000 | ❌ (internal) |
| message | 9010 | ❌ (internal) |

**Infrastructure**: Keycloak (7080), RabbitMQ (5672/15672), Kafka (9092), Redis (6379), MySQL (3307-3309), Prometheus (9090), Grafana (3000), Tempo (4318/3110), Loki (3100)

---

## Gateway Routing

All external traffic goes through gateway at port 8072:

```
Incoming:  /goutos/bank/{service}/**
           ↓  Path rewrite strips prefix
           ↓  Forward to service (direct URI with circuit breaker + retry)
Upstream:  /**
```

**Security**: OAuth2 Resource Server (Keycloak JWT). GET requests permitted, POST/PUT/DELETE require role-based access (ACCOUNTS, CARDS, LOANS).

**Rate Limiting**: Redis-based (1 req/sec per user key).

---

## API Endpoint Pattern (All Business Services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/create` | Create resource |
| GET | `/api/fetch?mobileNumber={}` | Fetch by mobile number |
| PUT | `/api/update` | Update resource |
| DELETE | `/api/delete?mobileNumber={}` | Delete by mobile number |

**Accounts only**: `GET /api/fetchCustomerDetails` - aggregated view (accounts + cards + loans via Feign clients)

---

## Code Conventions

### Package Structure
```
com.ggoutos.{service}/
├── {Service}Application.java      # Entry point (@EnableJpaAuditing, @EnableFeignClients)
├── controller/                    # REST controllers
├── service/                       # Service interfaces (I{Service}Service)
│   └── impl/                      # Service implementations ({Service}ServiceImpl)
├── entity/                        # JPA entities (extend BaseEntity)
├── mapper/                        # Static mappers (target-mutation pattern)
├── repository/                    # Spring Data JPA repositories
├── exception/                     # Custom exceptions + GlobalExceptionHandler
├── audit/                         # AuditAwareImpl + JpaAuditingConfiguration
└── constants/                     # Business constants
```

### Naming Conventions
- **Interfaces**: `I{Service}Service` (capital I prefix)
- **Implementations**: `{Service}ServiceImpl`
- **DTOs**: `{Entity}Dto` (in utils module, use `@Data`)
- **Mappers**: `{Entity}Mapper` (static methods, target-mutation)
- **Exceptions**: `{Resource}NotFoundException`, `{Entity}AlreadyExistsException`
- **Feign Clients**: `{Service}FeignClient` (in `service/client/`)
- **Functions**: `{Service}Functions` (Spring Cloud Function beans)

### Lombok Usage
```java
@Service
@RequiredArgsConstructor  // for final fields
public class AccountsServiceImpl implements IAccountsService { ... }

@Data  // for DTOs
public class AccountsDto { ... }

@Entity
@Getter @Setter @ToString @RequiredArgsConstructor
public class Customer extends BaseEntity { ... }
```

### Mapper Pattern (Static, Target-Mutation)
```java
public class AccountsMapper {
    static AccountsDto mapToAccountsDto(Accounts accounts, AccountsDto accountsDto) {
        accountsDto.setAccountNumber(accounts.getAccountNumber());
        // ... set other fields
        return accountsDto;
    }
    static Accounts mapToAccounts(AccountsDto accountsDto, Accounts accounts) { ... }
}
```

---

## Shared DTOs (utils module)

| DTO | Purpose |
|-----|---------|
| `CustomerDto` | Customer + account data for creation/update |
| `CustomerDetailsDto` | Aggregated view (accounts + cards + loans) |
| `AccountsDto` | Account data (accountNumber, accountType, branchAddress) |
| `CardsDto` | Card data (mobileNumber, cardNumber, cardType, limits) |
| `LoansDto` | Loan data (mobileNumber, loanNumber, loanType, balances) |
| `ResponseDto` | Standard success response (statusCode, statusMsg) |
| `ErrorResponseDto` | Standard error response (apiPath, errorCode, errorMessage, errorTime) |
| `AccountsMsgDto` | Event payload for async notifications (record: accountNumber, name, email, mobileNumber) |

---

## Inter-Service Communication

### Synchronous (Feign Clients)
```java
@FeignClient(name = "cards", fallback = CardsFallback.class)
public interface CardsFeignClient {
    @GetMapping(value = "/api/fetch", consumes = "application/json")
    CardsDto fetchCardDetails(
        @RequestHeader(name = "eazybank-correlation-id", required = true) String correlationId,
        @RequestParam String mobileNumber);
}
```

### Asynchronous (Spring Cloud Stream)
- **Accounts** publishes events via `StreamBridge.send("sendCommunication-out-0", accountsMsgDto)`
- **Message** service consumes from `emailsms-in-0` (destination: `send-communication`)
- **Message** processes via function composition: `email|sms`
- **Accounts** consumes completion events via `updateCommunication-in-0` (destination: `communication-sent`)
- Default binder: Kafka (can switch to RabbitMQ via `SPRING_CLOUD_STREAM_DEFAULT_BINDER`)

---

## Database

- **Per-service database pattern**: accountsdb (3307), loansdb (3308), cardsdb (3309)
- **MySQL LTS** with Flyway migrations (`V1__init_schema.sql`, `V2__communication_sw.sql`)
- **JPA**: `ddl-auto: none` (schema managed by Flyway)
- **ConfigServer** provides datasource credentials only (URL, username, password)

---

## Configuration

### ConfigServer
- Git backend (prod) / Native fallback (dev)
- Basic auth required (`CONFIG_SERVER_USER`, `CONFIG_SERVER_PASSWORD`)
- Serves only datasource configs; all other config is local to each service

### Profiles
- `default` - Development (localhost databases)
- `prod` - Production (Docker service name databases)

### Environment Files
- `.env` - Base config (in git)
- `.env.local` - Dev secrets (git-ignored)
- `.env.prod` - Prod secrets (git-ignored)

---

## Observability

- **OpenTelemetry**: Auto-instrumentation via javaagent (traces to Tempo)
- **Prometheus**: Metrics from `/actuator/prometheus` endpoint
- **Loki**: Log aggregation via Alloy (Docker container logs)
- **Grafana**: Unified dashboards (anonymous admin access for dev)
- **Log Pattern**: `"%5p [${spring.application.name},%X{trace_id},%X{span_id}]"`

---

## Resilience Patterns (Resilience4j)

| Pattern | Config |
|---------|--------|
| Circuit Breaker | slidingWindow: 10, failureThreshold: 50%, waitDuration: 10s |
| Retry | maxAttempts: 3, exponential backoff (multiplier: 2) |
| Time Limiter | timeout: 4s |
| Rate Limiter | 10 req/sec, timeout: 1s |

---

## Build Commands

```bash
# Build all
mvn clean install

# Run service
mvn spring-boot:run

# Docker images
mvn compile jib:dockerBuild          # → ggoutos/{service}:jib-k8s
mvn spring-boot:build-image          # → ggoutos/{service}:spring
mvn -Pnative native:compile          # GraalVM native

# Docker Compose
cd .docker && docker compose up --build
cd .docker && docker compose --env-file .env.prod up  # Prod
```

---

## Test Patterns

- **Context Load**: `@SpringBootTest` with `contextLoads()` + `testMainMethod()`
- **Controller**: `@WebMvcTest(Controller.class)` with `@MockitoBean` for services
- **Service**: `@ExtendWith(MockitoExtension.class)` with `@Mock` + `@InjectMocks`
- **Repository**: `@DataJpaTest`
- **Exception Handler**: `@WebMvcTest(GlobalExceptionHandler.class)`
- **Organization**: `@Nested` classes + `@DisplayName` annotations
- **Coverage**: JaCoCo enforces 80% minimum

---

## Key Files to Reference

| Purpose | File |
|---------|------|
| Parent POM | `pom.xml` |
| Shared DTOs | `utils/src/main/java/com/ggoutos/utils/dto/` |
| Gateway Routes | `gatewayserver/src/main/java/com/ggoutos/gatewayserver/config/RouteConfig.java` |
| Gateway Security | `gatewayserver/src/main/java/com/ggoutos/gatewayserver/config/SecurityConfig.java` |
| Docker Compose | `.docker/docker-compose.yml` |
| Service Config | `{service}/src/main/resources/application.yml` |
| Config Files | `.config/` directory (served by ConfigServer) |

---

*Generated: 2026-04-30 | For AI context reference only*
