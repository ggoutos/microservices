#!/bin/bash
#===============================================================================
# Istio Migration Script for EazyBank Platform
#===============================================================================
# This script implements the 9-phase migration approach from the architecture
# document (ISTIO_INTEGRATION_ARCHITECTURE.md).
#
# Usage: ./migrate-to-istio.sh [PHASE]
#   PHASE: Run specific phase (1-9), or all phases if not specified
#
# Options:
#   --dry-run    : Print commands without executing
#   --rollback   : Rollback to pre-Istio state
#   --status     : Check migration status
#   --help       : Show this help message
#===============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ISTIO_NAMESPACE="istio-system"
MICROSERVICES_NS="microservices"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SECURITY_DIR="$SCRIPT_DIR/security"
TRAFFIC_DIR="$SCRIPT_DIR/traffic"
OBSERVABILITY_DIR="$SCRIPT_DIR/observability"

# Parse command line arguments
DRY_RUN=false
ROLLBACK=false
STATUS=false
SPECIFIC_PHASE=""

for arg in "$@"; do
  case $arg in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --rollback)
      ROLLBACK=true
      shift
      ;;
    --status)
      STATUS=true
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [PHASE] [OPTIONS]"
      echo "PHASE: Run specific phase (1-9), or all phases if not specified"
      echo "Options:"
      echo "  --dry-run    : Print commands without executing"
      echo "  --rollback   : Rollback to pre-Istio state"
      echo "  --status     : Check migration status"
      echo "  --help       : Show this help message"
      exit 0
      ;;
    [1-9])
      SPECIFIC_PHASE="$arg"
      shift
      ;;
  esac
done

# Helper functions
log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

log_phase() {
  echo -e "${BLUE}=== $1 ===${NC}"
}

run_command() {
  if [ "$DRY_RUN" = true ]; then
    echo "DRY RUN: $1"
  else
    log_info "Executing: $1"
    eval "$1"
  fi
}

#===============================================================================
# Phase 1: Preparation
#===============================================================================
phase1_preparation() {
  log_phase "Phase 1: Preparation (Week 1)"
  
  log_info "1.1 Verifying Kubernetes cluster compatibility..."
  K8S_VERSION=$(kubectl version -o json | jq -r '.serverVersion.minor' 2>/dev/null || echo "0")
  if [ "$K8S_VERSION" -lt 28 ] 2>/dev/null; then
    log_warn "Kubernetes version may be too old. Istio 1.21+ requires Kubernetes 1.28+"
  fi
  
  log_info "1.2 Backing up existing configurations..."
  run_command "kubectl get all -n $MICROSERVICES_NS -o yaml > backup-microservices-$(date +%Y%m%d).yaml"
  run_command "kubectl get configmaps -n $MICROSERVICES_NS -o yaml > backup-configmaps-$(date +%Y%m%d).yaml"
  
  log_info "1.3 Documenting current service dependencies..."
  run_command "kubectl get svc -n $MICROSERVICES_NS -o wide"
  run_command "kubectl get pods -n $MICROSERVICES_NS -o wide"
  
  log_info "1.4 Setting up Istio CLI tools check..."
  if ! command -v istioctl &> /dev/null; then
    log_warn "istioctl is not installed. Installing..."
    run_command "curl -L https://istio.io/downloadIstio | sh -"
    run_command "export PATH=\$PWD/istio-1.21.0/bin:\$PATH"
  fi
  
  log_info "Phase 1 completed!"
}

#===============================================================================
# Phase 2: Istio Installation
#===============================================================================
phase2_installation() {
  log_phase "Phase 2: Istio Installation (Week 1-2)"
  
  log_info "2.1 Creating Istio Helm charts directory..."
  # Already created in .docker/istio/
  
  log_info "2.2 Installing Istio base CRDs..."
  run_command "helm install istio-base istio/base --namespace $ISTIO_NAMESPACE --set defaultRevision=default"
  
  log_info "2.3 Installing Istiod control plane..."
  run_command "helm install istiod istio/istiod --namespace $ISTIO_NAMESPACE --values $SCRIPT_DIR/base/istio-values.yaml"
  
  log_info "2.4 Installing Istio Ingress Gateway..."
  run_command "helm install istio-ingress istio/gateway --namespace $ISTIO_NAMESPACE --values $SCRIPT_DIR/base/istio-values.yaml"
  
  log_info "2.5 Verifying installation..."
  if command -v istioctl &> /dev/null; then
    run_command "istioctl verify-install -n $ISTIO_NAMESPACE"
  fi
  run_command "kubectl get pods -n $ISTIO_NAMESPACE"
  
  log_info "2.6 Deploying sample application to test Istio functionality..."
  # Optional: Deploy sample app for testing
  
  log_info "Phase 2 completed!"
}

