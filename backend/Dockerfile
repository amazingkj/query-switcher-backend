# Build stage
FROM --platform=linux/amd64 eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Install gradle
RUN apt-get update && apt-get install -y wget unzip && \
    wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip && \
    unzip -q gradle-8.5-bin.zip && \
    rm gradle-8.5-bin.zip

ENV PATH="/app/gradle-8.5/bin:${PATH}"

# Copy all build files
COPY build.gradle.kts settings.gradle* ./

# Copy source code
COPY src ./src

# Build application
RUN gradle bootJar --no-daemon

# Runtime stage
FROM --platform=linux/amd64 eclipse-temurin:21-jre

WORKDIR /app

# Set JAR file path
ARG JAR_FILE_PATH=build/libs/*.jar

# Copy jar from builder
COPY --from=builder /app/${JAR_FILE_PATH} sql-converter.jar

# 사용자 생성 및 디렉토리 준비
RUN groupadd -g 1000 appuser && \
    useradd -r -u 1000 -g appuser appuser && \
    chmod 644 sql-converter.jar && \
    mkdir -p /home/logs/sql-converter && \
    chown -R appuser:appuser /home && \
    chown -R appuser:appuser /app

USER appuser

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "--add-opens=java.base/java.net=ALL-UNNAMED", "-jar", "sql-converter.jar"]