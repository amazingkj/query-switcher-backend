import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
}

group = "com.sqlswitcher"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // SQL Parser
    implementation("com.github.jsqlparser:jsqlparser:4.9")
    
    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Metrics
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Database
    implementation("com.h2database:h2")
    implementation("org.postgresql:postgresql")
    implementation("com.mysql:mysql-connector-j:8.2.0")

    // Testcontainers (for SQL validation)
    implementation("org.testcontainers:testcontainers:1.19.3")
    implementation("org.testcontainers:mysql:1.19.3")
    implementation("org.testcontainers:postgresql:1.19.3")
    implementation("org.testcontainers:oracle-xe:1.19.3")

    // JSON Processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Rate Limiting
    implementation("com.bucket4j:bucket4j-core:8.10.0")
    implementation("com.bucket4j:bucket4j-redis:8.10.0")
    
    // OpenAPI Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    mainClass.set("com.sqlswitcher.SqlSwitcherApplicationKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