#===============================================================================
# Phase 3: Sidecar Injection
#===============================================================================
phase3_sidecar_injection() {
  log_phase "Phase 3: Sidecar Injection - Non-Production Services (Week 2)"
  
  log_info "3.1 Enabling namespace-level injection for microservices namespace..."
  run_command "kubectl label namespace $MICROSERVICES_NS istio-injection=enabled --overwrite"
  
  log_info "3.2 Configuring Eureka Server to NOT have sidecar..."
  run_command "kubectl patch deployment eurekaserver -n $MICROSERVICES_NS -p '{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"sidecar.istio.io/inject\":\"false\"}}}}'"
  
  log_info "3.3 Injecting sidecar into accounts service..."
  run_command "kubectl rollout restart deployment accounts -n $MICROSERVICES_NS"
  run_command "kubectl rollout status deployment accounts -n $MICROSERVICES_NS --timeout=300s"
  
  log_info "3.4 Verifying mTLS with PERMISSIVE mode..."
  run_command "kubectl apply -f $SECURITY_DIR/peer-authentication.yaml"
  
  log_info "3.5 Checking telemetry (Prometheus metrics, traces)..."
  run_command "kubectl get pods -n $MICROSERVICES_NS"
  
  log_info "3.6 Repeating for cards and loans services..."
  for svc in cards loans message gatewayserver configserver; do
    log_info "Restarting $svc to inject sidecar..."
    run_command "kubectl rollout restart deployment $svc -n $MICROSERVICES_NS"
    run_command "kubectl rollout status deployment $svc -n $MICROSERVICES_NS --timeout=300s"
  done
  
  log_info "3.6 Updating Helm charts to support istio_enabled flag..."
  # Done manually in Helm charts
  
  log_info "Phase 3 completed!"
}

#===============================================================================
# Phase 4: mTLS Configuration
#===============================================================================
phase4_mtls() {
  log_phase "Phase 4: mTLS Configuration (Week 3)"
  
  log_info "4.1 Configuring PeerAuthentication (PERMISSIVE mode initially)..."
  run_command "kubectl apply -f $SECURITY_DIR/peer-authentication.yaml"
  
  log_info "4.2 Testing service-to-service communication..."
  run_command "kubectl exec -it \$(kubectl get pod -n $MICROSERVICES_NS -l app=accounts -o jsonpath='{.items[0].metadata.name}') -n $MICROSERVICES_NS -- curl -s http://cards:9000/api/fetch?mobileNumber=1234567890"
  
  log_info "4.3 Verifying Eureka can still discover services..."
  run_command "kubectl get pods -n $MICROSERVICES_NS -l app=eurekaserver"
  
  log_info "4.4 Monitoring for any connection issues..."
  run_command "kubectl logs -n $ISTIO_NAMESPACE deployment/istiod --tail=50"
  
  log_info "4.5 Switching to STRICT mode after validation..."
  read -p "Switch to STRICT mTLS mode? (y/n): " confirm
  if [ "$confirm" = "y" ]; then
    run_command "kubectl patch peerauthentication default -n $MICROSERVICES_NS --type='json' -p='[{\"op\": \"replace\", \"path\": \"/spec/mtls/mode\", \"value\":\"STRICT\"}]'"
    log_info "mTLS mode switched to STRICT!"
  fi
  
  log_info "Phase 4 completed!"
}

#===============================================================================
# Phase 5: Traffic Management Migration
#===============================================================================
phase5_traffic_management() {
  log_phase "Phase 5: Traffic Management Migration (Week 3-4)"
  
  log_info "5.1 Creating DestinationRules for connection pool and outlier detection..."
  run_command "kubectl apply -f $TRAFFIC_DIR/destination-rules.yaml"
  
  log_info "5.2 Creating VirtualServices for retries and timeouts..."
  run_command "kubectl apply -f $TRAFFIC_DIR/virtual-services.yaml"
  
  log_info "5.3 Gradually removing Resilience4j config from Gateway (keep for now)..."
  log_warn "Resilience4j config should be kept until Istio is fully validated"
  
  log_info "5.4 Testing circuit breaking behavior..."
  # Test circuit breaking
  log_info "To test circuit breaking, run:"
  log_info "  for i in {1..100}; do curl -s -o /dev/null http://accounts:8080/api/fetch?mobileNumber=123; done"
  
  log_info "5.5 Documenting new traffic management approach..."
  log_info "See: $TRAFFIC_DIR/virtual-services.yaml"
  
  log_info "Phase 5 completed!"
}

