# Code Review — EazyBank Microservices

> Full-project review covering architecture, security, code quality, resilience & operations,
> infrastructure & delivery. Every finding cites file-level evidence from the actual source —
> no claims taken from README/docs. Reviewed at commit `049a69e` on 2026-06-11.
>
> **Scope:** all 8 Maven modules (75 main classes, 35 test classes), `.docker/` (compose,
> Kompose-generated K8s manifests, 20+ Helm charts), `.config/` shared configs, build setup.

---

## 1. Executive Summary

This is a well-studied Spring Cloud microservices implementation with genuinely strong
foundations: full observability stack (OTel, Prometheus, Loki, Tempo, Grafana), Resilience4j
patterns throughout, Flyway migrations, an enforced 80 % JaCoCo coverage gate, constructor
injection everywhere, and three deployment targets (Compose, K8s, Helm). **It is not
production-ready.** The gap is concentrated in security (live secrets committed to git, an
unauthenticated read path to customer PII, no auth on the services themselves) and in the
failure-handling seams between components (events that can be silently lost, fallbacks that
return `null`, three contradictory gateway timeouts).

**Top 5 issues:**

1. **Live credentials are committed to the repository** — `.docker/.env.prod` and
   `.docker/k8s-generated/env-prod-configmap.yaml` contain a GitHub PAT, a Gmail app password,
   the config-server password and the config encryption key. Rotate immediately, purge history.
2. **Every GET request bypasses authentication at the gateway** (`SecurityConfig.java:23`) —
   anyone can fetch full customer PII by enumerating 10-digit mobile numbers.
3. **Downstream services have no security at all** — no Spring Security dependency in
   accounts/cards/loans; their ports are reachable directly, so gateway auth is decorative.
4. **Event publishing can silently lose messages** — `StreamBridge.send()` is fire-and-forget,
   outside any transaction, with no outbox, no producer retries, no DLQ.
5. **No CI/CD exists** — the JaCoCo gate, tests, and security posture are never enforced
   anywhere; builds also require a locally installed JDK 25 (no wrapper/toolchain — verified:
   compilation fails on JDK 21 with `release version 25 not supported`).

**Verdict:** strong learning/portfolio architecture, B-grade code, D-grade security posture.
With the Phase-1 roadmap below (~2–3 weeks of work) it becomes a credible production candidate.

---

## 2. Scorecard

| Dimension | Grade | One-line rationale |
|---|---|---|
| Architecture & design | **B−** | Sound patterns, but Eureka/Config Server are redundant on K8s, `utils` DTO coupling, sync aggregation in `accounts` |
| Security | **D** | Secrets in git, open GET path to PII, unauthenticated services, unrestricted `/actuator/shutdown` |
| Code quality | **B** | Clean, documented, validated — but ~400 lines duplicated across 3 services and hand-written mappers |
| Resilience | **C+** | All the right patterns present, several misconfigured (timeouts, fallbacks, rate limiter) |
| Messaging reliability | **C−** | No outbox, no DLQ, no producer retries, no partition keys, no idempotent consumers |
| Observability | **B+** | Excellent intent and stack; fragile agent wiring tied to one image layout |
| Infrastructure & K8s | **C−** | `:latest` everywhere, secrets in ConfigMaps, internal services on LoadBalancers, broken probes |
| Testing | **B** | Real assertions, good structure; H2-only, zero integration/contract tests |
| CI/CD & supply chain | **F** | Nothing exists — no pipeline, no scanning, no SBOM, no wrapper |
| Documentation | **B** | ARCHITECTURE.md/AGENTS.md exist; README is 124 KB and drifts from code (e.g. `/api/v1` examples vs actual `/api`) |

---

## 3. Findings

Severity legend: **Critical** = exploitable or data-loss now · **High** = will bite in production
· **Medium** = correctness/maintainability risk · **Low** = hygiene.

### 3.1 Critical

