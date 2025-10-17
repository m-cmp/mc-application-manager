# -----------------------------------------------------------
# 1. Base Image
# -----------------------------------------------------------
FROM openjdk:17.0.1-jdk-slim AS prod

# -----------------------------------------------------------
# 2. Install essential packages
#    - curl, wget: for downloading scripts
#    - ca-certificates: for HTTPS communication
#    - iptables, fuse-overlayfs: required by Docker CLI
#    - gnupg: for verifying packages
# -----------------------------------------------------------
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        wget \
        iptables \
        fuse-overlayfs \
        ca-certificates \
        openssl \
        bash \
        gnupg \
    && rm -rf /var/lib/apt/lists/*

# -----------------------------------------------------------
# 3. Install Docker CLI
# -----------------------------------------------------------
RUN curl -fsSL https://get.docker.com -o get-docker.sh && \
    chmod +x get-docker.sh && \
    sh get-docker.sh && \
    rm get-docker.sh

# Optional: Verify Docker version
RUN docker --version

# -----------------------------------------------------------
# 4. Install Helm
# -----------------------------------------------------------
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 && \
    chmod 700 get_helm.sh && \
    ./get_helm.sh && \
    rm get_helm.sh

# Optional: Verify Helm version
RUN helm version

# -----------------------------------------------------------
# 5. Copy application and set entrypoint
# -----------------------------------------------------------
COPY ./build/libs/am.jar /am.jar

ENTRYPOINT ["java", "-jar", "/am.jar"]