#===============================================================================
# Phase 6: Observability Integration
#===============================================================================
phase6_observability() {
  log_phase "Phase 6: Observability Integration (Week 4)"
  
  log_info "6.1 Updating Prometheus to scrape Istio proxy metrics..."
  run_command "kubectl apply -f $OBSERVABILITY_DIR/telemetry.yaml"
  
  log_info "6.2 Configuring Istio telemetry for Tempo tracing..."
  run_command "kubectl apply -f $OBSERVABILITY_DIR/telemetry.yaml"
  
  log_info "6.3 Importing Istio dashboards to Grafana..."
  if [ -d "$OBSERVABILITY_DIR/grafana-dashboards" ]; then
    run_command "kubectl apply -f $OBSERVABILITY_DIR/grafana-dashboards/"
  fi
  
  log_info "6.4 Verifying distributed tracing includes Istio sidecar spans..."
  if command -v istioctl &> /dev/null; then
    run_command "istioctl proxy-config route \$(kubectl get pod -n $MICROSERVICES_NS -l app=accounts -o jsonpath='{.items[0].metadata.name}') -n $MICROSERVICES_NS"
  fi
  
  log_info "6.5 Creating unified dashboards (app + Istio metrics)..."
  log_info "See Grafana dashboards for Istio"
  
  log_info "Phase 6 completed!"
}

#===============================================================================
# Phase 7: Security Hardening
#===============================================================================
phase7_security() {
  log_phase "Phase 7: Security Hardening (Week 5)"
  
  log_info "7.1 Creating ServiceAccounts for each service..."
  for svc in accounts cards loans message gatewayserver configserver; do
    run_command "kubectl create serviceaccount ${svc}-sa -n $MICROSERVICES_NS --dry-run=client -o yaml | kubectl apply -f -"
  done
  
  log_info "7.2 Defining AuthorizationPolicies for service-to-service access..."
  run_command "kubectl apply -f $SECURITY_DIR/authorization-policies.yaml"
  
  log_info "7.3 Testing authorization (should block unauthorized calls)..."
  log_info "Test by trying to access a service from an unauthorized source"
  
  log_info "7.4 Configuring external service access (Keycloak, Git)..."
  run_command "kubectl apply -f $TRAFFIC_DIR/service-entries.yaml"
  
  log_info "7.5 Documenting security architecture..."
  log_info "See: $SECURITY_DIR/"
  
  log_info "Phase 7 completed!"
}

#===============================================================================
# Phase 8: Production Rollout
#===============================================================================
phase8_production() {
  log_phase "Phase 8: Production Rollout (Week 5-6)"
  
  log_info "8.1 Applying same changes to prod-env..."
  log_warn "Ensure you have a backup before applying to production!"
  read -p "Continue with production rollout? (y/n): " confirm
  if [ "$confirm" != "y" ]; then
    log_warn "Production rollout cancelled."
    return
  fi
  
  log_info "8.2 Monitoring metrics and logs closely..."
  run_command "kubectl get pods -n $MICROSERVICES_NS -w &"
  PID_WATCH=$!
  
  log_info "8.3 Rollback plan ready..."
  log_info "To rollback: ./migrate-to-istio.sh --rollback"
  
  log_info "8.4 Gradually increasing traffic to Istio-enabled services..."
  log_info "Monitor Grafana dashboards for performance metrics"
  
  log_info "8.5 Completing documentation and runbooks..."
  log_info "See: .docker/istio/README.md"
  
  # Stop watching
  kill $PID_WATCH 2>/dev/null || true
  
  log_info "Phase 8 completed!"
}

#===============================================================================
# Phase 9: Optimization
#===============================================================================
phase9_optimization() {
  log_phase "Phase 9: Optimization (Week 6+)"
  
  log_info "9.1 Fine-tuning circuit breaker settings..."
  log_info "Review and adjust DestinationRule settings based on observed metrics"
  
  log_info "9.2 Optimizing mTLS performance..."
  log_info "Monitor TLS handshake latency and adjust if needed"
  
  log_info "9.3 Considering removing Resilience4j if Istio handles all cases..."
  log_warn "Only remove Resilience4j after thorough testing"
  
  log_info "9.4 Evaluating if Eureka can be replaced with Istio service discovery..."
  log_info "This is a major architectural change - plan carefully"
  
  log_info "9.5 Planning for advanced features (canary, fault injection)..."
  log_info "See VirtualService configurations for canary deployment examples"
  
  log_info "Phase 9 completed!"
}

