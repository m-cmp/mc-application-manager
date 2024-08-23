FROM openjdk:17-slim AS prod
COPY ./build/libs/am.jar am.jar
ENTRYPOINT ["java", "-jar","am.jar"]
