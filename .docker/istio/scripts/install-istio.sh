#!/bin/bash
#===============================================================================
# Istio Automated Installation Script for EazyBank Platform
#===============================================================================
# This script automates the installation of Istio service mesh using Helm.
# 
# Usage: ./install-istio.sh [OPTIONS]
# Options:
#   --dry-run    : Print commands without executing
#   --uninstall  : Uninstall Istio
#   --verify     : Verify existing installation
#   --help       : Show this help message
#===============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ISTIO_VERSION="1.21.0"
ISTIO_NAMESPACE="istio-system"
VALUES_FILE="$(dirname "$0")/base/istio-values.yaml"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Parse command line arguments
DRY_RUN=false
UNINSTALL=false
VERIFY=false

for arg in "$@"; do
  case $arg in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --uninstall)
      UNINSTALL=true
      shift
      ;;
    --verify)
      VERIFY=true
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --dry-run    : Print commands without executing"
      echo "  --uninstall  : Uninstall Istio"
      echo "  --verify     : Verify existing installation"
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

run_command() {
  if [ "$DRY_RUN" = true ]; then
    echo "DRY RUN: $1"
  else
    log_info "Executing: $1"
    eval "$1"
  fi
}

#===============================================================================
# Prerequisites Check
#===============================================================================
check_prerequisites() {
  log_info "Checking prerequisites..."
  
  # Check kubectl
  if ! command -v kubectl &> /dev/null; then
    log_error "kubectl is not installed. Please install kubectl first."
    exit 1
  fi
  
  # Check helm
  if ! command -v helm &> /dev/null; then
    log_error "Helm is not installed. Please install Helm first."
    exit 1
  fi
  
  # Check istioctl (optional)
  if ! command -v istioctl &> /dev/null; then
    log_warn "istioctl is not installed. Some verification features will be disabled."
  fi
  
  # Check Kubernetes connection
  if ! kubectl cluster-info &> /dev/null; then
    log_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
    exit 1
  fi
  
  # Check Kubernetes version
  K8S_VERSION=$(kubectl version -o json | jq -r '.serverVersion.minor' 2>/dev/null || echo "0")
  if [ "$K8S_VERSION" -lt 28 ] 2>/dev/null; then
    log_warn "Kubernetes version may be too old. Istio 1.21+ requires Kubernetes 1.28+"
  fi
  
  log_info "Prerequisites check passed!"
}

#===============================================================================
# Add Istio Helm Repository
#===============================================================================
add_helm_repo() {
  log_info "Adding Istio Helm repository..."
  run_command "helm repo add istio https://istio-release.storage.googleapis.com/charts"
  run_command "helm repo update"
}

#===============================================================================
# Create Istio Namespace
#===============================================================================
create_namespace() {
  log_info "Creating Istio namespace..."
  run_command "kubectl create namespace $ISTIO_NAMESPACE --dry-run=client -o yaml | kubectl apply -f -"
  run_command "kubectl label namespace $ISTIO_NAMESPACE istio-injection=disabled --overwrite"
}

#===============================================================================
# Install Istio Base (CRDs)
#===============================================================================
install_istio_base() {
  log_info "Installing Istio Base (CRDs)..."
  run_command "helm install istio-base istio/base --namespace $ISTIO_NAMESPACE --set defaultRevision=default"
}

#===============================================================================
# Install Istiod (Control Plane)
#===============================================================================
install_istiod() {
  log_info "Installing Istiod (Control Plane)..."
  
  if [ ! -f "$VALUES_FILE" ]; then
    log_error "Values file not found: $VALUES_FILE"
    exit 1
  fi
  
  run_command "helm install istiod istio/istiod --namespace $ISTIO_NAMESPACE --values $VALUES_FILE"
}

#===============================================================================
# Install Istio Ingress Gateway
#===============================================================================
install_ingress_gateway() {
  log_info "Installing Istio Ingress Gateway..."
  run_command "helm install istio-ingress istio/gateway --namespace $ISTIO_NAMESPACE --values $VALUES_FILE"
}