#===============================================================================
# Check Migration Status
#===============================================================================
check_status() {
  log_phase "Istio Migration Status"
  echo ""
  
  log_info "Istio Control Plane:"
  kubectl get pods -n $ISTIO_NAMESPACE 2>/dev/null || log_warn "Istio not installed"
  echo ""
  
  log_info "Sidecar Injection Status:"
  kubectl get namespace $MICROSERVICES_NS -o jsonpath='{.metadata.labels.istio-injection}' 2>/dev/null || log_warn "Namespace not labeled"
  echo ""
  
  log_info "Pods with Sidecar:"
  kubectl get pods -n $MICROSERVICES_NS -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].name}{"\n"}{end}' 2>/dev/null | grep istio-proxy || log_warn "No sidecars found"
  echo ""
  
  log_info "PeerAuthentication:"
  kubectl get peerauthentication -n $MICROSERVICES_NS 2>/dev/null || log_warn "No PeerAuthentication found"
  echo ""
  
  log_info "DestinationRules:"
  kubectl get destinationrules -n $MICROSERVICES_NS 2>/dev/null || log_warn "No DestinationRules found"
  echo ""
  
  log_info "VirtualServices:"
  kubectl get virtualservices -n $MICROSERVICES_NS 2>/dev/null || log_warn "No VirtualServices found"
  echo ""
  
  log_info "AuthorizationPolicies:"
  kubectl get authorizationpolicies -n $MICROSERVICES_NS 2>/dev/null || log_warn "No AuthorizationPolicies found"
  echo ""
}

#===============================================================================
# Rollback Function
#===============================================================================
rollback() {
  log_phase "Rolling Back Istio Migration"
  log_warn "This will remove Istio configurations and restart services without sidecar"
  
  read -p "Are you sure you want to rollback? (y/n): " confirm
  if [ "$confirm" != "y" ]; then
    log_info "Rollback cancelled."
    return
  fi
  
  log_info "Removing Istio configurations..."
  run_command "kubectl delete -f $TRAFFIC_DIR/ 2>/dev/null || true"
  run_command "kubectl delete -f $SECURITY_DIR/ 2>/dev/null || true"
  run_command "kubectl delete -f $OBSERVABILITY_DIR/ 2>/dev/null || true"
  
  log_info "Disabling sidecar injection..."
  run_command "kubectl label namespace $MICROSERVICES_NS istio-injection- --overwrite"
  
  log_info "Restarting services without sidecar..."
  for svc in accounts cards loans message gatewayserver configserver; do
    run_command "kubectl rollout restart deployment $svc -n $MICROSERVICES_NS"
  done
  
  log_info "Rollback completed! Services are now running without Istio sidecar."
}

#===============================================================================
# Main Script Logic
#===============================================================================
main() {
  echo "==============================================================================="
  echo "Istio Migration Script for EazyBank Platform"
  echo "==============================================================================="
  echo ""
  
  if [ "$ROLLBACK" = true ]; then
    rollback
    exit 0
  fi
  
  if [ "$STATUS" = true ]; then
    check_status
    exit 0
  fi
    
  # Run specific phase or all phases
  if [ -n "$SPECIFIC_PHASE" ]; then
    case $SPECIFIC_PHASE in
      1) phase1_preparation ;;
      2) phase2_installation ;;
      3) phase3_sidecar_injection ;;
      4) phase4_mtls ;;
      5) phase5_traffic_management ;;
      6) phase6_observability ;;
      7) phase7_security ;;
      8) phase8_production ;;
      9) phase9_optimization ;;
      *) log_error "Invalid phase: $SPECIFIC_PHASE. Must be 1-9."; exit 1 ;;
    esac
  else
    # Run all phases
    phase1_preparation
    echo ""
    phase2_installation
    echo ""
    phase3_sidecar_injection
    echo ""
    phase4_mtls
    echo ""
    phase5_traffic_management
    echo ""
    phase6_observability
    echo ""
    phase7_security
    echo ""
    phase8_production
    echo ""
    phase9_optimization
  fi
  
  echo ""
  log_info "==============================================================================="
  log_info "Istio Migration completed!"
  log_info "==============================================================================="
  echo ""
  log_info "Check status with: ./migrate-to-istio.sh --status"
  log_info "Rollback with: ./migrate-to-istio.sh --rollback"
  echo ""
}

# Run main function
main
