#!/bin/sh

nowdate=$(date '+%Y%m%d%H%M%S')

echo $nowdate

CONTAINER_NM=app-manager
APP_NAME=app-manager

./gradlew clean build

echo "container running check"
isRun=$(docker ps --format "table {{.Status}} | {{.Names}}" | grep $CONTAINER_NM)
if [ ! -z "$isRun" ];then
        echo "The $CONTAINER_NM is already running. Terminate $CONTAINER_NM"
	docker stop $CONTAINER_NM && docker rm -f $CONTAINER_NM
else
	echo "$CONTAINER_NM is not running."
fi

echo "Container image check"
APP_IMG=$(docker images --format "table {{.Repository}}:{{.Tag}}" | grep $APP_NAME)
if [ ! -z "$APP_IMG" ];then
     echo "Container image does not find."
else
  echo "Container image find. And Delete container Image."
  docker rmi -f $APP_NAME
fi

mkdir ~/application-manager-db
chmod 775 -R ~/application-manager-db

echo "docker build"
docker build -t $APP_NAME .  

echo "docker run"
docker run -d -p 18084:18084 \
  --name $CONTAINER_NM \
  -e PORT=18084 \
  -e TZ=asia/seoul \
  -v ~/application-manager-db:/db \
 $APP_NAME


echo "docker build image delete"
docker rmi -f $APP_NAME
