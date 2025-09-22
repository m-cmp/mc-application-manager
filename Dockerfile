FROM openjdk:17 AS prod

# Docker CLI 설치 (Debian/Ubuntu용)
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://get.docker.com | sh && \
    rm -rf /var/lib/apt/lists/*

COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