**C1 — Live secrets committed to git**
`.docker/.env.prod` (git-tracked, verified via `git ls-files`) and
`.docker/k8s-generated/env-prod-configmap.yaml` contain in plaintext: a GitHub personal access
token, a Gmail SMTP app password, `CONFIG_SERVER_PASSWORD`, `ENCRYPTION_KEY`, and
`MYSQL_ROOT_PASSWORD`. `.gitignore` only excludes `.env.local`.
*Impact:* repo access ⇒ write access to the config git repo, ability to decrypt all encrypted
config properties, email account takeover, root DB access.
*Fix:* rotate **all** of these today; purge history (`git filter-repo` or BFG); add `.env*`
(except a committed `.env.example`) to `.gitignore`; move to Kubernetes Secrets backed by an
external store (External Secrets Operator + Vault/AWS Secrets Manager).

**C2 — All GET requests are unauthenticated at the gateway**
`gatewayserver/.../SecurityConfig.java:23` — `.pathMatchers(HttpMethod.GET).permitAll()`.
Combined with `GET /goutos/bank/accounts/api/fetch?mobileNumber=…` this allows unauthenticated
retrieval of name, email, mobile number, and account details for any customer; 10-digit mobile
numbers are enumerable.
*Fix:* require authentication for all business routes; keep `permitAll` only for
`/actuator/health/**` and the fallback route. Add object-level authorization (a user may only
fetch *their own* data — match JWT claim against the requested resource).

**C3 — Downstream services have zero security**
`accounts/pom.xml`, `cards/pom.xml`, `loans/pom.xml` contain no Spring Security /
OAuth2-resource-server dependency; `docker-compose.yml` and the K8s Services expose their ports.
Anyone on the network calls `accounts:8080/api/fetch` directly — the gateway is a suggestion,
not a boundary.
*Fix:* add `spring-boot-starter-oauth2-resource-server` to each service and validate the same
Keycloak JWTs (zero-trust); stop publishing service ports; longer-term add mTLS via a mesh.

**C4 — Unauthenticated remote shutdown of every service**
Every `application.yml` exposes `shutdown` with `access: unrestricted`
(e.g. `accounts/src/main/resources/application.yml:18,28-29`). One `POST /actuator/shutdown`
kills any service.
*Fix:* remove `shutdown` from the exposure list entirely; rely on the orchestrator for
lifecycle. Same review for `refresh`/`busrefresh` (state-changing, currently anonymous).

**C5 — Events can be silently lost (no outbox, no transaction)**
`AccountsServiceImpl.createAccount()` (`accounts/.../AccountsServiceImpl.java:37-55`): two
repository saves with **no `@Transactional`**, then `streamBridge.send()` fire-and-forget —
the boolean result is only logged. No producer retries/idempotence configured, so if Kafka is
unavailable the welcome-communication event vanishes while the account exists.
*Fix:* shortest path — Spring Modulith's event publication registry or a hand-rolled outbox
table written in the same transaction, drained by a publisher. Also wrap
create/update/delete service methods in `@Transactional` (today `updateAccount` performs four
sequential writes that can partially apply).

**C6 — Feign fallbacks return `null` into the response**
`CardsFallback.java:11` / `LoansFallback.java:11` return `null`;
`CustomersServiceImpl.java:43-44` assigns the results into `CustomerDetailsDto` with no null
handling. Callers cannot distinguish "no loan" from "loans service down", and downstream
consumers NPE.
*Fix:* return an explicit degraded representation (empty DTO + status field, or omit the field
and document partial responses), and surface partial-failure via the API contract.

### 3.2 High

**H1 — JWT validated by signature only.** Gateway config uses `jwk-set-uri` alone
(`gatewayserver application.yml:53`) against the Keycloak **master** realm. No
issuer validation; master realm should never serve application tokens.
*Fix:* create a dedicated realm; use `issuer-uri` (enables iss validation + discovery); add
audience validation.

