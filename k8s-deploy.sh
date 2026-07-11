#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  Blinkit Kubernetes Deployment Script (Mac/Linux)
# ═══════════════════════════════════════════════════════════════════════════════

# Don't exit on error - handle errors gracefully
# set -e

echo ""
echo "╔═══════════════════════════════════════════════════════════════════════════════╗"
echo "║                    BLINKIT KUBERNETES DEPLOYMENT                              ║"
echo "╚═══════════════════════════════════════════════════════════════════════════════╝"
echo ""

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "ERROR: kubectl not found. Please install kubectl or enable Kubernetes in Docker Desktop."
    exit 1
fi

echo "[1/7] Checking Kubernetes cluster..."
if ! kubectl cluster-info &> /dev/null; then
    echo "ERROR: Cannot connect to Kubernetes cluster. Enable Kubernetes in Docker Desktop."
    exit 1
fi
echo "      ✓ Kubernetes cluster is running"

echo ""
echo "[2/7] Checking Docker images..."

# Function to check if image exists
image_exists() {
    docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^$1$"
}

# Check and build only if needed
IMAGES_NEEDED=false

for img in "eureka:v1" "api-gateway:v1" "blinkit-app:v1" "inventory-service:v1"; do
    if ! image_exists "$img"; then
        IMAGES_NEEDED=true
        break
    fi
done

if [ "$IMAGES_NEEDED" = true ]; then
    echo "      Some images missing. Building Docker images (this may take a few minutes)..."
    
    if ! image_exists "eureka:v1"; then
        echo "      Building eureka..."
        if docker build -f discovery-server/Dockerfile -t eureka:v1 . ; then
            echo "      ✓ eureka:v1 built"
        else
            echo "      ✗ Failed to build eureka:v1"
            exit 1
        fi
    fi
    
    if ! image_exists "api-gateway:v1"; then
        echo "      Building api-gateway..."
        if docker build -f api-gateway/Dockerfile -t api-gateway:v1 . ; then
            echo "      ✓ api-gateway:v1 built"
        else
            echo "      ✗ Failed to build api-gateway:v1"
            exit 1
        fi
    fi
    
    if ! image_exists "blinkit-app:v1"; then
        echo "      Building blinkit-app..."
        if docker build -f blinkit-app/Dockerfile -t blinkit-app:v1 . ; then
            echo "      ✓ blinkit-app:v1 built"
        else
            echo "      ✗ Failed to build blinkit-app:v1"
            exit 1
        fi
    fi
    
    if ! image_exists "inventory-service:v1"; then
        echo "      Building inventory-service..."
        if docker build -f inventory-service/Dockerfile -t inventory-service:v1 . ; then
            echo "      ✓ inventory-service:v1 built"
        else
            echo "      ✗ Failed to build inventory-service:v1"
            exit 1
        fi
    fi
else
    echo "      ✓ All Docker images already exist:"
    echo "        - eureka:v1"
    echo "        - api-gateway:v1"
    echo "        - blinkit-app:v1"
    echo "        - inventory-service:v1"
    echo "      (Skipping build step)"
fi

echo ""
echo "[3/7] Creating namespace..."
kubectl apply -f k8s/00-namespace.yaml
echo "      ✓ Namespace created"

echo ""
echo "[4/7] Deploying infrastructure (PostgreSQL, Redis, Kafka, Elasticsearch)..."
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/02-redis.yaml
kubectl apply -f k8s/03-kafka.yaml
kubectl apply -f k8s/04-elasticsearch.yaml
echo "      ✓ Infrastructure deployed"

echo ""
echo "[5/7] Waiting for infrastructure to be ready (60 seconds)..."
sleep 60
echo "      ✓ Wait complete"

echo ""
echo "[6/7] Deploying application services..."
kubectl apply -f k8s/10-eureka.yaml
echo "      Waiting for Eureka to start (30 seconds)..."
sleep 30
kubectl apply -f k8s/11-blinkit-app.yaml
kubectl apply -f k8s/12-inventory-service.yaml
kubectl apply -f k8s/13-api-gateway.yaml
echo "      ✓ Application services deployed"

echo ""
echo "[7/7] Deployment complete!"
echo ""
echo "╔═══════════════════════════════════════════════════════════════════════════════╗"
echo "║                           DEPLOYMENT SUMMARY                                  ║"
echo "╠═══════════════════════════════════════════════════════════════════════════════╣"
echo "║  API Gateway:    http://localhost:30085                                       ║"
echo "║  Eureka:         kubectl port-forward svc/eureka 8761:8761 -n blinkit        ║"
echo "║                                                                               ║"
echo "║  Commands:                                                                    ║"
echo "║  - kubectl get pods -n blinkit                (see all pods)                 ║"
echo "║  - kubectl get svc -n blinkit                 (see all services)             ║"
echo "║  - kubectl logs -f deployment/blinkit-app -n blinkit  (view logs)            ║"
echo "║  - kubectl get hpa -n blinkit                 (see autoscalers)              ║"
echo "╚═══════════════════════════════════════════════════════════════════════════════╝"
echo ""
