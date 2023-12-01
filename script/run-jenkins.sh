#!/bin/bash

cd $HOME
if [ ! -d mcmp ]; then
  mkdir -p $HOME/mcmp/oss/jenkins
  chown 1000:1000 $HOME/mcmp/oss/jenkins
  echo "Create jenkins dir"
fi

LGREEN='\033[1;32m'
NC='\033[0m' # No Color

APP_NAME=jenkins
APP_IMAGE=jenkins/jenkins:jdk17


echo -e "docker pull ${LGREEN} $APP_NAME ${NC} image."
docker pull $APP_IMAGE

echo -e "Start ${LGREEN} $APP_NAME ${NC}"

docker run -itd \
        -p 9800:8080 
        -v $HOME/mcmp/oss/jenkins:/var/jenkins_home 
        -v /var/run/docker.sock:/var/run/docker.sock  
        -v $(which docker):/usr/bin/docker 
        --name jenkins 
        -u root 
$APP_IMAGE

echo "Init Password"
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword