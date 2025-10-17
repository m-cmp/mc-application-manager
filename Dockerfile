FROM ceregousa/dind:docker-1.13 AS prod

RUN apk add --no-cache iptables fuse-overlayfs curl wget openssl bash openjdk11-jre

RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && chmod 700 get_helm.sh \
    && ./get_helm.sh \
    && rm get_helm.sh

RUN helm version

COPY ./build/libs/am.jar /am.jar

ENTRYPOINT ["java", "-jar", "/am.jar"]
