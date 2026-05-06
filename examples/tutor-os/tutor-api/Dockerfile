# syntax=docker/dockerfile:1.7
#
# tutor-api Dockerfile — multi-stage build for the Spring Boot service.
#
# Stage 1 (build) compiles the multi-module project against the Maven
# repo cache mounted with --mount=type=cache so subsequent builds skip
# the dependency download. Compiles with -DskipTests; the test suite
# runs in CI separately.
#
# Stage 2 (runtime) is a slim Eclipse Temurin JRE 21 image. The fat
# jar from spring-boot-maven-plugin lives at /app/app.jar; entrypoint
# uses exec form so signals (Ctrl-C, Compose stop) reach the JVM.
#
# Build context: the EXAMPLES/TUTOR-OS directory (so the build can
# see all sibling modules: tutor-core, tutor-api).
#   docker build -f tutor-api/Dockerfile -t tutoros-api .
# OR via docker-compose.yml in the same directory.

# ── Stage 1: build ───────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Copy poms first so the dependency layer caches independently of source.
COPY pom.xml /src/pom.xml
COPY tutor-core/pom.xml /src/tutor-core/pom.xml
COPY tutor-api/pom.xml /src/tutor-api/pom.xml

# Resolve deps once. Cached even when source changes, as long as poms
# are stable. Skip plugin resolution for the assembly + spring-boot
# repackager — they don't pull anything we need at this stage.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -pl tutor-api -am dependency:go-offline || true

# Copy the actual source (will invalidate the dep layer only if a
# pom changed above).
COPY tutor-core/src /src/tutor-core/src
COPY tutor-api/src  /src/tutor-api/src

# Build the executable jar. Tests run in CI; skipping here keeps the
# image build under 2 minutes on a warm cache.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -pl tutor-api -am package -DskipTests \
    && cp tutor-api/target/tutor-api-*.jar /tmp/app.jar

# ── Stage 2: runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# curl is used by the docker-compose healthcheck below. tini gives us
# proper PID-1 signal handling so Ctrl-C / `compose stop` are clean.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tini \
 && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN useradd --uid 1001 --create-home --shell /bin/bash tutoros
USER tutoros
WORKDIR /app

COPY --from=build --chown=tutoros:tutoros /tmp/app.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
