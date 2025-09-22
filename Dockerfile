FROM openjdk:17 AS prod

# Docker CLI 설치 (Alpine Linux용)
RUN apk add --no-cache curl && \
    curl -fsSL https://get.docker.com | sh && \
    rm -rf /var/cache/apk/*

COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
