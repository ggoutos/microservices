# Istio Integration Architecture for EazyBank Microservices Platform

> **Comprehensive design document** for integrating Istio service mesh with the EazyBank microservices platform running on Kubernetes.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Architecture Analysis](#2-current-architecture-analysis)
3. [Istio Installation Strategy](#3-istio-installation-strategy)
4. [Sidecar Injection Strategy](#4-sidecar-injection-strategy)
5. [mTLS and Service-to-Service Security](#5-mtls-and-service-to-service-security)
6. [Traffic Management](#6-traffic-management)
7. [Observability Integration](#7-observability-integration)
8. [Ingress/Egress Configuration](#8-ingressegress-configuration)
9. [Keycloak OAuth2 Integration](#9-keycloak-oauth2-integration)
10. [Step-by-Step Integration Plan](#10-step-by-step-integration-plan)
11. [Potential Conflicts and Mitigation](#11-potential-conflicts-and-mitigation)
12. [Configuration Examples](#12-configuration-examples)
13. [Recommendations](#13-recommendations)

---

## 1. Executive Summary

### Current State
The EazyBank platform consists of 7 microservices (accounts, cards, loans, message, configserver, eurekaserver, gatewayserver) deployed on Kubernetes using Helm charts. The platform currently uses:
- **Spring Cloud Gateway** for API gateway functionality with OAuth2/Keycloak authentication
- **Resilience4j** for circuit breaking, retry, and rate limiting
- **Eureka** for service discovery
- **Prometheus/Tempo/Loki/Grafana** for observability
- **OpenTelemetry** for distributed tracing

### Known Issue (SEC-002)
Keycloak OAuth2 is not fully integrated into service-to-service communication, creating a security gap.

### Istio Integration Benefits
1. **Enhanced Security**: mTLS for all service-to-service communication
2. **Simplified Traffic Management**: Replace Resilience4j with Istio's circuit breaking, retries, and timeouts
3. **Unified Observability**: Leverage Istio's metrics, traces, and access logs
4. **Advanced Routing**: Canary deployments, traffic splitting, fault injection
5. **Security Policy**: Authorization policies for fine-grained access control

---

## 2. Current Architecture Analysis

### 2.1 Kubernetes Setup

**Current Deployment Structure**:
```
.docker/
├── k8s-generated/           # Kompose-generated manifests
│   ├── accounts-deployment.yaml
│   ├── gatewayserver-deployment.yaml (LoadBalancer)
│   └── ... (other services)
└── helm/
    ├── eazybank-common/     # Base templates
    │   └── templates/
    │       ├── deployment.yaml
    │       └── service.yaml
    ├── eazybank-services/   # Individual service charts
    │   ├── accounts/
    │   ├── cards/
    │   ├── loans/
    │   ├── message/
    │   ├── configserver/
    │   ├── eurekaserver/
    │   └── gatewayserver/
    └── environments/         # Umbrella charts
        ├── dev-env/
        ├── prod-env/
        └── qa-env/
```

**Key Observations**:
- Services use basic Kubernetes Deployments with resource limits (500m CPU, 512Mi memory)
- Internal services use ClusterIP (accounts, cards, loans, message, configserver, eurekaserver)
- Gateway server uses LoadBalancer for external access (port 8072)
- Health checks use `/actuator/health/readiness` endpoint
- Configuration via ConfigMaps (`env`, `env-prod`)

### 2.2 Service Communication Patterns

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Current Traffic Flow                         │
│                                                                     │
│  Client → [Keycloak JWT] → Gatewayserver (Spring Cloud Gateway)    │
│                                    │                                │
│                    ┌───────────────┼───────────────┐                │
│                    ▼               ▼               ▼                │
│              accounts:8080   loans:8090    cards:9000             │
│                    │               │               │                │
│                    └───────┬───────┴───────┬───────┘                │
│                            │               │                        │
│                    [Feign Clients with Correlation ID headers]       │
│                            │               │                        │
│                    ▼               ▼               ▼                │
│              cards:9000    loans:8090    (inter-service)           │
│                                                                     │
│  Resilience4j: Circuit Breaker, Retry, Rate Limiter (on Gateway)  │
│  Eureka: Service Discovery (lb://ACCOUNTS, etc.)                  │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 Current Security Gaps

| Issue | Description | Impact |
|-------|-------------|--------|
| SEC-002 | OAuth2 not integrated service-to-service | Internal calls unauthenticated |
| No mTLS | Service communication is plain HTTP | Traffic can be intercepted |
| No Authorization Policy | No fine-grained access control | Any service can call any other |

---

## 3. Istio Installation Strategy

### 3.1 Installation Approach: Helm (Recommended)

**Why Helm over istioctl or Operator**:
1. **GitOps Compatible**: Helm charts can be version-controlled
2. **Consistent with Existing Setup**: EazyBank already uses Helm extensively
3. **Easy Rollback**: `helm rollback` for quick recovery
4. **Multi-Environment Support**: Different values per environment

### 3.2 Installation Commands

```bash
# Add Istio Helm repository
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

# Create istio-system namespace
kubectl create namespace istio-system

# Install Istio base chart (CRDs)
helm install istio-base istio/base \
  --namespace istio-system \
  --set defaultRevision=default

# Install Istiod (control plane)
helm install istiod istio/istiod \
  --namespace istio-system \
  --values .docker/helm/istio/istiod-values.yaml

# Install Istio Ingress Gateway
helm install istio-ingress istio/gateway \
  --namespace istio-system \
  --values .docker/helm/istio/ingress-gateway-values.yaml
```

### 3.3 Recommended Istio Version

**Istio 1.21+** (or latest stable):
- Requires Kubernetes 1.28+ (verify your cluster version)
- Supports Kubernetes Gateway API (alternative to Istio Gateway)
- Improved Helm chart structure

### 3.4 Istio Component Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Istio Control Plane                         │
│                        (istio-system namespace)                   │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │  Istiod      │  │  Ingress     │  │  Egress      │            │
│  │  (Pilot +    │  │  Gateway     │  │  Gateway     │            │
│  │  Citadel +   │  │              │  │              │            │
│  │  Galley)     │  │              │  │              │            │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘            │
│         │                  │                  │                    │
│         └──────────────────┼──────────────────┘                    │
│                            │                                       │
│                    ┌───────▼────────┐                             │
│                    │  Kubernetes    │                             │
│                    │  API Server    │                             │
│                    └────────────────┘                             │
└─────────────────────────────────────────────────────────────────────┘
                            │
                            │ xDS API (discovery)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    EazyBank Services (microservices ns)              │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐       │
│  │  Pod: accounts-deployment                               │       │
│  │  ┌──────────────┐  ┌─────────────────────────────┐    │       │
│  │  │  accounts     │  │  istio-proxy (sidecar)      │    │       │
│  │  │  container    │  │  - Envoy proxy              │    │       │
│  │  │               │  │  - Handles mTLS              │    │       │
│  │  │               │  │  - Traffic management        │    │       │
│  │  │               │  │  - Telemetry                 │    │       │
│  │  └──────────────┘  └─────────────────────────────┘    │       │
│  └──────────────────────────────────────────────────────────┘       │
│                                                                     │
│  [Same pattern for cards, loans, message, etc.]                     │
└────────────────────────────────│─────────────────────────────────────┘
                                │
                                ▼
                        ┌──────────────┐
                        │  Prometheus   │ (scrapes Istio metrics)
                        │  Grafana     │ (visualizes Istio dashboards)
                        │  Tempo       │ (receives Istio traces)
                        └──────────────┘
```

---

## 4. Sidecar Injection Strategy

### 4.1 Injection Approach: Namespace-Level (Recommended)

**Strategy**: Enable automatic sidecar injection for the `microservices` namespace.

```yaml
# .docker/helm/environments/dev-env/templates/namespace.yaml (new file)
apiVersion: v1
kind: Namespace
metadata:
  name: microservices
  labels:
    istio-injection: enabled  # Enable auto-injection for this namespace
```

### 4.2 Services Requiring Sidecars

| Service | Sidecar Required | Reason |
|---------|------------------|--------|
| **accounts** | ✅ Yes | Service-to-service calls with cards/loans |
| **cards** | ✅ Yes | Receives calls from accounts |
| **loans** | ✅ Yes | Receives calls from accounts |
| **message** | ✅ Yes | Event processing |
| **gatewayserver** | ✅ Yes | Entry point, OAuth2 validation |
| **configserver** | ⚠️ Optional | Internal config, consider if sensitive |
| **eurekaserver** | ❌ No | Service discovery, may cause issues with Istio |

### 4.3 Eureka Server Exception

**Recommendation**: Do NOT inject sidecar into Eureka Server initially.

**Reason**: Eureka uses peer-to-peer replication and health checks that may conflict with Istio's mTLS and traffic management.

```yaml
# .docker/helm/eazybank-services/eurekaserver/templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.deploymentName }}
spec:
  template:
    metadata:
      annotations:
        sidecar.istio.io/inject: "false"  # Disable injection for Eureka
    spec:
      containers:
      - name: {{ .Values.appLabel }}
        # ... existing configuration
```

### 4.4 Sidecar Resource Configuration

```yaml
# Add to eazybank-common templates/deployment.yaml
spec:
  template:
    metadata:
      annotations:
        # Resource limits for Istio sidecar
        sidecar.istio.io/proxyCPU: "100m"
        sidecar.istio.io/proxyMemory: "128Mi"
        # Exclude ports from Istio (if needed)
        # traffic.sidecar.istio.io/excludeInboundPorts: "8080"
        # traffic.sidecar.istio.io/excludeOutboundPorts: "3306"
```

### 4.5 Helm Chart Modifications

Update the `eazybank-common/templates/deployment.yaml` to support Istio:

```yaml
{{- define "common.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.deploymentName }}
  labels:
    app: {{ .Values.appLabel }}
    version: {{ .Values.image.tag | default "latest" }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: {{ .Values.appLabel }}
  template:
    metadata:
      labels:
        app: {{ .Values.appLabel }}
        version: {{ .Values.image.tag | default "latest" }}
      {{- if .Values.istio_enabled }}
      annotations:
        sidecar.istio.io/inject: "true"
      {{- end }}
    spec:
      {{- if not .Values.istio_enabled }}
      # Original pod spec without Istio
      containers:
      # ... existing container config
      {{- else }}
      # With Istio - add topology spread constraints
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              app: {{ .Values.appLabel }}
      containers:
      - name: {{ .Values.appLabel }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        # ... rest of container config
      {{- end }}
{{- end -}}
```

---

## 5. mTLS and Service-to-Service Security

### 5.1 mTLS Mode: STRICT (Recommended for Production)

**Phase 1 (Initial)**: PERMISSIVE mode - allows both mTLS and plain text
**Phase 2 (After validation)**: STRICT mode - enforces mTLS for all services

```yaml
# .docker/helm/istio/peer-authentication.yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: microservices
spec:
  mtls:
    mode: STRICT  # Options: STRICT, PERMISSIVE, DISABLE
  # Optional: port-level mTLS override
  # portLevelMtls:
  #   3306:
  #     mode: DISABLE  # Disable mTLS for MySQL (handled by database)
```

### 5.2 Service-to-Service Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    mTLS Handshake Flow                             │
│                                                                     │
│  accounts-pod                cards-pod                              │
│  ┌──────────────┐         ┌──────────────┐                        │
│  │ accounts     │         │ cards        │                        │
│  │ container    │         │ container    │                        │
│  └──────┬───────┘         └──────┬───────┘                        │
│         │                        │                                 │
│  ┌──────▼───────┐         ┌──────▼───────┐                        │
│  │ istio-proxy  │         │ istio-proxy  │                        │
│  │ (Envoy)      │         │ (Envoy)      │                        │
│  └──────┬───────┘         └──────┬───────┘                        │
│         │                        │                                 │
│         └────────┬───────────────┘                                 │
│                  │ mTLS Handshake (TLS 1.3)                       │
│                  │ - Certificate presented (SPIFFE ID)            │
│                  │ - Identity verified via Istiod (Citadel)        │
│                  │ - Encrypted channel established                 │
│                  ▼                                                 │
│         Service-to-Service Call (Encrypted)                        │
│         accounts → cards:9000/api/fetch                           │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.3 Authorization Policies

**Requirement**: Address SEC-002 (OAuth2 not integrated service-to-service).

```yaml
# .docker/helm/istio/authorization-policy-accounts.yaml
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: accounts-service-policy
  namespace: microservices
spec:
  selector:
    matchLabels:
      app: accounts
  rules:
  # Allow gatewayserver to access accounts
  - from:
    - source:
        principals: ["cluster.local/ns/microservices/sa/gatewayserver-sa"]
    to:
    - operation:
        methods: ["GET", "POST", "PUT", "DELETE"]
        paths: ["/api/*", "/actuator/health/*"]
  # Allow accounts to access cards and loans (inter-service)
  - from:
    - source:
        principals: ["cluster.local/ns/microservices/sa/accounts-sa"]
    to:
    - operation:
        methods: ["GET"]
        paths: ["/api/fetch"]
```

### 5.4 Service Account Configuration

Create dedicated ServiceAccounts for each service:

```yaml
# Add to eazybank-common/templates/serviceaccount.yaml (new file)
{{- define "common.serviceaccount" -}}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ .Values.appLabel }}-sa
  namespace: microservices
  {{- if .Values.istio_enabled }}
  annotations:
    # Enable workload identity if using cloud provider
    # iam.gke.io/gcp-service-account: "{{ .Values.appLabel }}@project.iam.gserviceaccount.com"
  {{- end }}
{{- end -}}
```

Update the deployment to use the ServiceAccount:

```yaml
# In eazybank-common/templates/deployment.yaml
spec:
  template:
    spec:
      serviceAccountName: {{ .Values.appLabel }}-sa
      automountServiceAccountToken: true
```

---

## 6. Traffic Management

### 6.1 Resilience4j vs Istio: Recommendation

**Recommendation**: **Hybrid Approach** - Use Istio for infrastructure-level traffic management, keep application-level resilience patterns where business logic is needed.

| Feature | Current (Resilience4j) | Istio Alternative | Recommendation |
|---------|------------------------|-------------------|----------------|
| **Circuit Breaking** | ✅ On Gateway | ✅ VirtualService/DestinationRule | **Migrate to Istio** |
| **Retries** | ✅ On Gateway | ✅ VirtualService | **Migrate to Istio** |
| **Rate Limiting** | ✅ Redis on Gateway | ⚠️ Istio has basic support | **Keep Redis for now** |
| **Timeouts** | ✅ On Gateway | ✅ VirtualService | **Migrate to Istio** |
| **Bulkhead** | ❌ Not implemented | ⚠️ Via connection pool | **Use Istio** |

### 6.2 Why Migrate to Istio for Traffic Management?

1. **Centralized Configuration**: No need to configure Resilience4j in each service
2. **Protocol Agnostic**: Works for HTTP, gRPC, TCP
3. **Dynamic Updates**: Change policies without restarting services
4. **Observability**: Built-in metrics for circuit breaking, retries

### 6.3 Istio Traffic Management Configuration

```yaml
# .docker/helm/istio/destination-rule-accounts.yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: accounts
  namespace: microservices
spec:
  host: accounts
  trafficPolicy:
    # Connection pool settings
    connectionPool:
      tcp:
        maxConnections: 50
        connectTimeout: 5s
        keepAlive: {}
      http:
        http1MaxPendingRequests: 10
        maxRequestsPerConnection: 2
        maxRetries: 3
        perTryTimeout: 2s
    # Circuit breaker (outlier detection)
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
      minHealthPercent: 30
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
---
# .docker/helm/istio/virtual-service-accounts.yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: accounts
  namespace: microservices
spec:
  hosts:
  - accounts
  http:
  - match:
    - headers:
        x-user-type:
          exact: test
    route:
    - destination:
        host: accounts
        subset: v2
      weight: 100
  - route:
    - destination:
        host: accounts
        subset: v1
      weight: 90
    - destination:
        host: accounts
        subset: v2
      weight: 10
    # Retry policy
    retries:
      attempts: 3
      perTryTimeout: 2s
      retryOn: 5xx,reset,connect-failure,refused-stream
    # Timeout
    timeout: 5s
    # CORS policy (if needed)
    corsPolicy:
      allowOrigins:
      - exact: "http://localhost:3000"
      allowMethods:
      - GET
      - POST
      - PUT
      - DELETE
      allowHeaders:
      - "eazybank-correlation-id"
      - "Authorization"
```

### 6.4 Replacing Spring Cloud Gateway with Istio Ingress

**Option A: Keep Spring Cloud Gateway (Recommended Initially)**
- Gradual migration
- Keep OAuth2/Keycloak integration
- Use Istio for internal traffic only

**Option B: Replace with Istio Ingress Gateway**
- Full Istio-native architecture
- Need to implement OAuth2 at Istio level (EnvoyFilter + OPA or custom ext_authz)

```yaml
# Option B: Istio Ingress Gateway Configuration
# .docker/helm/istio/gateway.yaml
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: eazybank-gateway
  namespace: microservices
spec:
  selector:
    istio: ingressgateway  # Use Istio's ingress gateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - "*.eazybank.com"
    - "localhost"
    tls:
      httpsRedirect: true  # Redirect HTTP to HTTPS
  - port:
      number: 443
      name: https
      protocol: HTTPS
    tls:
      mode: SIMPLE
      credentialName: eazybank-tls-cert  # Kubernetes Secret with TLS cert
    hosts:
    - "*.eazybank.com"
---
# VirtualService to route external traffic
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: eazybank-routes
  namespace: microservices
spec:
  hosts:
  - "eazybank.com"
  - "localhost"
  gateways:
  - eazybank-gateway
  http:
  - match:
    - uri:
        prefix: /goutos/bank/accounts
    route:
    - destination:
        host: accounts
        port:
          number: 8080
  - match:
    - uri:
        prefix: /goutos/bank/cards
    route:
    - destination:
        host: cards
        port:
          number: 9000
  - match:
    - uri:
        prefix: /goutos/bank/loans
    route:
    - destination:
        host: loans
        port:
          number: 8090
```

---

## 7. Observability Integration

### 7.1 Current Observability Stack

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Current Observability Stack                      │
│                                                                     │
│  Services → [OTEL Javaagent] → Tempo (traces)                      │
│  Services → [Actuator/Prometheus] → Prometheus (metrics)           │
│  Docker/Alloy → Loki (logs)                                       │
│  All → Grafana (dashboards)                                       │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Istio Observability Integration

Istio automatically generates telemetry for all sidecar-proxied traffic.

```yaml
# .docker/helm/istio/telemetry.yaml
apiVersion: telemetry.istio.io/v1alpha1
kind: Telemetry
metadata:
  name: eazybank-telemetry
  namespace: microservices
spec:
  # Metrics configuration
  metrics:
  - providers:
    - name: prometheus
  # Tracing configuration
  tracing:
  - providers:
    - name: tempo
      # Or use OpenTelemetry Collector
  # Access logging
  accessLogging:
  - providers:
    - name: otel
      format: json
```

### 7.3 Prometheus Integration

Istio exposes Prometheus metrics on port 15020 (proxy) and 15090 (envoy stats).

**Update Prometheus ConfigMap** to scrape Istio metrics:

```yaml
# .docker/k8s-generated/prometheus-cm0-configmap.yaml (updated)
data:
  prometheus.yml: |
    global:
      scrape_interval: 5s
      evaluation_interval: 5s
    
    scrape_configs:
    # Existing service metrics
    - job_name: 'accounts'
      metrics_path: '/actuator/prometheus'
      static_configs:
        - targets: ['accounts:8080']
    
    # Istio proxy metrics
    - job_name: 'istio-proxy'
      metrics_path: '/stats/prometheus'
      kubernetes_sd_configs:
      - role: pod
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_container_name]
        action: keep
        regex: 'istio-proxy'
      - source_labels: [__meta_kubernetes_namespace]
        action: keep
        regex: 'microservices'
    
    # Istio control plane metrics
    - job_name: 'istiod'
      metrics_path: '/metrics'
      static_configs:
        - targets: ['istiod.istio-system:15014']
    
    # ... rest of existing config
```

### 7.4 Tempo (Tracing) Integration

Istio can send traces directly to Tempo via OpenTelemetry or Zipkin protocol.

```yaml
# .docker/helm/istio/telemetry-tracing.yaml
apiVersion: telemetry.istio.io/v1alpha1
kind: Telemetry
metadata:
  name: tracing-config
  namespace: microservices
spec:
  selector:
    matchLabels:
      app: accounts  # Apply to specific service or remove for all
  tracing:
  - providers:
    - name: tempo-otel
  - customTags:
      correlation_id:
        header:
          name: eazybank-correlation-id
      user_id:
        header:
          name: x-user-id
---
# OpenTelemetry provider configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: istio-system
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    exporters:
      otlp:
        endpoint: tempo:4318
        tls:
          insecure: true
    service:
      pipelines:
        traces:
          receivers: [otlp]
          exporters: [otlp]
```

### 7.5 Grafana Dashboards for Istio

Install Istio dashboards in Grafana:

```yaml
# .docker/grafana/dashboards/istio-dashboards-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: istio-dashboards
  namespace: grafana
  labels:
    grafana_dashboard: "1"
data:
  istio-mesh-dashboard.json: |
    # (Istio Mesh Dashboard JSON)
  istio-service-dashboard.json: |
    # (Istio Service Dashboard JSON)
  istio-workload-dashboard.json: |
    # (Istio Workload Dashboard JSON)
```

**Recommended Dashboards**:
1. **Istio Mesh Dashboard** - Overview of all services
2. **Istio Service Dashboard** - Per-service metrics
3. **Istio Workload Dashboard** - Per-pod metrics
4. **Istio Performance Dashboard** - Latency, throughput

---

## 8. Ingress/Egress Configuration

### 8.1 Current Ingress: Spring Cloud Gateway

```
Client → [OAuth2 JWT] → Spring Cloud Gateway (port 8072) → Services
```

### 8.2 Istio Ingress Options

**Option 1: Sidecar Approach (Recommended for Migration)**
- Keep Spring Cloud Gateway as entry point
- Inject Istio sidecar into Gateway pod
- Gateway handles OAuth2, Istio handles internal traffic

**Option 2: Replace with Istio Ingress Gateway**
- Remove Spring Cloud Gateway
- Use Istio Ingress Gateway + VirtualServices
- Implement OAuth2 via EnvoyFilter or external authz

### 8.3 Recommended Architecture (Hybrid)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Recommended Ingress Architecture                  │
│                                                                     │
│  External Client                                                   │
│       │                                                             │
│       ▼                                                             │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  Istio Ingress Gateway (istio-system namespace)           │     │
│  │  - TLS termination                                      │     │
│  │  - Rate limiting (optional)                              │     │
│  │  - Basic routing                                        │     │
│  └────────────────────┬─────────────────────────────────────┘     │
│                       │                                           │
│                       ▼                                           │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  Spring Cloud Gateway (microservices namespace)           │     │
│  │  - OAuth2/Keycloak JWT validation                       │     │
│  │  - Path rewrite (/goutos/bank/{service}/** → /**)        │     │
│  │  - Request/Response filters (correlation ID)             │     │
│  │  [+ Istio sidecar for mTLS to backend]                  │     │
│  └────────────────────┬─────────────────────────────────────┘     │
│                       │                                           │
│         ┌─────────────┼─────────────┐                            │
│         ▼             ▼             ▼                            │
│    accounts:8080  loans:8090  cards:9000                        │
│    [+sidecar]    [+sidecar]   [+sidecar]                      │
│                                                                     │
│  All service-to-service traffic encrypted with mTLS                 │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.4 Egress Configuration

For external services (Keycloak, Git config repo, etc.):

```yaml
# .docker/helm/istio/service-entry-keycloak.yaml
apiVersion: networking.istio.io/v1beta1
kind: ServiceEntry
metadata:
  name: keycloak-external
  namespace: microservices
spec:
  hosts:
  - keycloak.eazybank.com  # Or external hostname
  ports:
  - number: 80
    name: http
    protocol: HTTP
  - number: 443
    name: https
    protocol: HTTPS
  location: MESH_EXTERNAL
  resolution: DNS
---
# Optional: Egress Gateway for controlled external access
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: egress-gateway
  namespace: istio-system
spec:
  selector:
    istio: egressgateway
  servers:
  - port:
      number: 443
      name: https
      protocol: HTTPS
    hosts:
    - "*.keycloak.com"
    tls:
      mode: ISTIO_MUTUAL
```

---

## 9. Keycloak OAuth2 Integration

### 9.1 Current State

- Keycloak validates JWT tokens at Spring Cloud Gateway
- No OAuth2 validation on service-to-service calls (SEC-002)

### 9.2 Istio + Keycloak Integration Options

**Option A: Keep OAuth2 at Gateway Level (Recommended)**
- Gateway validates JWT
- Pass validated identity via headers to backend services
- Use mTLS for service-to-service encryption

```yaml
# Gateway passes identity headers to backend
# In Spring Cloud Gateway (RouteConfig.java) - already implemented
# Just ensure headers are passed through Istio sidecar
```

**Option B: End-to-End OAuth2 with Istio**
- Use Istio's RequestAuthentication and AuthorizationPolicy
- Validate JWT at each service (redundant but more secure)

```yaml
# .docker/helm/istio/request-authentication.yaml
apiVersion: security.istio.io/v1beta1
kind: RequestAuthentication
metadata:
  name: keycloak-jwt
  namespace: microservices
spec:
  selector:
    matchLabels:
      app: accounts  # Apply to specific service
  jwtRules:
  - issuer: "http://keycloak:8080/realms/master"
    jwksUri: "http://keycloak:8080/realms/master/protocol/openid-connect/certs"
    # Forward the original token to the application
    outputPayloadToHeader: "x-jwt-payload"
---
# AuthorizationPolicy to require JWT
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: require-jwt
  namespace: microservices
spec:
  selector:
    matchLabels:
      app: accounts
  rules:
  - from:
    - source:
        requestPrincipals: ["*"]  # Require valid JWT
    to:
    - operation:
        methods: ["GET", "POST", "PUT", "DELETE"]
```

### 9.3 Recommended Approach for SEC-002

**Hybrid Security Model**:
1. **External Traffic**: OAuth2 JWT validated at Gateway (existing)
2. **Internal Traffic**: mTLS + AuthorizationPolicy (new with Istio)
3. **Identity Propagation**: Pass user identity via headers (correlation ID, user ID)

```yaml
# Pass identity from Gateway to backend via headers
# In gatewayserver RouteConfig.java, ensure these headers are forwarded:
# - Authorization: Bearer <token>
# - x-user-id: <user-id>
# - eazybank-correlation-id: <correlation-id>

# Istio AuthorizationPolicy for service-to-service
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: internal-only
  namespace: microservices
spec:
  rules:
  # Allow requests only from known services
  - from:
    - source:
        principals: 
        - "cluster.local/ns/microservices/sa/accounts-sa"
        - "cluster.local/ns/microservices/sa/gatewayserver-sa"
    to:
    - operation:
        paths: ["/api/*"]
```

---

## 10. Step-by-Step Integration Plan

### Phase 1: Preparation (Week 1)

```markdown
[ ] 1.1 Verify Kubernetes cluster compatibility (1.28+ for Istio 1.21+)
[ ] 1.2 Backup existing configurations
[ ] 1.3 Create test environment (clone of dev-env)
[ ] 1.4 Document current service dependencies and traffic flows
[ ] 1.5 Set up Istio CLI tools (istioctl) for testing
```

### Phase 2: Istio Installation (Week 1-2)

```markdown
[ ] 2.1 Create Istio Helm charts in .docker/helm/istio/
[ ] 2.2 Install Istio base CRDs
[ ] 2.3 Install Istiod control plane
[ ] 2.4 Install Istio Ingress Gateway
[ ] 2.5 Verify installation: `istioctl verify-install`
[ ] 2.6 Deploy sample application to test Istio functionality
```

### Phase 3: Sidecar Injection - Non-Production Services (Week 2)

```markdown
[ ] 3.1 Enable namespace-level injection for test namespace
[ ] 3.2 Inject sidecar into accounts service
[ ] 3.3 Verify mTLS with PERMISSIVE mode
[ ] 3.4 Check telemetry (Prometheus metrics, traces)
[ ] 3.5 Repeat for cards and loans services
[ ] 3.6 Update Helm charts to support `istio_enabled` flag
```

### Phase 4: mTLS Configuration (Week 3)

```markdown
[ ] 4.1 Configure PeerAuthentication (PERMISSIVE mode initially)
[ ] 4.2 Test service-to-service communication
[ ] 4.3 Verify Eureka can still discover services (no sidecar on Eureka)
[ ] 4.4 Monitor for any connection issues
[ ] 4.5 Switch to STRICT mode after validation
```

### Phase 5: Traffic Management Migration (Week 3-4)

```markdown
[ ] 5.1 Create DestinationRules for connection pool and outlier detection
[ ] 5.2 Create VirtualServices for retries and timeouts
[ ] 5.3 Gradually remove Resilience4j config from Gateway (keep for now)
[ ] 5.4 Test circuit breaking behavior
[ ] 5.5 Document new traffic management approach
```

### Phase 6: Observability Integration (Week 4)

```markdown
[ ] 6.1 Update Prometheus to scrape Istio proxy metrics
[ ] 6.2 Configure Istio telemetry for Tempo tracing
[ ] 6.3 Import Istio dashboards to Grafana
[ ] 6.4 Verify distributed tracing includes Istio sidecar spans
[ ] 6.5 Create unified dashboards (app + Istio metrics)
```

### Phase 7: Security Hardening (Week 5)

```markdown
[ ] 7.1 Create ServiceAccounts for each service
[ ] 7.2 Define AuthorizationPolicies for service-to-service access
[ ] 7.3 Test authorization (should block unauthorized calls)
[ ] 7.4 Configure external service access (Keycloak, Git)
[ ] 7.5 Document security architecture
```

### Phase 8: Production Rollout (Week 5-6)

```markdown
[ ] 8.1 Apply same changes to prod-env
[ ] 8.2 Monitor metrics and logs closely
[ ] 8.3 Have rollback plan ready
[ ] 8.4 Gradually increase traffic to Istio-enabled services
[ ] 8.5 Complete documentation and runbooks
```

### Phase 9: Optimization (Week 6+)

```markdown
[ ] 9.1 Fine-tune circuit breaker settings
[ ] 9.2 Optimize mTLS performance
[ ] 9.3 Consider removing Resilience4j if Istio handles all cases
[ ] 9.4 Evaluate if Eureka can be replaced with Istio service discovery
[ ] 9.5 Plan for advanced features (canary, fault injection)
```

---

## 11. Potential Conflicts and Mitigation

### 11.1 Conflict Matrix

| Conflict | Description | Impact | Mitigation |
|----------|-------------|--------|------------|
| **Eureka + Istio** | Both provide service discovery | Duplicate service registry | Keep Eureka for app-level discovery, Istio for traffic management |
| **Resilience4j + Istio** | Both implement circuit breaking | Double retry/circuit break | Migrate to Istio gradually |
| **Spring Cloud Gateway + Istio Ingress** | Two ingress layers | Added latency | Use Gateway for OAuth2, Istio for mTLS |
| **Sidecar Port Conflicts** | Istio uses 15001, 15006 | Port conflicts | Ensure no app uses these ports |
| **Health Checks** | Istio may interfere with /health | Pods appear unhealthy | Configure `traffic.sidecar.istio.io/includeInboundPorts: "8080"` |
| **Eureka Health Checks** | Eureka expects direct access | Service registration fails | Use `eureka.instance.preferIpAddress=true` or ServiceEntry |

### 11.2 Eureka Server Considerations

**Problem**: Eureka uses peer-to-peer replication and heartbeats.

**Mitigation**:
```yaml
# Option A: No sidecar on Eureka (recommended)
# In eurekaserver deployment:
annotations:
  sidecar.istio.io/inject: "false"

# Option B: If sidecar needed, configure traffic rules
apiVersion: networking.istio.io/v1beta1
kind: ServiceEntry
metadata:
  name: eurekaserver-external
spec:
  hosts:
  - eurekaserver.microservices.svc.cluster.local
  ports:
  - number: 8070
    name: http
    protocol: HTTP
  location: MESH_INTERNAL
  resolution: STATIC
  endpoints:
  - address: 10.0.0.1  # Eureka pod IP
```

### 11.3 Database Connections (MySQL)

**Problem**: Istio sidecar may interfere with database connections.

**Mitigation**:
```yaml
# Exclude database ports from Istio interception
# In deployment annotations:
metadata:
  annotations:
    traffic.sidecar.istio.io/excludeOutboundPorts: "3306,3307,3308,3309"
```

Or configure in MySQL services:
```yaml
# DestinationRule for MySQL
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: accountsdb
spec:
  host: accountsdb
  trafficPolicy:
    tls:
      mode: DISABLE  # No mTLS for database connections
```

### 11.4 Redis and RabbitMQ

Similar to databases, these may need exclusion or special handling:

```yaml
# Exclude Redis and RabbitMQ ports
traffic.sidecar.istio.io/excludeOutboundPorts: "6379,5672,15672"
```

---

## 12. Configuration Examples

### 12.1 Helm Chart Structure for Istio

```
.docker/helm/istio/
├── Chart.yaml              # Istio components chart
├── values.yaml             # Default values
├── values-dev.yaml         # Dev environment overrides
├── values-prod.yaml        # Prod environment overrides
└── templates/
    ├── namespace.yaml              # Istio namespace
    ├── peer-authentication.yaml    # mTLS config
    ├── destination-rule-*.yaml     # Traffic policies
    ├── virtual-service-*.yaml      # Routing rules
    ├── authorization-policy-*.yaml # Security policies
    ├── request-authentication.yaml # JWT validation
    ├── service-entry-*.yaml        # External services
    ├── telemetry.yaml              # Observability
    └── gateways.yaml               # Ingress/Egress
```

### 12.2 Example: Updated Helm Values for Accounts Service

```yaml
# .docker/helm/eazybank-services/accounts/values.yaml (updated)
deploymentName: accounts-deployment
serviceName: accounts
appLabel: accounts
appName: accounts

replicaCount: 2  # Increased for HA with Istio

image:
  repository: eazybytes/accounts
  tag: s14

containerPort: 8080

service:
  type: ClusterIP
  port: 8080
  targetPort: 8080

# Feature flags
appname_enabled: true
profile_enabled: true
config_enabled: true
eureka_enabled: true
resouceserver_enabled: false
otel_enabled: true
kafka_enabled: true

# NEW: Istio configuration
istio_enabled: true
istio:
  # Sidecar configuration
  sidecar:
    inject: true
    resources:
      cpu: 100m
      memory: 128Mi
  # mTLS mode for this service
  mtls:
    mode: STRICT  # or PERMISSIVE for migration
  # Traffic management
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 50
      http:
        maxRequestsPerConnection: 10
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 30s
  # VirtualService settings
  virtualService:
    retries:
      attempts: 3
      perTryTimeout: 2s
    timeout: 5s
```

### 12.3 Example: Istio Values File

```yaml
# .docker/helm/istio/values.yaml
# Istio control plane configuration
istiod:
  enabled: true
  replicaCount: 1  # Increase for HA
  image:
    repository: istio/pilot
    tag: 1.21.0
  
  # Configuration
  config:
    # Enable Kubernetes Gateway API
    enableGatewayAPI: false  # Keep false to use Istio API
    # Trust domain for SPIFFE identities
    trustDomain: cluster.local
  
  # Resources
  resources:
    requests:
      cpu: 500m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1024Mi

# Ingress Gateway configuration
ingressGateway:
  enabled: true
  labels:
    istio: ingressgateway
  replicaCount: 2
  
  # Service configuration
  service:
    type: LoadBalancer
    ports:
    - name: http2
      port: 80
      targetPort: 8080
    - name: https
      port: 443
      targetPort: 8443
  
  # Resources
  resources:
    requests:
      cpu: 100m
      memory: 128Mi
    limits:
      cpu: 500m
      memory: 512Mi

# Global mTLS setting
mtls:
  mode: STRICT  # STRICT, PERMISSIVE, or DISABLE

# Telemetry configuration
telemetry:
  enabled: true
  tracing:
    enabled: true
    provider: tempo  # or zipkin, jaeger
    samplingRate: 100.0  # 100% for dev, lower for prod
  metrics:
    enabled: true
    provider: prometheus
  accessLogging:
    enabled: true
    format: json
```

---

## 13. Recommendations

### 13.1 Key Decisions

| Decision | Recommendation | Rationale |
|----------|----------------|------------|
| **Installation** | Helm | Consistent with existing setup, GitOps-friendly |
| **mTLS Mode** | STRICT (after PERMISSIVE validation) | Addresses SEC-002, encrypts all internal traffic |
| **Traffic Management** | Migrate to Istio | Centralized, protocol-agnostic, better observability |
| **Ingress** | Keep Spring Cloud Gateway initially | Preserves OAuth2 investment, gradual migration |
| **Eureka** | Keep, no sidecar | Avoids service discovery conflicts |
| **Databases** | Exclude from Istio | Better performance, no need for mTLS |

### 13.2 Success Criteria

Define measurable goals for the Istio integration:

```markdown
[ ] All service-to-service traffic encrypted with mTLS
[ ] SEC-002 resolved (OAuth2 integrated or mTLS provides equivalent security)
[ ] Zero downtime during migration
[ ] Istio metrics visible in Grafana
[ ] Distributed traces include Istio sidecar spans
[ ] Circuit breaking and retries working via Istio
[ ] Authorization policies blocking unauthorized access
[ ] Performance within 5% of pre-Istio baseline
```

### 13.3 Risks and Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Increased Latency** | Medium | Medium | Monitor, tune connection pools, consider proxy performance |
| **Configuration Complexity** | High | Medium | Use Helm, document everything, start simple |
| **Service Discovery Issues** | Medium | High | Keep Eureka, test thoroughly |
| **mTLS Certificate Issues** | Low | High | Monitor Istiod, have rollback plan |
| **Observability Gaps** | Low | Medium | Verify all metrics/traces in Grafana |

### 13.4 Next Steps

1. **Review this document** with the team
2. **Set up a test environment** for Istio validation
3. **Create the Helm charts** in `.docker/helm/istio/`
4. **Start with Phase 1** of the integration plan
5. **Document lessons learned** throughout the process

---

## Appendices

### A. Useful Istio Commands

```bash
# Verify Istio installation
istioctl version
istioctl verify-install

# Check sidecar injection
kubectl get namespace -L istio-injection
kubectl describe pod <pod-name> -n microservices | grep -i istio

# View proxy configuration
istioctl proxy-config cluster <pod-name> -n microservices
istioctl proxy-config listener <pod-name> -n microservices
istioctl proxy-config route <pod-name> -n microservices

# Check mTLS status
istioctl authn tls-check <pod-name> -n microservices

# Analyze configuration
istioctl analyze -n microservices

# View proxy logs
kubectl logs <pod-name> -c istio-proxy -n microservices

# Generate dashboard
istioctl dashboard kiali  # Requires Kiali installed
```

### B. Istio Resources Quick Reference

| Resource | Kind | Purpose |
|----------|------|---------|
| **VirtualService** | `networking.istio.io/v1beta1` | Routing rules, retries, timeouts |
| **DestinationRule** | `networking.istio.io/v1beta1` | Traffic policies, circuit breaking, mTLS |
| **Gateway** | `networking.istio.io/v1beta1` | Ingress/egress configuration |
| **ServiceEntry** | `networking.istio.io/v1beta1` | Register external services |
| **PeerAuthentication** | `security.istio.io/v1beta1` | mTLS mode (STRICT/PERMISSIVE) |
| **RequestAuthentication** | `security.istio.io/v1beta1` | JWT validation |
| **AuthorizationPolicy** | `security.istio.io/v1beta1` | Access control rules |
| **Telemetry** | `telemetry.istio.io/v1alpha1` | Observability configuration |
| **Sidecar** | `networking.istio.io/v1beta1` | Fine-tune sidecar behavior |

### C. Comparison: Before and After Istio

| Aspect | Before Istio | After Istio |
|--------|--------------|-------------|
| **Security** | OAuth2 at Gateway only, no mTLS | mTLS everywhere, AuthZ policies |
| **Traffic Management** | Resilience4j (app-level) | Istio (infrastructure-level) |
| **Observability** | App metrics + OTEL traces | + Istio proxy metrics/traces |
| **Service Discovery** | Eureka | Eureka + Istio (dual) |
| **Configuration** | application.yml | + Istio CRDs |
| **Complexity** | Medium | Higher initially, lower long-term |

---

*Document Version: 1.0*  
*Created: 2026-04-30*  
*Author: Architect Mode (Kilo Code)*
