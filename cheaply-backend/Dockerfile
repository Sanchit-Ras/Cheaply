# Multi-stage build for the Spring Boot backend.
#
# The builder uses the official Gradle image rather than the wrapper: the
# wrapper's first step is a 130 MB download of the Gradle distribution inside
# the build container, which proved unreliable on some Docker Desktop
# networks. With Gradle preinstalled, the only downloads left are the
# project's dependencies from Maven Central.
FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /app

# Config first, so the dependency layer caches until build.gradle changes.
COPY build.gradle settings.gradle ./

# Warm the dependency cache; tolerated to fail so the real build reports errors.
RUN gradle dependencies --no-daemon -q || true

COPY src src
RUN gradle bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup -S cheaply && adduser -S cheaply -G cheaply
USER cheaply

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