**H2 — Internal infrastructure exposed publicly.** `kompose.service.type: LoadBalancer` on
Keycloak, Prometheus, Grafana, Kafka, RabbitMQ, Redis (compose labels → generated Services).
Plus: Grafana anonymous **Admin** (`docker-compose.yml:153-154`), Keycloak `admin/admin`,
MinIO `supersecret`, Redis with no auth.
*Fix:* `ClusterIP` for everything except the gateway; real credentials via secrets; Grafana
anonymous off or Viewer.

**H3 — No K8s hardening.** Secrets in ConfigMaps instead of Secrets; no `securityContext`
(every container can run as root — the `accounts/Dockerfile` and the Jib config both omit a
non-root user); no NetworkPolicies; no resource requests/limits on infra pods.
*Fix:* non-root user in images (`<container><user>1000</user></container>` for Jib), baseline
`securityContext`, default-deny NetworkPolicy with explicit flows, requests/limits everywhere.

**H4 — Three contradictory gateway timeouts.** Route metadata `RESPONSE_TIMEOUT_ATTR = 1000 ms`
(`RouteConfig.java:82`) vs HTTP client `response-timeout: 2s` (yml:64) vs TimeLimiter 4 s
(yml:78 and `RouteConfig.java:99`). The 1 s per-route metadata wins for the proxy call, making
the 4 s time-limiter dead config and SLAs unpredictable. The route retry (3 attempts,
`RouteConfig.java:76-78`) then multiplies load on an already-slow service by 4×.
*Fix:* pick one timeout chain (e.g. connect 1 s, response 3 s, TimeLimiter 4 s as the outer
bound), delete the rest; cap retries at 1–2 with jitter, GET-only (already GET-only — good).

**H5 — Rate limiting is trivially bypassable and a SPOF.**
`RateLimitConfig.java:18-22` keys the limiter on the client-supplied `user` header (rotate the
header ⇒ fresh bucket; omit it ⇒ all anonymous users share one bucket), and
`new RedisRateLimiter(1, 1, 1)` allows ~1 req/s globally per key. Redis down ⇒ behavior
degrades for all routes.
*Fix:* key on the authenticated JWT principal (fall back to client IP from `X-Forwarded-For`
set by your own edge); set realistic rates; configure `deny-empty-key` deliberately and decide
fail-open vs fail-closed for Redis outages.

**H6 — Weak, collision-prone ID generation; no idempotency.**
`new Random()` per call for account/card/loan numbers
(`AccountsServiceImpl.java:82`, `CardsServiceImpl.java:87`, `LoansServiceImpl.java:41`);
horizontal scaling raises collision odds; a collision surfaces as a 500 on the unique
constraint. POST `/create` has no idempotency key, so client retries create duplicates.
*Fix:* DB sequence or TSID/UUIDv7 for identifiers; `Idempotency-Key` header support on creates.

**H7 — Messaging has no failure path.** No DLQ (`enable-dlq` absent), no
`max-attempts` tuning, no producer `retries`/`enable.idempotence`, no
`message-key-expression` (so per-account ordering is not guaranteed), consumer functions have
no error handling (`MessageFunctions.java`, `AccountsFunctions.java`).
*Fix:* DLQ per binding, idempotent producer, key by `accountNumber`, idempotent consumer
(communication-status update is naturally idempotent — make that explicit).

**H8 — Probe and shutdown gaps in K8s.** Kompose-generated Deployments have a malformed
shell-string `livenessProbe` and **no readinessProbe** (e.g.
`k8s-generated/accounts-deployment.yaml:30-37`); the Helm common template defines no probes at
all; no `server.shutdown: graceful` in any service.
*Fix:* `httpGet` probes on `/actuator/health/liveness` + `/readiness` (they're already enabled
in the apps), `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s`,
`maxUnavailable: 0` rolling updates.

