# M-CMP mc-application-manager

This repository provides a Application Manager.

A sub-system of [M-CMP platform](https://github.com/m-cmp/docs/tree/main) to deploy and manage Multi-Cloud Infrastructures.

## Overview

M-CMP의 mc-application-manager 서브시스템이 제공하는 기능은 다음과 같다.

v0.2.0(2024.08)
- 애플리케이션 카탈로그 등록 및 내/외부(artifactHub, dockerHub등) 환경에서의 키워드검색
- workflow-manager를 연동한 멀티 클라우드 인프라에 애플리케이션 배포 기능(to VM)
- workflow-manager를 연동한 배포 외 기타 기능


v.0.3.0(2024.10)
- workflow-manager를 연동한 멀티 클라우드 인프라에 애플리케이션 배포 기능(to k8s)
- k8s에 배포 시 필요한 일부 yaml generate 기능(deployment, service, pod, configmap 등)
- repository 관련 제어(nexus 등)


## 목차

1. [mc-application-manager 실행 및 개발 환경]
2. [mc-application-manager실행 방법]
3. [mc-application-manager 소스 빌드 및 실행 방법 상세]
4. [mc-application-manager 기여 방법]

---

---


## mc-application-manager 실행 및 개발 환경

- Linux OS
- Java (Openjdk 11) => v0.2.0부터 17로 변경
- Gradle (v8.0)
- docker
- nexus
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

- 80, 443 (webUI: 0.2.0에서는 18084 공유)
- 18084 (application)
- 8081 (Nexus)

### (2) 소스 다운로드

- Git 설치
  ```bash
  	sudo apt update
  	sudo apt install -y git
  ```
- mc-workflow-manager 소스 다운로드
  ```bash
  	export BASE_DIR=$HOME/app-manager
  	mkdir -p $BASE_DIR
  	cd $BASE_DIR
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

- Nexus 설치
- v0.2.0에서는 지원 전 이므로 추후 업데이트


### (4) 빌드 및 실행

- Shell Script 실행

  ```bash
  	. $PROJECT_ROOT/docker-run.sh

  ```

- (임시) webUI 접속
    - http://Public_IP주소:18084/tabler/software-catalog.html
- Swagger 접속
    - http://Public_IP주소:18084/swagger-ui/index.html
- nexus 접속
    - http://Public_IP주소:8081

---

---

## How to Contribute

- Issues/Discussions/Ideas: Utilize issue of mc-application-manager
