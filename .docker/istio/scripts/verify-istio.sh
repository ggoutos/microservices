#!/bin/bash
#===============================================================================
# Istio Verification Script for EazyBank Platform
#===============================================================================
# This script verifies that Istio is correctly installed and configured.
#
# Usage: ./verify-istio.sh [OPTIONS]
# Options:
#   --quick      : Run quick verification only
#   --detailed  : Run detailed verification (default)
#   --fix        : Attempt to fix common issues
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

# Parse command line arguments
QUICK_MODE=false
DETAILED_MODE=true
FIX_MODE=false

for arg in "$@"; do
  case $arg in
    --quick)
      QUICK_MODE=true
      DETAILED_MODE=false
      shift
      ;;
    --detailed)
      QUICK_MODE=false
      DETAILED_MODE=true
      shift
      ;;
    --fix)
      FIX_MODE=true
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --quick      : Run quick verification only"
      echo "  --detailed  : Run detailed verification (default)"
      echo "  --fix        : Attempt to fix common issues"
      echo "  --help       : Show this help message"
      exit 0
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

log_section() {
  echo ""
  echo -e "${BLUE}=== $1 ===${NC}"
}

check_passed() {
  echo -e "  ${GREEN}✓ PASSED${NC}: $1"
}

check_failed() {
  echo -e "  ${RED}✗ FAILED${NC}: $1"
}

check_warning() {
  echo -e "  ${YELLOW}⚠ WARNING${NC}: $1"
}

#===============================================================================
# Verification Functions
#===============================================================================
check_istio_installation() {
  log_section "Checking Istio Installation"
  
  # Check Istio namespace
  if kubectl get namespace $ISTIO_NAMESPACE &> /dev/null; then
    check_passed "Istio namespace exists"
  else
    check_failed "Istio namespace not found"
    if [ "$FIX_MODE" = true ]; then
      log_info "Creating Istio namespace..."
      kubectl create namespace $ISTIO_NAMESPACE
    fi
    return 1
  fi
  
  # Check Istio pods
  local ISTIO_PODS=$(kubectl get pods -n $ISTIO_NAMESPACE --no-headers 2>/dev/null | wc -l)
  if [ "$ISTIO_PODS" -gt 0 ]; then
    check_passed "Istio pods are running ($ISTIO_PODS pods)"
    kubectl get pods -n $ISTIO_NAMESPACE
  else
    check_failed "No Istio pods found in $ISTIO_NAMESPACE"
    return 1
  fi
  
  # Check istiod
  if kubectl get svc istiod -n $ISTIO_NAMESPACE &> /dev/null; then
    check_passed "Istiod control plane is running"
  else
    check_failed "Istiod control plane not found"
    return 1
  fi
  
  # Check ingress gateway
  if kubectl get svc istio-ingress -n $ISTIO_NAMESPACE &> /dev/null; then
    check_passed "Istio Ingress Gateway is installed"
  else
    check_warn "Istio Ingress Gateway not found (optional)"
  fi
}

check_sidecar_injection() {
  log_section "Checking Sidecar Injection"
  
  # Check namespace label
  local INJECTION_LABEL=$(kubectl get namespace $MICROSERVICES_NS -o jsonpath='{.metadata.labels.istio-injection}' 2>/dev/null)
  if [ "$INJECTION_LABEL" = "enabled" ]; then
    check_passed "Namespace $MICROSERVICES_NS has sidecar injection enabled"
  else
    check_failed "Namespace $MICROSERVICES_NS does not have istio-injection=enabled"
    if [ "$FIX_MODE" = true ]; then
      log_info "Enabling sidecar injection..."
      kubectl label namespace $MICROSERVICES_NS istio-injection=enabled --overwrite
    fi
  fi
  
  # Check pods for sidecar
  local PODS_WITH_SIDECAR=0
  local TOTAL_PODS=0
  
  while read -r POD CONTAINERS; do
    TOTAL_PODS=$((TOTAL_PODS + 1))
    if echo "$CONTAINERS" | grep -q "istio-proxy"; then
      PODS_WITH_SIDECAR=$((PODS_WITH_SIDECAR + 1))
    fi
  done < <(kubectl get pods -n $MICROSERVICES_NS --no-headers -o custom-columns="NAME:.metadata.name,CONTAINERS:.spec.containers[*].name" 2>/dev/null)
  
  if [ "$TOTAL_PODS" -eq 0 ]; then
    check_warn "No pods found in $MICROSERVICES_NS namespace"
  elif [ "$PODS_WITH_SIDECAR" -eq "$TOTAL_PODS" ]; then
    check_passed "All $TOTAL_PODS pods have istio-proxy sidecar"
  else
    check_failed "Only $PODS_WITH_SIDECAR out of $TOTAL_PODS pods have istio-proxy sidecar"
    log_info "Pods without sidecar:"
    kubectl get pods -n $MICROSERVICES_NS -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].name}{"\n"}{end}' | grep -v "istio-proxy"
  fi
}