**H9 — No CI/CD, no supply-chain controls.** No `.github/workflows`, no Maven wrapper
(verified: build impossible on stock JDK 21), no dependency scanning, no image scanning, no
SBOM. The 80 % coverage gate only fires if a developer happens to run `mvn verify`.
*Fix:* GitHub Actions: build → test → JaCoCo check → OWASP Dependency-Check → image build →
Trivy scan → push (OIDC, no long-lived creds). Add `mvnw` and CycloneDX SBOM.

**H10 — Image strategy chaos.** Three build paths (Buildpacks `:spring`, Jib `:jib`, custom
`accounts/Dockerfile`) while compose expects `ggoutos/*:${IMAGE_TAG}`; all infra images are
`:latest`; **Helm values still reference the upstream course images `eazybytes/*:s14`**
(`helm/eazybank-services/*/values.yaml:12`) — a Helm deploy today would run someone else's
containers.
*Fix:* standardize on one build (Jib recommended), pin every image by tag+digest, fix Helm
values, add Renovate for bumps.

**H11 — Message infrastructure is single-copy.** Kafka: one broker, RF=1, offsets RF=1
(`docker-compose.yml:249-281`) — any restart loses data. RabbitMQ volume is commented out
(`docker-compose.yml:235-236`).
*Fix:* 3 brokers / RF=3 / `min.insync.replicas=2` anywhere durability matters; re-enable the
Rabbit volume.

**H12 — ~400+ lines duplicated across accounts/cards/loans.** `BaseEntity`, `AuditAwareImpl`,
`JpaAuditingConfiguration`, `GlobalExceptionHandler`, `ResourceNotFoundException`,
`*AlreadyExistsException`, constants — three near-identical copies each, while a shared
`utils` module already exists.
*Fix:* extract a `common-web`/`common-persistence` starter module (separate from the DTO
artifact to keep coupling deliberate).

**H13 — Config server is a hard startup dependency.**
`config.import: "configserver:…"` without `optional:` and no retry config — config server down
⇒ nothing starts.
*Fix:* `spring.config.import: "optional:configserver:…"` + `spring.cloud.config.fail-fast=true`
with `spring-retry` where strictness is wanted; decide per environment.

### 3.3 Medium

| # | Finding | Evidence | Fix |
|---|---|---|---|
| M1 | `show-sql: true` logs SQL (and PII parameters end up in Loki) | all three service ymls | off in prod; use datasource-proxy/observability instead |
| M2 | Generic exception handler returns raw `exception.getMessage()` to clients | `GlobalExceptionHandler` ×3 | generic message + correlation ID; log details server-side |
| M3 | JPA auditor hardcoded (`"ACCOUNTS_MS"`) — audit trail can't attribute users | `AuditAwareImpl.java:18` ×3 | resolve from `SecurityContext`/JWT once services authenticate |
| M4 | `updateAccount`/`deleteAccount` multi-write without `@Transactional` | `AccountsServiceImpl.java:112-144` | annotate service methods |
| M5 | Hand-written static mappers, no null-safety | 4 mapper classes | MapStruct (compile-time, generated, null-aware) |
| M6 | Tests run on H2 while prod is MySQL — dialect drift; zero integration tests | `*/src/test/resources/application.yml` | Testcontainers MySQL + Kafka; `@ServiceConnection` |
| M7 | OTel agent path `-javaagent:/app/libs/opentelemetry-javaagent-*.jar` only matches the **Jib** layout — buildpack/Dockerfile images silently lose tracing (and `%X{trace_id}` in logs goes empty) | `.env` / `env-configmap.yaml:7` vs three image layouts | one image strategy; or switch to Micrometer Tracing + OTLP starter (no agent) |
| M8 | Eureka + Config Server running **inside** K8s duplicate platform features | helm charts for both | K8s DNS + ConfigMaps/ESO (or Spring Cloud Kubernetes) on K8s; keep Eureka for compose-only |
| M9 | `spring-boot-devtools` declared `runtime` in the **parent**, inherited by everything | `pom.xml:76-81` | remove or confine; verify excluded from images |
| M10 | Config typos: `SPRING_CLOUD_STREAM_DEAFULT_BINDER` (×2), `com.goutos.gateway` logger (never matches `com.ggoutos`), `circuitbreakereventst` endpoint; `SecurityConfig` duplicates the `DNS_PREFIX` paths as string literals | message/accounts ymls; gateway yml:7,15; `SecurityConfig.java:24-26` | fix names; share the prefix constant |
| M11 | Prometheus uses static targets and 5 s scrape | `.docker/prometheus/prometheus.yml` | kubernetes_sd / ServiceMonitor; 15–30 s |
| M12 | Helm hygiene: all charts frozen at `0.1.0`, no `Chart.lock`, common chart typed `application` not `library`, PVCs 100 Mi, no rollout strategy | `.docker/helm/**`, `k8s-generated/*pvc*` | version bumps, lock deps, fix type, size PVCs |
| M13 | REST design: verbs in paths (`/create`, `/fetch`), `417 Expectation Failed` misused for failed updates, no URI versioning, boolean service returns | controllers ×3 | resource-oriented paths `/api/v1/accounts`, 404/409/204 semantics |
| M14 | Model inconsistencies: plural entity names (`Accounts`), `AccountsMsgDto` is a record while every other DTO is a `@Data` class, record lacks validation | entities, `utils/dto` | singular entities; one DTO convention |

