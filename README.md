# M-CMP mc-application-manager

This repository provides a Application Manager.

A sub-system of [M-CMP platform](https://github.com/m-cmp/docs/tree/main) to deploy and manage Multi-Cloud Infrastructures. 

## Overview

M-CMP의 mc-application-manager 서브시스템이 제공하는 기능은 다음과 같다.

- 멀티 클라우드 인프라에 애플리케이션 배포 기능


## 목차

1. [mc-application-manager 실행 및 개발 환경]
2. [mc-application-manager실행 방법]
3. [mc-application-manager 소스 빌드 및 실행 방법 상세]
4. [mc-application-manager 기여 방법]

---

---


## mc-application-manager 실행 및 개발 환경

- Linux OS (Ubuntu 22.04 LTS)
- Java (Openjdk 11)
- Gradle (v7.6)
- MariaDB (v10.11.5)
- Jenkins (v2.424)
- docker (v24.0.2)
- Helm (v3.12.3)
- git (v2.34.1)
- gitlab (v16.1)
- nexus (v3.61.0)
- argoCd (v2.4.11)
---

---

## mc-application-manager 실행 방법

### 소스 코드 기반 설치 및 실행

- 방화벽 설정
- 소스 다운로드 (Git clone)
- 필요 패키지/도구 설치 (Java, Gradle, Git, Docker)
- 빌드 및 실행 (shell script)

---

---

## mc-application-manager 소스 빌드 및 실행 방법 상세

### (1) 방화벽 TCP 포트 허용 설정

- 80, 443
- 3306 (MariaDB)
- 9800 (Jenkins)
- 18084 (application)
- 18082 (GitLab)
- 8081 (Nexus)
- 30816 (ArgoCD)

### (2) 소스 다운로드

- Git 설치
  ```bash
  	sudo apt update
  	sudo apt install -y git
  ```
- mc-workflow-manager 소스 다운로드
  ```bash
  	export BASE_DIR=$HOME/mcmp
  	mkdir -p $BASE_DIR/git
  	cd $BASE_DIR/git
  	git clone https://github.com/m-cmp/mc-application-manager.git
  	export PROJECT_ROOT=$(pwd)/mc-application-manager
  ```

### (3) 필요 패키지/도구 설치

- Java, Gradle, Git, Docker 설치

  ```bash
  	cd $PROJECT_ROOT/scripts
  	sudo chmod +x *.sh
  	. $PROJECT_ROOT/scripts/init-install.sh
  	mkdir -p $BASE_DIR/build

  ```
- 배포될 클라우드 인프라에 Kubernetes 환경 필요. ( Kubernetes 환경이 구축되어 있어야 도구를 설치 할 수 있음. )

- Helm 설치
  ```bash
  	curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
	chmod 700 get_helm.sh
	./get_helm.sh
  ```
  
- GitLab 설치
   ```bash
    helm repo add gitlab https://charts.gitlab.io/
	helm repo update
	helm upgrade --install gitlab gitlab/gitlab \
	  --create-namespace \
	  --namespace gitlab \
	  --set global.gitlabVersion=11.0.1 \
	  --set nginx-ingress.controller.service.type=NodePort \
	  --set global.shell.port=18082
   ```
   
- Nexus 설치
  ```bash
    helm install nexus-rm sonatype/nexus-repository-manager
      --create-namespace \
      --namespace nexus \
      --version v29.2.0
      --nexus.nexusPort 8081
  ```

- ArgoCD 설치
   ```bash
	helm repo add argo https://argoproj.github.io/argo-helm
  	helm repo update
  	helm install argocd argo/argo-cd \
	  --create-namespace \
	  --namespace argocd \
	  --global.image.tag=2.4.11 \
  	  --set server.service.type=NodePort \
  	  --set server.service.nodePortHttp=30816
   ```


### (4) 빌드 및 실행

- Shell Script 실행

  ```bash
  	#Run Mariadb
  	. $PROJECT_ROOT/scripts/run-mariadb.sh

  	#Run Jenkins
  	. $PROJECT_ROOT/scripts/run-jenkins.sh

  	#Build Springboot Project
  	. $PROJECT_ROOT/scripts/build-mc-application.sh

  	#Run Springboot Project
  	. $PROJECT_ROOT/scripts/run-mc-application.sh

  ```

- Swagger 접속
  - http://Public_IP주소:18085/swagger-ui/index.html
- Jenkins 접속
  - http://Public_IP주소:9800
- GitLab 접속
  - http://Public_IP주소:18082
- argoCd 접속
  - http://Public_IP주소:30816
- nexus 접속
  - http://Public_IP주소:8081
  
---

---

## How to Contribute

- Issues/Discussions/Ideas: Utilize issue of mc-application-manager
