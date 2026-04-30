# Istio Integration for EazyBank Microservices Platform

> **Comprehensive Istio service mesh integration** for the EazyBank microservices platform running on Kubernetes.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Directory Structure](#2-directory-structure)
3. [Prerequisites](#3-prerequisites)
4. [Installation](#4-installation)
5. [Migration Guide](#5-migration-guide)
6. [Configuration](#6-configuration)
7. [Verification](#7-verification)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Overview

This directory contains all configuration files, scripts, and documentation for integrating Istio service mesh into the EazyBank microservices platform.

### Benefits

1. **Enhanced Security**: mTLS for all service-to-service communication
2. **Simplified Traffic Management**: Circuit breaking, retries, and timeouts via Istio
3. **Unified Observability**: Leverage Istio's metrics, traces, and access logs
4. **Advanced Routing**: Canary deployments, traffic splitting, fault injection
5. **Security Policy**: Authorization policies for fine-grained access control

### Architecture Decision

- **Installation Method**: Helm (consistent with existing setup)
- **mTLS Mode**: STRICT (after PERMISSIVE validation)
- **Ingress**: Keep Spring Cloud Gateway initially, use Istio for internal traffic
- **Eureka**: Keep without sidecar to avoid service discovery conflicts
- **Databases**: Exclude from Istio sidecar interception

---

## 2. Directory Structure

```
.docker/istio/
├── README.md                          # This file
├── install-istio.sh                   # Automated Istio installation script
├── migrate-to-istio.sh                # Step-by-step migration script (9-phase approach)
├── base/                              # Base Istio installation manifests
│   ├── namespace.yaml                 # Istio namespace configuration
│   └── istio-values.yaml             # Helm values for Istio installation
├── security/                          # Security configurations
│   ├── peer-authentication.yaml      # mTLS configuration (STRICT/PERMISSIVE)
│   ├── authorization-policies.yaml    # Service-to-service access control
│   └── request-authentication.yaml   # JWT validation (Keycloak integration)
├── traffic/                           # Traffic management
│   ├── destination-rules.yaml        # Circuit breaking, outlier detection
│   ├── virtual-services.yaml         # Timeouts, retries, routing rules
│   └── service-entries.yaml         # External services (Keycloak, databases)
├── observability/                     # Observability integration
│   ├── telemetry.yaml                # Metrics, traces, logs collection
│   └── grafana-dashboards/         # Istio dashboards for Grafana
└── scripts/                          # Utility scripts
    └── verify-istio.sh               # Verification script
```

---

## 3. Prerequisites

### Kubernetes Cluster

- **Kubernetes Version**: 1.28+ (required for Istio 1.21+)
- **kubectl**: Configured and authenticated
- **Helm**: 3.9+ installed
- **istioctl**: Download from [Istio releases](https://github.com/istio/istio/releases)

### Verify Cluster Compatibility

```bash
# Check Kubernetes version
kubectl version --short

# Check available nodes
kubectl get nodes

# Verify Helm is installed
helm version
```

### EazyBank Platform

- Existing Kubernetes manifests in `.docker/k8s-generated/`
- Helm charts in `.docker/helm/`
- Microservices namespace created (or will be created)

---

## 4. Installation

### Quick Start

```bash
# Navigate to istio directory
cd .docker/istio

# Run the automated installation script
./install-istio.sh
```

### Manual Installation Steps

#### Step 1: Add Istio Helm Repository

```bash
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update
```

#### Step 2: Create Istio Namespace

```bash
kubectl create namespace istio-system
kubectl label namespace istio-system istio-injection=disabled
```

#### Step 3: Install Istio Base (CRDs)

```bash
helm install istio-base istio/base \
  --namespace istio-system \
  --set defaultRevision=default
```

#### Step 4: Install Istiod (Control Plane)

```bash
helm install istiod istio/istiod \
  --namespace istio-system \
  --values base/istio-values.yaml
```

#### Step 5: Install Istio Ingress Gateway

```bash
helm install istio-ingress istio/gateway \
  --namespace istio-system \
  --values base/istio-values.yaml
```

#### Step 6: Verify Installation

```bash
# Check Istio pods
kubectl get pods -n istio-system

# Verify installation
istioctl verify-install

# Check control plane status
kubectl get svc istiod -n istio-system
```

---

## 5. Migration Guide

The migration follows a **9-phase approach** to ensure zero downtime:

### Phase 1: Preparation (Week 1)
- Verify Kubernetes cluster compatibility
- Backup existing configurations
- Create test environment
- Document service dependencies

### Phase 2: Istio Installation (Week 1-2)
- Install Istio base, istiod, and ingress gateway
- Verify installation
- Deploy test applications

### Phase 3: Sidecar Injection (Week 2)
- Enable namespace-level injection
- Inject sidecar into accounts service
- Verify mTLS with PERMISSIVE mode
- Repeat for cards and loans

### Phase 4: mTLS Configuration (Week 3)
- Configure PeerAuthentication (PERMISSIVE mode)
- Test service-to-service communication
- Switch to STRICT mode after validation

### Phase 5: Traffic Management (Week 3-4)
- Create DestinationRules for circuit breaking
- Create VirtualServices for retries and timeouts
- Test traffic management policies

### Phase 6: Observability Integration (Week 4)
- Update Prometheus to scrape Istio metrics
- Configure telemetry for Tempo tracing
- Import Istio dashboards to Grafana

### Phase 7: Security Hardening (Week 5)
- Create ServiceAccounts for each service
- Define AuthorizationPolicies
- Configure external service access

### Phase 8: Production Rollout (Week 5-6)
- Apply changes to production
- Monitor metrics and logs
- Execute rollback plan if needed

### Phase 9: Optimization (Week 6+)
- Fine-tune circuit breaker settings
- Optimize mTLS performance
- Plan advanced features (canary, fault injection)

### Run Migration Script

```bash
cd .docker/istio
./migrate-to-istio.sh
```

---

## 6. Configuration

### Sidecar Injection

**Enable for microservices namespace**:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: microservices
  labels:
    istio-injection: enabled
```

**Disable for Eureka Server** (add to deployment annotations):
```yaml
metadata:
  annotations:
    sidecar.istio.io/inject: "false"
```

### mTLS Configuration

**PERMISSIVE Mode** (initial):
```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: microservices
spec:
  mtls:
    mode: PERMISSIVE
```

**STRICT Mode** (after validation):
```yaml
spec:
  mtls:
    mode: STRICT
```

### Traffic Management

**DestinationRule** (circuit breaking):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
spec:
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 50
      http:
        http1MaxPendingRequests: 10
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 30s
```

**VirtualService** (retries and timeouts):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
spec:
  http:
  - route:
    - destination:
        host: accounts
    retries:
      attempts: 3
      perTryTimeout: 2s
    timeout: 5s
```

---

## 7. Verification

### Verify Sidecar Injection

```bash
# Check namespace injection label
kubectl get namespace microservices -L istio-injection

# Verify sidecar in pods
kubectl get pods -n microservices
kubectl describe pod <pod-name> -n microservices | grep -i istio

# Check proxy status
istioctl proxy-status -n microservices
```

### Verify mTLS

```bash
# Check mTLS mode
istioctl authn tls-check -n microservices

# View proxy configuration
istioctl proxy-config cluster <pod-name> -n microservices
```

### Verify Traffic Management

```bash
# Analyze configuration
istioctl analyze -n microservices

# Check destination rules
kubectl get destinationrules -n microservices

# Check virtual services
kubectl get virtualservices -n microservices
```

### Verify Observability

```bash
# Check telemetry configuration
kubectl get telemetry -n microservices

# View proxy metrics
kubectl port-forward -n istio-system svc/istio-ingress 15020:15020
curl http://localhost:15020/stats/prometheus

# Check Prometheus targets
kubectl port-forward -n prometheus svc/prometheus 9090:9090
# Visit http://localhost:9090/targets
```

---

## 8. Troubleshooting

### Common Issues

#### Issue: Sidecar Not Injected

**Symptom**: Pod only has one container (no istio-proxy)

**Solution**:
```bash
# Verify namespace label
kubectl get namespace microservices -L istio-injection

# Re-deploy the pod after enabling injection
kubectl rollout restart deployment <deployment-name> -n microservices
```

#### Issue: mTLS Handshake Fails

**Symptom**: 503 errors between services

**Solution**:
```bash
# Check PeerAuthentication configuration
kubectl get peerauthentication -n microservices -o yaml

# Temporarily switch to PERMISSIVE mode
kubectl patch peerauthentication default -n microservices --type='json' -p='[{"op": "replace", "path": "/spec/mtls/mode", "value":"PERMISSIVE"}]'
```

#### Issue: Eureka Service Discovery Fails

**Symptom**: Services not registering with Eureka

**Solution**:
Ensure Eureka Server has sidecar injection disabled:
```yaml
annotations:
  sidecar.istio.io/inject: "false"
```

#### Issue: Database Connections Fail

**Symptom**: Services cannot connect to MySQL

**Solution**:
Exclude database ports from Istio interception:
```yaml
annotations:
  traffic.sidecar.istio.io/excludeOutboundPorts: "3306,3307,3308,3309"
```

### Useful Commands

```bash
# View Istio logs
kubectl logs -n istio-system deployment/istiod

# View sidecar proxy logs
kubectl logs <pod-name> -c istio-proxy -n microservices

# Check Istio resources
kubectl get all -n istio-system

# Analyze configuration
istioctl analyze -n microservices --all-namespaces
```

---

## References

- [Istio Documentation](https://istio.io/latest/docs/)
- [Istio / Security Concepts](https://istio.io/latest/docs/concepts/security/)
- [Istio / Traffic Management](https://istio.io/latest/docs/concepts/traffic-management/)
- [Istio / Observability](https://istio.io/latest/docs/concepts/observability/)
- [EazyBank Architecture Document](../ISTIO_INTEGRATION_ARCHITECTURE.md)

---

*Version: 1.0*  
*Created: 2026-04-30*  
*Author: Code Mode (Kilo Code)*