check_mtls() {
  log_section "Checking mTLS Configuration"
  
  # Check PeerAuthentication
  local PEER_AUTH=$(kubectl get peerauthentication -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$PEER_AUTH" -gt 0 ]; then
    check_passed "PeerAuthentication resources found ($PEER_AUTH)"
    kubectl get peerauthentication -n $MICROSERVICES_NS
  else
    check_warn "No PeerAuthentication resources found"
  fi
  
  # Check mTLS mode using istioctl (if available)
  if command -v istioctl &> /dev/null; then
    log_info "Running istioctl authn tls-check..."
    istioctl authn tls-check -n $MICROSERVICES_NS 2>/dev/null || check_warn "Some services may not have mTLS configured"
  else
    check_warn "istioctl not installed - skipping mTLS verification"
  fi
}

check_traffic_management() {
  log_section "Checking Traffic Management"
  
  # Check DestinationRules
  local DEST_RULES=$(kubectl get destinationrules -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$DEST_RULES" -gt 0 ]; then
    check_passed "DestinationRule resources found ($DEST_RULES)"
    kubectl get destinationrules -n $MICROSERVICES_NS
  else
    check_warn "No DestinationRule resources found"
  fi
  
  # Check VirtualServices
  local VIRT_SERVICES=$(kubectl get virtualservices -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$VIRT_SERVICES" -gt 0 ]; then
    check_passed "VirtualService resources found ($VIRT_SERVICES)"
    kubectl get virtualservices -n $MICROSERVICES_NS
  else
    check_warn "No VirtualService resources found"
  fi
}

check_security_policies() {
  log_section "Checking Security Policies"
  
  # Check AuthorizationPolicies
  local AUTHZ_POLICIES=$(kubectl get authorizationpolicies -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$AUTHZ_POLICIES" -gt 0 ]; then
    check_passed "AuthorizationPolicy resources found ($AUTHZ_POLICIES)"
    kubectl get authorizationpolicies -n $MICROSERVICES_NS
  else
    check_warn "No AuthorizationPolicy resources found"
  fi
  
  # Check RequestAuthentications
  local REQ_AUTH=$(kubectl get requestauthentications -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$REQ_AUTH" -gt 0 ]; then
    check_passed "RequestAuthentication resources found ($REQ_AUTH)"
    kubectl get requestauthentications -n $MICROSERVICES_NS
  else
    check_warn "No RequestAuthentication resources found"
  fi
}

check_observability() {
  log_section "Checking Observability Integration"
  
  # Check Telemetry resources
  local TELEMETRY=$(kubectl get telemetry -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$TELEMETRY" -gt 0 ]; then
    check_passed "Telemetry resources found ($TELEMETRY)"
    kubectl get telemetry -n $MICROSERVICES_NS
  else
    check_warn "No Telemetry resources found"
  fi
  
  # Check if Prometheus can scrape Istio metrics
  if kubectl get svc prometheus -n prometheus &> /dev/null; then
    check_passed "Prometheus is installed"
  else
    check_warn "Prometheus not found in 'prometheus' namespace"
  fi
}

check_service_entries() {
  log_section "Checking Service Entries"
  
  local SVC_ENTRIES=$(kubectl get serviceentry -n $MICROSERVICES_NS --no-headers 2>/dev/null | wc -l)
  if [ "$SVC_ENTRIES" -gt 0 ]; then
    check_passed "ServiceEntry resources found ($SVC_ENTRIES)"
    kubectl get serviceentry -n $MICROSERVICES_NS
  else
    check_warn "No ServiceEntry resources found"
  fi
}

check_proxy_config() {
  if [ "$DETAILED_MODE" = false ]; then
    return
  fi
  
  log_section "Checking Proxy Configuration (Sample Pod)"
  
  # Get first pod with istio-proxy
  local SAMPLE_POD=$(kubectl get pods -n $MICROSERVICES_NS -l "app in (accounts,cards,loans)" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
  
  if [ -z "$SAMPLE_POD" ]; then
    check_warn "No sample pod found for proxy config check"
    return
  fi
  
  check_passed "Using pod: $SAMPLE_POD"
  
  # Check proxy status
  if command -v istioctl &> /dev/null; then
    log_info "Proxy status:"
    istioctl proxy-status $SAMPLE_POD -n $MICROSERVICES_NS 2>/dev/null || check_warn "Could not get proxy status"
    
    log_info "Proxy config (clusters):"
    istioctl proxy-config cluster $SAMPLE_POD -n $MICROSERVICES_NS 2>/dev/null | head -20 || check_warn "Could not get proxy config"
  else
    check_warn "istioctl not installed - skipping proxy config verification"
  fi
}

#===============================================================================
# Main Script Logic
#===============================================================================
main() {
  echo "==============================================================================="
  echo "Istio Verification Script for EazyBank Platform"
  echo "==============================================================================="
  echo ""
  
  # Run all checks
  check_istio_installation
  echo ""
  
  check_sidecar_injection
  echo ""
  
  if [ "$QUICK_MODE" = false ]; then
    check_mtls
    echo ""
    
    check_traffic_management
    echo ""
    
    check_security_policies
    echo ""
    
    check_observability
    echo ""
    
    check_service_entries
    echo ""
    
    check_proxy_config
    echo ""
  fi
  
  echo "==============================================================================="
  log_info "Verification completed!"
  echo "==============================================================================="
  echo ""
  log_info "Next steps:"
  log_info "1. Review any FAILED or WARNING items above"
  log_info "2. Run with --fix to attempt automatic fixes"
  log_info "3. Check Istio logs: kubectl logs -n $ISTIO_NAMESPACE deployment/istiod"
  echo ""
}

# Run main function
main
