# ── Stage 1: build ────────────────────────────────────────────────────────────
# NOTE: Build from monorepo root:  docker build -f discovery-server/Dockerfile -t eureka .
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
RUN mvn dependency:go-offline -q -pl discovery-server -am

# Copy source and build
COPY discovery-server/src ./discovery-server/src
RUN mvn package -DskipTests -q -pl discovery-server

# ── Stage 2: run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/discovery-server/target/*.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
