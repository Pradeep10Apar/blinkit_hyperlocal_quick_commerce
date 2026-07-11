# ── Stage 1: build ────────────────────────────────────────────────────────────
# NOTE: Build from monorepo root:  docker build -f blinkit-app/Dockerfile -t blinkit-app .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM and ALL module POMs (Maven validates all modules in parent)
COPY pom.xml ./pom.xml
COPY blinkit-app/pom.xml ./blinkit-app/pom.xml
COPY inventory-service/pom.xml ./inventory-service/pom.xml
COPY inventory-api/pom.xml ./inventory-api/pom.xml
COPY discovery-server/pom.xml ./discovery-server/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml

# Download dependencies for this module
RUN mvn dependency:go-offline -q -pl blinkit-app,inventory-api -am

# Copy only this module's source and build
COPY inventory-api/src ./inventory-api/src
COPY blinkit-app/src ./blinkit-app/src
RUN mvn package -DskipTests -q -pl blinkit-app,inventory-api -am

# ── Stage 2: run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# This is where the named volume will be mounted
RUN mkdir -p /app/images

COPY --from=build /app/blinkit-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
