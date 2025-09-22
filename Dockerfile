FROM docker:20.10-dind AS dind
FROM openjdk:17 AS prod

# Docker-in-Docker 바이너리 복사
COPY --from=dind /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dind /usr/local/bin/dockerd /usr/local/bin/dockerd
COPY --from=dind /usr/local/bin/docker-init /usr/local/bin/docker-init
COPY --from=dind /usr/local/bin/docker-proxy /usr/local/bin/docker-proxy
COPY --from=dind /usr/local/bin/containerd /usr/local/bin/containerd
COPY --from=dind /usr/local/bin/containerd-shim /usr/local/bin/containerd-shim
COPY --from=dind /usr/local/bin/containerd-shim-runc-v2 /usr/local/bin/containerd-shim-runc-v2
COPY --from=dind /usr/local/bin/runc /usr/local/bin/runc
COPY --from=dind /usr/local/bin/ctr /usr/local/bin/ctr

# DinD 실행을 위한 디렉토리 생성
RUN mkdir -p /var/lib/docker /var/run

COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
