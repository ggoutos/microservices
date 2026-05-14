# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Build All Services
```bash
mvn clean install  # Build all modules (utils, eurekaserver, configserver, gatewayserver, accounts, loans, cards, message)
```

### Run Tests
```bash
mvn test                    # Run all tests across modules
mvn test -pl accounts       # Run tests for a single service
mvn test -pl accounts -Dtest=AccountsControllerTest  # Run a single test class
```
JaCoCo enforces 80% minimum code coverage (configured in parent POM).

### Run Services Locally
```bash
cd accounts && mvn spring-boot:run  # Requires infrastructure (Eureka, Config Server, databases) running first
```

### Docker Images
```bash
mvn compile jib:dockerBuild       # Jib (recommended, no Docker daemon needed) → ggoutos/{service}:jib
mvn spring-boot:build-image       # Buildpacks → ggoutos/{service}:spring
mvn -Pnative native:compile      # GraalVM native image (requires GraalVM 25+)
```

### Full Stack (Docker Compose)
```bash
cd .docker
docker compose up --build               # Dev (uses .env)
docker compose --env-file .env.prod up  # Production
```
Startup order enforced via `depends_on: service_healthy`: Monitoring → Keycloak → RabbitMQ → Redis → MySQL → Eureka → Config Server → Business Services → Gateway.

## Architecture Overview

### Project Structure
Multi-module Maven project (parent POM: `com.ggoutos:microservices`) using Java 25, Spring Boot 4.0.5, Spring Cloud 2025.1.1.

| Module | Purpose | Port |
|--------|---------|------|
| `utils` | Shared JAR (DTOs, mappers) - not a Spring Boot app | — |
| `eurekaserver` | Service discovery (Netflix Eureka) | 8070 |
| `configserver` | Centralized config (Git backend prod, native fallback dev) | 8071 |
| `gatewayserver` | API gateway (WebFlux reactive, OAuth2 JWT, Resilience4j) | 8072 |
| `accounts` | Accounts + customer aggregation, Feign clients to cards/loans | 8080 |
| `loans` | Loan management | 8090 |
| `cards` | Card management | 9000 |
| `message` | Event-driven email/SMS notifications (Spring Cloud Stream) | 9010 |

### Key Interaction Patterns
- **External Traffic**: Client → Gateway (validates JWT via Keycloak, adds correlation ID) → Path rewrite `/goutos/bank/{service}/**` → Forwards to `lb://{SERVICE}` via Eureka load-balancing
- **Inter-Service Calls**: Accounts uses Feign clients to Cards/Loans; all `/api/fetch` endpoints require `eazybank-correlation-id` header (propagated via gateway filters `RequestTraceFilter` → `ResponseTraceFilter`)
- **Event-Driven Flow**: Account creation → Accounts publishes `AccountsMsgDto` to `send-communication` topic (RabbitMQ/Kafka via StreamBridge) → Message service processes via `email()` → `sms()` function pipeline (Spring Cloud Function composition) → Publishes to `communication-sent` → Accounts consumes and updates `communicationSw`
- **Config Refresh**: RabbitMQ message bus propagates config changes from Config Server to all services

### Database per Service
Separate MySQL instances with Flyway migrations: accountsdb (3307), loansdb (3308), cardsdb (3309). No shared databases between business services.

### Observability Pipeline
All services auto-instrumented with OpenTelemetry javaagent:
- Traces → Tempo (port 4318)
- Metrics → Prometheus (port 9090) → Grafana (port 3000)
- Logs → Loki (port 3100) → Grafana

## API Conventions
All business services expose standard endpoints (require OAuth2 JWT via gateway):
- `POST /api/create?mobileNumber={}`
- `GET /api/fetch?mobileNumber={}` (requires `eazybank-correlation-id` header)
- `PUT /api/update`
- `DELETE /api/delete?mobileNumber={}`

## Code Style
- **Interfaces**: `I{Service}Service` (e.g., `IAccountsService`), implementations in `impl/` subpackage
- **DTOs**: In `utils` module, use Lombok `@Data` + OpenAPI `@Schema`
- **Mappers**: Static target-mutation pattern (e.g., `AccountsMapper.mapToAccountsDto(accounts, dto)`)
- **Tests**: JUnit 5 with `@Nested` for grouping, `@DisplayName` for descriptions, `@WebMvcTest` for controllers
- **Entities**: Extend `BaseEntity` (auditing fields), use Lombok `@Getter @Setter @ToString @RequiredArgsConstructor`

## Important Notes
- Config Server only serves datasource credentials; other config remains in local `application.yml` files
- Gateway server does not use Config Server (fully self-contained)
- Message service default binder is Kafka, set `SPRING_CLOUD_STREAM_DEFAULT_BINDER=rabbit` to switch to RabbitMQ
- Sensitive env vars (`CONFIG_SERVER_PASSWORD`, `GIT_TOKEN`, `MYSQL_ROOT_PASSWORD`) go in `.env.local` (dev) or `.env.prod` (prod) — both git-ignored
- Docker images use `ggoutos/{service}:jib` naming (Jib) or `ggoutos/{service}:spring` (Buildpacks)
- Kubernetes manifests and Helm charts in `.docker/helm/` and `.docker/k8s-generated/`
