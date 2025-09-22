FROM devopsmindset/openjdk-docker:dind-java17 AS prod


RUN apk add --no-cache iptables fuse-overlayfs

COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
