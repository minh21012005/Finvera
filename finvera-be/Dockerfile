# Stage 1: Build JAR using Maven on JDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production package
COPY src ./src
RUN mvn clean package -DskipTests -B && \
    cp target/*.jar app.jar

# Stage 2: Minimal runtime image with JRE 21
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user for security
RUN addgroup -S finvera && adduser -S finvera -G finvera
USER finvera

COPY --from=builder --chown=finvera:finvera /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