### 3.4 Low

- `guest:guest` RabbitMQ fallback defaults in ymls; Eureka dashboard unauthenticated; no access logging at the gateway.
- No `.dockerignore`, no `Makefile`/Taskfile, no `mvnw` (also listed under H9), 124 KB README that drifts from code.
- Magic numbers in ID generation; stray indentation (`CustomersServiceImpl.java:20`); commented-out code (`AccountsController.java:63` references a nonexistent `username` variable; dead dependency blocks in `accounts/pom.xml:86-93`).
- CSRF disabled at the gateway (`SecurityConfig.java:31`) — **acceptable** for a stateless,
  token-based API (no cookies), but document it as a deliberate decision.

---

## 4. What's Done Well

- **Observability-first mindset** — OTel + Prometheus + Loki + Tempo + Grafana wired from day 1, trace/span IDs in the log pattern, correlation-ID propagation through gateway filters and Feign calls.
- **Resilience patterns actually configured, with comments explaining each knob** — circuit breakers, retries with backoff, time limiters, rate limiters at both gateway and service level.
- **Quality gates** — 80 % JaCoCo line-coverage check; tests have real assertions (`@Nested`, `assertThatThrownBy`, MockMvc), not coverage padding.
- **Clean code basics** — constructor injection only, Bean Validation on DTOs (`@Pattern`, `@Email`…), OpenAPI annotations, `ddl-auto: none` + Flyway, manual `equals/hashCode` on entities handling Hibernate proxies (a pitfall most projects miss).
- **Container craft** — the accounts Dockerfile does multi-stage jlink-minimized JRE plus a GraalVM native stage; compose healthchecks gate startup ordering properly.
- **Helm library-chart pattern** — `eazybank-common` reused by six service charts reduces template duplication.

---

## 5. Prioritized Roadmap

**Phase 0 — today (stop the bleeding)**
1. Rotate GitHub PAT, Gmail app password, config-server password, encryption key, DB passwords; purge `.env.prod` & `env-prod-configmap.yaml` from git history; fix `.gitignore`.
2. Remove `shutdown` from actuator exposure (all services).
3. Require auth on business GET routes at the gateway.

**Phase 1 — this sprint (security boundary + correctness)**
4. OAuth2 resource server in accounts/cards/loans; dedicated Keycloak realm; `issuer-uri`.
5. Fix Feign fallbacks (no nulls) and add `@Transactional` to multi-write service methods.
6. Consolidate gateway timeouts; fix rate-limiter key + rates.
7. CI pipeline (build, test, JaCoCo, Trivy, dependency check) + `mvnw`.

