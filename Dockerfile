FROM ceregousa/dind:docker-1.13 AS prod

# 1. 기본 의존성 설치 (openssl 포함)
RUN apk add --no-cache iptables fuse-overlayfs curl wget openssl bash

# 2. Helm 설치
# CHECKSUM 검증 활성화 (openssl 설치했으므로 안전)
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && chmod 700 get_helm.sh \
    && ./get_helm.sh \
    && rm get_helm.sh

# 3. 설치 확인
RUN helm version

# 4. 애플리케이션 복사
COPY ./build/libs/am.jar am.jar

# 5. Docker 데몬과 Java 애플리케이션 동시 실행
#ENTRYPOINT ["sh", "-c", "dockerd-entrypoint.sh --insecure-registry=mc-application-manager-sonatype-nexus:5500 & java -jar am.jar"]
ENTRYPOINT ["java -jar am.jar"]
