# ── Stage 1: build ────────────────────────────────────────────────────────────
# NOTE: Build from monorepo root:  docker build -f api-gateway/Dockerfile -t api-gateway .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent POM and module POMs
COPY pom.xml ./pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY blinkit-app/pom.xml ./blinkit-app/pom.xml
COPY inventory-service/pom.xml ./inventory-service/pom.xml
COPY inventory-api/pom.xml ./inventory-api/pom.xml
COPY discovery-server/pom.xml ./discovery-server/pom.xml

# Download dependencies
RUN mvn dependency:go-offline -q -pl api-gateway -am

# Copy source and build
COPY api-gateway/src ./api-gateway/src
RUN mvn package -DskipTests -q -pl api-gateway

# ── Stage 2: run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/api-gateway/target/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
