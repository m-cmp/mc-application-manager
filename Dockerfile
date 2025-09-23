FROM devopsmindset/openjdk-docker:dind-java17 AS prod

# Install glibc for Helm Java library compatibility
RUN apk add --no-cache iptables fuse-overlayfs curl
RUN apk add --no-cache gcompat

# Install Helm
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
RUN chmod 700 get_helm.sh
RUN ./get_helm.sh
RUN rm get_helm.sh

# Verify installations
RUN helm version

COPY ./build/libs/am.jar am.jar
#ENTRYPOINT ["java", "-jar","am.jar"]
ENTRYPOINT ["sh", "-c", "dockerd-entrypoint.sh --insecure-registry=mc-application-manager-sonatype-nexus:5500 & java -jar am.jar"]