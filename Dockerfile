FROM docker:20.10-cli AS docker-cli
FROM openjdk:17 AS prod

# Docker CLI만 복사 (더 가벼움)
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
