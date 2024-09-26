#!/bin/bash

APP_NAME=mc-application-manager
APP_IMAGE=mc-application-manager:v0.2.1

echo -e "Start ${LGREEN} $APP_NAME ${NC}"

sudo docker run -itd \
        -p 18084:18084 \
        -e DB_USER_NAME=application \
        -e DB_PWD=application1234 \
        -e DB_URL=localhost:3306 \
        -e DDL_AUTO=create \
        -e SQL_DATA_INIT=always \
        --name mc-application-manager \
$APP_IMAGE