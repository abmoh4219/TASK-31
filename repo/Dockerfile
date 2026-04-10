# Multi-stage build for Retail Campaign Governance.
# Stage 1: build the Spring Boot fat JAR with Maven.
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper + descriptor first to maximize layer cache hits.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline --no-transfer-progress || true

# Copy sources and build the fat JAR (skip tests — tests run in Dockerfile.test).
COPY src/ src/
RUN ./mvnw -q clean package -DskipTests --no-transfer-progress

# Stage 2: minimal runtime image.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# mysql-client is needed at runtime for BackupService (mysqldump).
RUN apk add --no-cache mysql-client

# Copy the built JAR from the builder stage.
COPY --from=builder /build/target/retail-campaign.jar /app/app.jar

# Create the upload + backup directories that volumes will mount over.
RUN mkdir -p /app/uploads /app/backups

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
