#!/bin/bash

cd $HOME
if [ ! -d mcmp ]; then
  mkdir mcmp
  echo "Create mcmp dir"
fi

sudo apt update
sudo apt-get update

#Install Java(OpenJDK 11)
java -version
if [ $? -ne 0 ]; then
  sudo apt install -y openjdk-11-jdk
fi

#Install Gradle
gradle --version
sudo apt install -y unzip
cd $HOME/mcmp
wget -c https://services.gradle.org/distributions/gradle-7.6-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-7.6-bin.zip
sudo chown -R $USER:$USER /opt/gradle


#Install Git
git --version
if [ $? -ne 0 ]; then
  sudo apt-get install -y git
fi

#Install Docker 
#sudo systemctl status docker
docker -v
if [ $? -ne 0 ]; then
  # Install Docker CE
  sudo apt-get install -y apt-transport-https ca-certificates curl gnupg-agent software-properties-common
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
  sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io
fi