**Phase 2 — next sprint (reliability)**
8. Outbox/Spring Modulith events; DLQs; idempotent producer + consumers; partition keys.
9. Readiness/liveness `httpGet` probes, graceful shutdown, rolling-update strategy, resource limits, securityContext, NetworkPolicies.
10. Secrets → K8s Secrets + External Secrets Operator; ClusterIP everything internal.
11. Pin all images; one build strategy; fix `eazybytes/*` Helm values.

**Phase 3 — next month (platform maturity)**
12. Extract shared `common-*` modules; MapStruct; Testcontainers integration tests; contract tests.
13. Kafka 3-broker/RF-3 (or managed); ID-generation strategy; idempotency keys.
14. GitOps (ArgoCD + Helmfile), drop `k8s-generated/` as a deployment path, environments via values overlays.

---

## 6. Custom Code → Industry-Standard Replacements

| Custom code in repo | Replace with | Why |
|---|---|---|
| Hand-written static mappers (`AccountsMapper`, `CustomerMapper`, `CardsMapper`, `LoansMapper`) | **MapStruct** | Compile-time generated, null-safe, fails the build on unmapped fields |
| Correlation-ID plumbing (`FilterUtility`, `RequestTraceFilter`, `ResponseTraceFilter`, manual header pass-through in Feign + controllers) | **Micrometer Tracing / W3C `traceparent` context propagation** | The OTel stack already propagates trace context end-to-end; the custom header duplicates it with manual code in every signature |
| `KeycloakRoleConverter` | Configured `JwtGrantedAuthoritiesConverter` (`authoritiesClaimName=realm_access.roles`, `authorityPrefix=ROLE_`) or **spring-addons-starter-oidc** | Same behavior, no custom unchecked casts/NPE path |
| `new Random()` ID generation | **DB sequences**, **TSID** (hypersistence-tsid) or **UUIDv7** | Collision-free, sortable, multi-instance safe |
| Hardcoded `AuditAwareImpl` ("ACCOUNTS_MS") | `SecurityContext`-based `AuditorAware` | Real user attribution for compliance |
| Per-service `GlobalExceptionHandler` + `ErrorResponseDto` | **RFC 9457 Problem Details** (`spring.mvc.problemdetails.enabled=true`, `ProblemDetail`) | Standard error contract, less code |
| Hand-rolled Kafka/Keycloak/Grafana/Prometheus Helm charts | **Bitnami Kafka**, **Keycloak Operator/codecentric chart**, **kube-prometheus-stack**, **grafana/loki** charts as `Chart.yaml` dependencies | Maintained, hardened, HA-capable defaults |
| `k8s-generated/` Kompose manifests | Delete; Helm (or Kustomize) as the single K8s source of truth | Two divergent manifest sets guarantee drift |
| Secrets via `env-*-configmap.yaml` | **External Secrets Operator** (+ Vault/AWS Secrets Manager) or **Sealed Secrets** | Secrets out of git, rotated centrally |
| Three image-build paths | One: **Jib** (or Buildpacks) for all modules | Reproducibility; fixes the OTel agent-path coupling |
| Eureka + Config Server **on Kubernetes** | K8s Service DNS + ConfigMaps/ESO, or **Spring Cloud Kubernetes** | Removes two stateful runtime dependencies the platform already provides |
| Shared `utils` DTO jar as the inter-service contract | **OpenAPI-generated clients** per service (openapi-generator) and/or **Spring Cloud Contract** | Versioned, consumer-driven contracts instead of lockstep jar upgrades |

---

## 7. Recommended Spring Modules & Libraries

