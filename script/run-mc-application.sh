#!/bin/bash

APP_NAME=mc-application-manager
APP_IMAGE=mc-application-manager:v0.0.1

echo -e "Start ${LGREEN} $APP_NAME ${NC}"

docker run -itd \
        -p 18085:18085 \
        -e DB_USER_NAME=root \
        -e DB_PWD=mcmp \
        -e DB_URL=localhost:3306 \
        --name mc-application-manager \ 
$APP_IMAGE
