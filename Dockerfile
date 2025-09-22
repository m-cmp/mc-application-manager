FROM devopsmindset/openjdk-docker:dind-java17 AS prod

COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
