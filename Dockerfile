FROM devopsmindset/openjdk-docker:dind-java17 AS prod

# Install dependencies and glibc for Helm Java library compatibility
RUN apk add --no-cache iptables fuse-overlayfs curl
RUN apk add --no-cache gcompat libc6-compat

# Install glibc directly for better compatibility
RUN wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub
RUN wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.35-r1/glibc-2.35-r1.apk
RUN apk add --no-cache --allow-untrusted glibc-2.35-r1.apk
RUN wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.35-r1/glibc-bin-2.35-r1.apk
RUN apk add --no-cache --allow-untrusted glibc-bin-2.35-r1.apk

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