| Library / module | Problem it solves here |
|---|---|
| `spring-boot-starter-oauth2-resource-server` (in each service) | Closes the unauthenticated-direct-access hole (C3); enables real `AuditorAware` |
| **Spring Modulith** (`spring-modulith-events-kafka`) | Transactional outbox / event publication registry — fixes the dual-write event loss (C5) with minimal code |
| **Spring Cloud Stream DLQ** config + `spring-kafka` `@RetryableTopic` | Failure path for consumers (H7): bounded retries, dead-letter topics |
| **Testcontainers** (`spring-boot-testcontainers`, `@ServiceConnection`) | Integration tests against real MySQL/Kafka/Keycloak instead of H2-only (M6) |
| **MapStruct** | Replaces 4 hand-written mappers (M5) |
| **Spring Cloud Kubernetes** | Discovery + config from the platform when on K8s (M8) |
| **Spring Cloud Contract** | Consumer-driven contracts for accounts→cards/loans Feign calls; decouples from the shared DTO jar |
| **springdoc** (already present) + **openapi-generator-maven-plugin** | Generate typed Feign clients from each service's spec |
| **hypersistence-tsid** / `java.util.UUID` v7 | Collision-free identifiers (H6) |
| **ShedLock** | Safe scheduled jobs once outbox pollers/cleanup tasks exist with >1 replica |
| **ArchUnit** | Enforce layering & no-cross-module-imports as tests (guards the dedup refactor) |
| **Resilience4j Bulkhead** module | Already on the classpath family — isolate Feign thread usage so a slow cards call can't starve accounts |
| `spring.threads.virtual.enabled=true` | Java 25 + Boot 4: virtual threads for the blocking MVC services — higher concurrency, no code change |
| **CycloneDX Maven plugin** + **OWASP dependency-check** | SBOM + CVE gate in CI (H9) |
| **Spring Cloud Gateway `TokenRelay` filter** | Forward the validated JWT downstream once services become resource servers |

---

## 8. Future Features — From Good Design to Production-Grade Cloud Native

**Security & zero trust**
- Dedicated Keycloak realm per environment; audience checks; token exchange for service-to-service identity.
- Service mesh (Istio Ambient or Linkerd) for mTLS, retries-at-mesh, and authz policy; or strict NetworkPolicies if a mesh is overkill.
- Policy-as-code admission control (Kyverno/Gatekeeper): no-root, no-latest, required probes/limits.
- Fine-grained authorization (per-account ownership) via Keycloak Authorization Services or OpenFGA.

**Delivery & GitOps**
- ArgoCD ApplicationSets + Helmfile; environments as values overlays; PR-preview environments.
- Progressive delivery with Argo Rollouts (canary + automatic rollback on SLO burn).
- Supply chain: image signing (cosign), SLSA provenance, Renovate for dependency currency.

**Data & events**
- Debezium CDC outbox as the system grows past Modulith's registry.
- Schema Registry (Avro/Protobuf) + schema-evolution rules for `send-communication` events.
- Saga orchestration (Temporal, or choreography + compensation) once multi-service writes appear (e.g. "close customer" spanning accounts+cards+loans).
- Read replicas + connection pooling proxy; cache-aside Redis for hot reads (`fetch by mobile`).

**Operations & SRE**
- SLOs with error budgets in Grafana; alert rules + runbooks; on-call rotation docs.
- Load testing (k6/Gatling) and chaos experiments (resilience configs are currently unproven hypotheses).
- Velero backups, tested restore; defined RTO/RPO per data store.
- Cost visibility (Kubecost/OpenCost) once on shared clusters.

**Performance**
- GraalVM native images (scaffolding already exists) or CRaC for sub-second cold starts.
- Virtual threads (see §7) and HTTP interface clients to retire blocking Feign threads.

**Developer experience**
- Backstage (or simple TechDocs) service catalog; ADRs for the decisions in §6.
- `Makefile`/Taskfile: `make up`, `make test`, `make deploy-dev`; devcontainer with JDK 25.

---

## 9. AWS Deployment Plan

Target architecture: **EKS for the services you own; managed AWS services for everything you don't need to operate.**

