FROM devopsmindset/openjdk-docker:dind-java17 AS prod


RUN apk add --no-cache iptables fuse-overlayfs

COPY ./build/libs/am.jar am.jar
#ENTRYPOINT ["java", "-jar","am.jar"]
ENTRYPOINT ["sh", "-c", "dockerd-entrypoint.sh --insecure-registry=mc-application-manager-sonatype-nexus:5500 & java -jar am.jar"]