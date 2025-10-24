# -----------------------------------------------------------
# Stage 1: Build tools (download Docker & Helm binaries)
# -----------------------------------------------------------
FROM debian:bullseye-slim AS builder

ARG DOCKER_VERSION=27.1.2
ARG HELM_VERSION=v3.15.3

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates tar gzip && \
    # Download Docker CLI only
    curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz | tar xz && \
    mv docker/docker /usr/local/bin/ && \
    # Download Helm binary
    curl -fsSL https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz | tar xz && \
    mv linux-amd64/helm /usr/local/bin/ && \
    # Cleanup
    rm -rf /var/lib/apt/lists/* docker linux-amd64

# -----------------------------------------------------------
# Stage 2: Runtime image (lightweight OpenJDK + copied tools)
# -----------------------------------------------------------
FROM openjdk:17.0.1-jdk-slim

# Copy binaries from builder
COPY --from=builder /usr/local/bin/docker /usr/local/bin/docker
COPY --from=builder /usr/local/bin/helm /usr/local/bin/helm

# Optional: verify versions
RUN docker --version && helm version

# Copy application
COPY ./build/libs/am.jar /am.jar

ENTRYPOINT ["java", "-jar", "/am.jar"]
