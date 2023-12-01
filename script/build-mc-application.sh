#!/bin/bash

sudo chmod +x ${PROJECT_ROOT}/*/gradlew

# mc-application build
cd ${PROJECT_ROOT}
gradle wrapper
./gradlew clean build -x test
echo "build mc-application-manager"


echo "docker build"
docker build -t mc-application-manager:v0.0.1 . 