#===============================================================================
# Verify Installation
#===============================================================================
verify_installation() {
  log_info "Verifying Istio installation..."
  
  # Check pods
  log_info "Checking Istio pods..."
  kubectl get pods -n $ISTIO_NAMESPACE
  
  # Check services
  log_info "Checking Istio services..."
  kubectl get svc -n $ISTIO_NAMESPACE
  
  # Run istioctl verify-install if available
  if command -v istioctl &> /dev/null; then
    log_info "Running istioctl verify-install..."
    istioctl verify-install -n $ISTIO_NAMESPACE
  fi
  
  # Check control plane
  log_info "Checking Istiod status..."
  kubectl get svc istiod -n $ISTIO_NAMESPACE
}

#===============================================================================
# Uninstall Istio
#===============================================================================
uninstall_istio() {
  log_warn "Uninstalling Istio..."
  
  run_command "helm uninstall istio-ingress -n $ISTIO_NAMESPACE"
  run_command "helm uninstall istiod -n $ISTIO_NAMESPACE"
  run_command "helm uninstall istio-base -n $ISTIO_NAMESPACE"
  
  log_warn "Deleting Istio namespace..."
  run_command "kubectl delete namespace $ISTIO_NAMESPACE"
  
  log_info "Istio uninstalled!"
}

#===============================================================================
# Apply Security and Traffic Configurations
#===============================================================================
apply_configurations() {
  log_info "Applying Istio configurations..."
  
  local SECURITY_DIR="$SCRIPT_DIR/security"
  local TRAFFIC_DIR="$SCRIPT_DIR/traffic"
  local OBSERVABILITY_DIR="$SCRIPT_DIR/observability"
  
  # Apply namespace configuration
  if [ -f "$SCRIPT_DIR/base/namespace.yaml" ]; then
    log_info "Applying namespace configuration..."
    run_command "kubectl apply -f $SCRIPT_DIR/base/namespace.yaml"
  fi
  
  # Apply security configurations
  if [ -d "$SECURITY_DIR" ]; then
    log_info "Applying security configurations..."
    run_command "kubectl apply -f $SECURITY_DIR/"
  fi
  
  # Apply traffic configurations
  if [ -d "$TRAFFIC_DIR" ]; then
    log_info "Applying traffic configurations..."
    run_command "kubectl apply -f $TRAFFIC_DIR/"
  fi
  
  # Apply observability configurations
  if [ -d "$OBSERVABILITY_DIR" ]; then
    log_info "Applying observability configurations..."
    run_command "kubectl apply -f $OBSERVABILITY_DIR/"
  fi
}

#===============================================================================
# Main Script Logic
#===============================================================================
main() {
  echo "==============================================================================="
  echo "Istio Installation Script for EazyBank Platform"
  echo "==============================================================================="
  echo ""
  
  if [ "$UNINSTALL" = true ]; then
    uninstall_istio
    exit 0
  fi
  
  if [ "$VERIFY" = true ]; then
    verify_installation
    exit 0
  fi
  
  # Installation flow
  check_prerequisites
  add_helm_repo
  create_namespace
  install_istio_base
  install_istiod
  install_ingress_gateway
  
  log_info "Waiting for pods to be ready..."
  if [ "$DRY_RUN" = false ]; then
    kubectl wait --for=condition=ready pod -l app=istiod -n $ISTIO_NAMESPACE --timeout=300s
    kubectl wait --for=condition=ready pod -l istio=ingressgateway -n $ISTIO_NAMESPACE --timeout=300s
  fi
  
  verify_installation
  apply_configurations
  
  echo ""
  log_info "==============================================================================="
  log_info "Istio installation completed successfully!"
  log_info "==============================================================================="
  echo ""
  log_info "Next steps:"
  log_info "1. Enable sidecar injection: kubectl label namespace microservices istio-injection=enabled"
  log_info "2. Restart your services to inject sidecar"
  log_info "3. Run migration script: ./migrate-to-istio.sh"
  echo ""
}

# Run main function
main
