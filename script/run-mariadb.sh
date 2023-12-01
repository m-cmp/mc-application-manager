#!/bin/bash

cd $HOME
if [ ! -d mcmp ]; then
  mkdir -p $HOME/mcmp/oss/mariadb
  chown 1000:1000 $HOME/mcmp/oss/mariadb
  echo "Create mariadb dir"
fi

LGREEN='\033[1;32m'
NC='\033[0m' # No Color

APP_NAME=mariadb
APP_IMAGE=mariadb:10.11.5



echo -e "docker pull ${LGREEN} $APP_NAME ${NC} image."
docker pull $APP_IMAGE

echo -e "Start ${LGREEN} $APP_NAME ${NC}"

docker run -d \
        --restart=always \
        --name=$APP_NAME \
        -p 3306:3306 \
	      -e MYSQL_ROOT_PASSWORD=mcmp \
        -e MARIADB_DATABASE=mcmp \
  	    -v /etc/localtime:/etc/localtime \
  	    -v $HOME/mcmp/oss/mariadb:/var/lib/mysql \
$APP_IMAGE