**Phase A — Foundations (week 1)**
- AWS Organizations: separate `dev` / `staging` / `prod` accounts; SSO via IAM Identity Center.
- Terraform (or CDK): VPC across 3 AZs — public subnets (ALB/NAT only), private app subnets, isolated data subnets; VPC endpoints for ECR/S3/Secrets Manager.
- ECR repos per service with scan-on-push; GitHub Actions → AWS via OIDC (no static keys).

**Phase B — Replace self-hosted infra with managed services (week 2–3)**

| Current (self-hosted) | AWS replacement | Notes |
|---|---|---|
| 3× MySQL containers | **Aurora MySQL** (one cluster, schema-per-service, or 3 small RDS instances) Multi-AZ + **RDS Proxy** | Flyway keeps running migrations as-is |
| Kafka (single broker) | **Amazon MSK** (3 brokers, RF=3) or MSK Serverless for dev | Spring Cloud Stream needs only broker/IAM auth config |
| RabbitMQ | **Amazon MQ** — or consolidate the bus refresh onto Kafka and drop Rabbit entirely | one less broker to run |
| Redis | **ElastiCache for Redis** (cluster mode off, Multi-AZ) | gateway rate limiter unchanged |
| Keycloak | Keycloak on EKS backed by Aurora, **or Amazon Cognito** if its feature set suffices | Cognito = less ops, less flexibility |
| Config Server + private git config repo | **AWS AppConfig / Parameter Store + Secrets Manager** via Spring Cloud AWS, or keep Config Server initially | staged migration is fine |
| Eureka | **Drop on EKS** — K8s DNS + (optionally) AWS Cloud Map | gateway `lb://` URIs become `http://service.namespace` |
| MinIO (Loki storage) | **S3** | Loki natively supports S3 |
| Prometheus/Grafana/Loki/Tempo | **Amazon Managed Prometheus + Managed Grafana**, Loki/Tempo on EKS with S3, collected via **ADOT**; CloudWatch for control-plane logs | keeps the Grafana experience |

**Phase C — Compute & traffic (week 3–4)**
- **EKS** with managed node groups + **Karpenter** (Spot for stateless services); Fargate optional for the gateway.
- **AWS Load Balancer Controller**: internet-facing ALB → Spring Cloud Gateway only; **AWS WAF** (rate rules, geo, common rule sets) in front; ACM TLS; Route 53 DNS.
- All business services on ClusterIP behind the gateway; **IRSA** for pod→AWS permissions (S3, Secrets Manager, MSK IAM auth).
- External Secrets Operator syncing from **Secrets Manager**; KMS CMKs for RDS/MSK/EBS/S3 encryption.

**Phase D — Pipeline & operations (week 4+)**
- GitHub Actions: `mvn verify` (JaCoCo gate) → Jib build/push to ECR → Trivy + Inspector scan → ArgoCD (running on EKS) syncs Helm releases per environment.
- HPA on RPS/CPU; PodDisruptionBudgets; `topologySpreadConstraints` across AZs.
- GuardDuty, Security Hub, CloudTrail org-trail enabled from day 1.
- Backups: automated RDS snapshots + Velero→S3 for cluster state; game-day a restore.
- Cost guardrails: budgets + alerts; Aurora Serverless v2 and MSK Serverless in dev to scale to zero-ish.

**Pragmatic sizing note:** for a dev/portfolio footprint, a single small EKS cluster +
1× Aurora Serverless v2 + MSK Serverless + ElastiCache t4g.micro keeps the bill modest; the
architecture above scales the same shape to production traffic.

---

## 10. Methodology

Five review passes (architecture by lead reviewer; security, code quality, resilience/ops,
infrastructure as parallel deep-dives), each reading source files directly. Key claims were
re-verified against the tree before publication (git-tracked secrets via `git ls-files`,
Helm image drift via grep, rate-limiter and service-layer code re-read). Build verification
was attempted and fails on JDK 21 (`release version 25 not supported`) — the JaCoCo gate and
test suite could therefore not be independently executed in this environment; test quality was
assessed by reading the test sources.
