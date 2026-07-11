@echo off
REM ═══════════════════════════════════════════════════════════════════════════════
REM  Blinkit Kubernetes Deployment Script (Windows)
REM ═══════════════════════════════════════════════════════════════════════════════

echo.
echo ╔═══════════════════════════════════════════════════════════════════════════════╗
echo ║                    BLINKIT KUBERNETES DEPLOYMENT                              ║
echo ╚═══════════════════════════════════════════════════════════════════════════════╝
echo.

REM Check if kubectl is available
kubectl version --client >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: kubectl not found. Please install kubectl or enable Kubernetes in Docker Desktop.
    exit /b 1
)

echo [1/7] Checking Kubernetes cluster...
kubectl cluster-info >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster. Enable Kubernetes in Docker Desktop.
    exit /b 1
)
echo       ✓ Kubernetes cluster is running

echo.
echo [2/7] Building Docker images (this may take a few minutes)...
echo       Building eureka...
docker build -f discovery-server/Dockerfile -t eureka:v1 . >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Failed to build eureka image
    exit /b 1
)
echo       ✓ eureka:v1 built

echo       Building api-gateway...
docker build -f api-gateway/Dockerfile -t api-gateway:v1 . >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Failed to build api-gateway image
    exit /b 1
)
echo       ✓ api-gateway:v1 built

echo       Building blinkit-app...
docker build -f blinkit-app/Dockerfile -t blinkit-app:v1 . >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Failed to build blinkit-app image
    exit /b 1
)
echo       ✓ blinkit-app:v1 built

echo       Building inventory-service...
docker build -f inventory-service/Dockerfile -t inventory-service:v1 . >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Failed to build inventory-service image
    exit /b 1
)
echo       ✓ inventory-service:v1 built

echo.
echo [3/7] Creating namespace...
kubectl apply -f k8s/00-namespace.yaml
echo       ✓ Namespace created

echo.
echo [4/7] Deploying infrastructure (PostgreSQL, Redis, Kafka, Elasticsearch)...
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/02-redis.yaml
kubectl apply -f k8s/03-kafka.yaml
kubectl apply -f k8s/04-elasticsearch.yaml
echo       ✓ Infrastructure deployed

echo.
echo [5/7] Waiting for infrastructure to be ready (60 seconds)...
timeout /t 60 /nobreak >nul
echo       ✓ Wait complete

echo.
echo [6/7] Deploying application services...
kubectl apply -f k8s/10-eureka.yaml
echo       Waiting for Eureka to start (30 seconds)...
timeout /t 30 /nobreak >nul
kubectl apply -f k8s/11-blinkit-app.yaml
kubectl apply -f k8s/12-inventory-service.yaml
kubectl apply -f k8s/13-api-gateway.yaml
echo       ✓ Application services deployed

echo.
echo [7/7] Deployment complete!
echo.
echo ╔═══════════════════════════════════════════════════════════════════════════════╗
echo ║                           DEPLOYMENT SUMMARY                                  ║
echo ╠═══════════════════════════════════════════════════════════════════════════════╣
echo ║  API Gateway:    http://localhost:30085                                       ║
echo ║  Eureka:         kubectl port-forward svc/eureka 8761:8761 -n blinkit        ║
echo ║                                                                               ║
echo ║  Commands:                                                                    ║
echo ║  - kubectl get pods -n blinkit                (see all pods)                 ║
echo ║  - kubectl get svc -n blinkit                 (see all services)             ║
echo ║  - kubectl logs -f deployment/blinkit-app -n blinkit  (view logs)            ║
echo ║  - kubectl get hpa -n blinkit                 (see autoscalers)              ║
echo ╚═══════════════════════════════════════════════════════════════════════════════╝
echo.

pause
