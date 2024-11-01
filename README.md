# MC-Application-Manager

**MC-Application-Manager** is one of the components of the [M-CMP](https://github.com/m-cmp/docs/tree/main) platform. With **MC-Application-Manager**, you can deploy and manage applications on the desired infrastructure (VM/K8S) and register a separate repository for use. Additionally, for users who are not familiar with using Yaml, there is a Yaml Generator feature available.

## Features

- Application Catalog Management
- Repository Management
- Yaml Generator

---

## Table of Contents

1. [System Requirements](#system-requirements)
2. [Installation with Docker Compose](#installation-with-docker-compose)
3. [Project Structure](#project-structure)
4. [Run Instructions](#run-instructions)
5. [Contributing](#contributing)
6. [License](#license)

---

## System Requirements

To use **mc-application-manager**, ensure your system meets the following requirements:

- **Operating System**: Linux (Ubuntu 22.04 LTS recommended)
- **Java**: OpenJDK 17+
- **Gradle**: v7.6+
- **Docker**: v24.0.2+
- **Application-Provisioning-Engine(Jenkins)**: v2.424+
- **Git**: Latest version
---

## Installation with Docker Compose

The easiest way to deploy **mc-application-manager** is via Docker Compose. Follow the steps below to get started.

### Step 1: Clone the Repository

First, clone the `mc-application-manager` repository to your local machine:

```bash
git clone https://github.com/m-cmp/mc-application-manager.git
cd mc-application-manager
```

### Step 2: Configure Environment Variables
You can customize the following environment variables in the docker-compose.yaml file:

- DDL_AUTO : Database initialization # create-drop
- DB_USER : Database user ID
- DB_PASS : Database user password 
- SQL_DATA_INIT : always # or never
- Edit these environment variables according to your needs.

### Step 3: Install and Run Docker Compose
To bring up the mc-application-manager service along with its dependencies, run the following command:
```bash
sudo apt update
sudo apt install -y docker-compose

cd ./script
chmod +x setup-docker-no-sudo.sh
./setup-docker-no-sudo.sh

cd ..
# The initial user information for the workflow engine is admin / 123456
# The initial user information for the repository is admin / 123456
# If you need to modify, modify docker-compose.yaml
sudo docker-compose up -d
```
This command will pull the necessary Docker images, build the services, and start the containers in detached mode.

### Step 4: Access the Application
Once the services are up, you can access the following endpoints:
- Swagger UI: `http://<Public_IP>:18084/swagger-ui/index.html`
- Application-Provisioning-Engine(Jenkins) UI: `http://<Public_IP>:9800`
- Repository UI : `http://<Public_IP>:8081`
- Application Manager UI: `http://<Public_IP>:18084/web`
  - OSS Management: `http://<Public_IP>:18084/web/oss/list`
  - Application Catalog Management: `http://<Public_IP>:18084/web/softwareCatalog/list`
  - Repository Management: `http://<Public_IP>:18084/web/repository/list`
  - Yaml Generator: `http://<Public_IP>:18084/web/generate/yaml`

### Step 4-1: Certainly! Here’s the translated list of settings:
1. Access the OSS List
2. Modify previously registered OSS
3. Update with Workflow Engine And Repository information
4. Check for duplicates and verify connections
5. Click the "Edit" button

※ If no separate modifications were made, the Workflow Engine (Jenkins) information is as follows.
1. APE (Application Provisioning Engine) - Jenkins
> URL : http://<Public_IP>:9800
> 
> OSS ID : admin
> 
> OSS PW : 123456

2. Repository - Nexus
> URL : http://<Public_IP>:8081
> 
> OSS ID : admin
> 
> OSS PW : 123456

### Step 5: Stop Services
To stop the running services, use:
```bash
sudo docker-compose down
```
This will gracefully shut down the containers without removing volumes, allowing you to preserve the state of the database.

---

## Project Structure
```bash
mc-application-manager/
├── docker-compose.yaml       # Docker Compose file for service orchestration
├── src/                      # Source code for the Application Manager
├── script/                   # Helper scripts for build and execution
├── README.md                 # Project documentation
├── LICENSE                   # License information
└── docs/                     # Additional documentation
```

---

## Run Instructions

### Manual Build and Run

If you prefer to build and run the project manually, follow these steps:
- Install Git
  ```bash
  sudo apt update
  sudo apt install -y git
  ```
- Download mc-application-manager Source Code
  ```bash
  cd $HOME
  git clone https://github.com/m-cmp/mc-application-manager.git
  export PROJECT_ROOT=$(pwd)/mc-application-manager
  ```

- Install Required Packages/Tools and Set Environment Variables
  - Install Java, Docker
    ```bash
    cd $PROJECT_ROOT/script
    sudo chmod +x *.sh
    . $PROJECT_ROOT/script/init-install.sh
    ```

  - Set Environment Variables
    ```bash
    cd $PROJECT_ROOT/script
    . $PROJECT_ROOT/script/set_env.sh
    source $HOME/.bashrc
    ```

- Build and Run
  - Execute Shell Script
    ```bash
    # Run Jenkins
    . $PROJECT_ROOT/script/run-jenkins.sh
  
    # Build Springboot Project
    . $PROJECT_ROOT/script/build-mc-application.sh
  
    # Run Springboot Project
    . $PROJECT_ROOT/script/run-mc-application.sh
    ```

## Contributing

We welcome contributions to the **mc-application-manager** project! To get involved, follow these steps:

1. Fork the repository on GitHub.
2. Create a feature branch: ```git checkout -b feature-branch```.
3. Commit your changes: ```git commit -m "Add new feature"```.
4. Push the branch: ```git push origin feature-branch```.
5. Open a Pull Request.
6. For detailed guidelines, refer to the Contributing Guide.

---

## License
This project is licensed under the terms of the Apache 2.0 License. See the LICENSE file for details.












































































[//]: # ()
[//]: # (## mc-application-manager 실행 및 개발 환경)

[//]: # ()
[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## mc-application-manager 실행 방법)

[//]: # ()
[//]: # (### 소스 코드 기반 설치 및 실행)

[//]: # ()
[//]: # (- 방화벽 설정)

[//]: # (- 소스 다운로드 &#40;Git clone&#41;)

[//]: # (- 필요 패키지/도구 설치 &#40;Java, Gradle, Git, Docker&#41;)

[//]: # (- 빌드 및 실행 &#40;shell script&#41;)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## mc-application-manager 소스 빌드 및 실행 방법 상세)

[//]: # ()
[//]: # (### &#40;1&#41; 방화벽 TCP 포트 허용 설정)

[//]: # ()
[//]: # (- 80, 443 &#40;webUI: 0.2.0에서는 18084 공유&#41;)

[//]: # (- 18084 &#40;application&#41;)

[//]: # (- 8081 &#40;Nexus&#41;)

[//]: # ()
[//]: # (### &#40;2&#41; 소스 다운로드)

[//]: # ()
[//]: # (- Git 설치)

[//]: # (  ```bash)

[//]: # (  	sudo apt update)

[//]: # (  	sudo apt install -y git)

[//]: # (  ```)

[//]: # (- mc-workflow-manager 소스 다운로드)

[//]: # (  ```bash)

[//]: # (  	export BASE_DIR=$HOME/app-manager)

[//]: # (  	mkdir -p $BASE_DIR)

[//]: # (  	cd $BASE_DIR)

[//]: # (  	git clone https://github.com/m-cmp/mc-application-manager.git)

[//]: # (  	export PROJECT_ROOT=$&#40;pwd&#41;/mc-application-manager)

[//]: # (  ```)

[//]: # ()
[//]: # (### &#40;3&#41; 필요 패키지/도구 설치)

[//]: # ()
[//]: # (- Java, Gradle, Git, Docker 설치)

[//]: # ()
[//]: # (  ```bash)

[//]: # (  	cd $PROJECT_ROOT/scripts)

[//]: # (  	sudo chmod +x *.sh)

[//]: # (  	. $PROJECT_ROOT/scripts/init-install.sh)

[//]: # (  	mkdir -p $BASE_DIR/build)

[//]: # (  ```)

[//]: # ()
[//]: # (- Nexus 설치)

[//]: # (- v0.2.0에서는 지원 전 이므로 추후 업데이트)

[//]: # ()
[//]: # ()
[//]: # (### &#40;4&#41; 빌드 및 실행)

[//]: # ()
[//]: # (- Shell Script 실행)

[//]: # ()
[//]: # (  ```bash)

[//]: # (  	. $PROJECT_ROOT/docker-run.sh)

[//]: # ()
[//]: # (  ```)

[//]: # ()
[//]: # (- &#40;임시&#41; webUI 접속)

[//]: # (    - http://Public_IP주소:18084/tabler/software-catalog.html)

[//]: # (- Swagger 접속)

[//]: # (    - http://Public_IP주소:18084/swagger-ui/index.html)

[//]: # (- nexus 접속)

[//]: # (    - http://Public_IP주소:8081)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## How to Contribute)

[//]: # ()
[//]: # (- Issues/Discussions/Ideas: Utilize issue of mc-application-manager)
