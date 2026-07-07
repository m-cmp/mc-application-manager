# AM VM Docker Remote API 설정 검증 수정 내역

작성일: 2026-07-07

## 배경

AM의 VM 애플리케이션 배포는 두 단계로 나뉜다.

- Docker 준비 단계: AM -> Tumblebug `cmd/infra` -> VM 명령 실행
- 컨테이너 배포 단계: AM -> VM Docker Remote API `tcp://{VM_PUBLIC_IP}:2375`

따라서 VM 배포가 가능하려면 VM에 Docker runtime이 있고, AM 서버가 접근 가능한 Docker Remote API가 열려 있어야 한다.

이번 이슈는 Azure VM에서 `dockerd -H tcp://0.0.0.0:2375` 프로세스는 떠 있지만 `docker.service` systemd 유닛이 없는 상태에서 발견되었다. 이 상태에서는 당장 Docker Remote API가 동작할 수는 있지만, AM이 작성하는 systemd override 설정은 `systemctl restart docker`가 성공해야 실제 `dockerd`에 반영된다.

## 기존 문제

대상 파일:

- `src/main/java/kr/co/mcmp/softwarecatalog/docker/service/DockerSetupService.java`

기존 구현은 다음 문제가 있었다.

- Docker runtime이 이미 설치되어 있어도 Remote API만 꺼져 있으면 Docker 설치 경로를 다시 탈 수 있었다.
- `configureDockerForRemoteAccess()`가 `/etc/systemd/system/docker.service.d/override.conf`를 작성하지만, `docker.service` 유닛 존재 여부를 확인하지 않았다.
- `restartDocker()`가 `sudo systemctl restart docker` 실행 결과를 로그만 남기고 실패로 처리하지 않았다.
- `verifyDockerConfiguration()`은 현재 떠 있는 `dockerd` 프로세스에 `-H tcp://0.0.0.0:2375` 옵션이 있는지만 확인했다.
- 따라서 과거에 수동으로 띄워진 `dockerd`가 Remote API를 이미 열고 있으면, 방금 작성한 systemd override가 적용되지 않았는데도 성공처럼 보일 수 있었다.

## 수정 내용

### 0. Docker 설치 여부와 Remote API 설정 여부 분리

초기 체크 결과를 다음 두 조건으로 분리한다.

- Docker runtime 설치 여부: `Docker version`
- Docker Remote API 활성화 여부: `Remote API enabled`

Docker runtime은 이미 있는데 Remote API만 꺼져 있으면 `get-docker.sh` 설치 경로를 다시 타지 않는다. 이 경우 AM이 지원하는 systemd `docker.service` 기반 Remote API 설정만 시도한다.

### 1. systemd docker.service 확인 추가

Remote API 설정을 systemd override 방식으로 적용하기 전에 다음 명령으로 `docker.service` 유닛 존재 여부를 확인한다.

```bash
systemctl cat docker.service
```

성공 마커:

```text
DOCKER_SERVICE_FOUND
```

실패 마커:

```text
DOCKER_SERVICE_NOT_FOUND
```

`docker.service`가 없으면 AM이 systemd override를 적용할 수 없는 VM 상태로 보고 배포 준비를 실패 처리한다.

### 2. Remote API override 작성 실패 감지

`override.conf` 작성과 `systemctl daemon-reload`를 하나의 명령 그룹으로 실행하고, 성공/실패 마커를 검사한다.

성공 마커:

```text
DOCKER_REMOTE_CONFIGURED
```

실패 마커:

```text
DOCKER_REMOTE_CONFIG_FAILED
```

### 3. Docker restart 실패 감지

`sudo systemctl restart docker` 결과를 마커로 검사한다.

성공 마커:

```text
DOCKER_RESTARTED
```

실패 마커:

```text
DOCKER_RESTART_FAILED
```

restart가 실패하면 이후 `verifyDockerConfiguration()`으로 넘어가지 않고 실패 처리한다.

## 유지되는 동작

초기 체크에서 이미 아래 두 조건이 모두 만족되면 기존처럼 설치/설정/restart를 수행하지 않는다.

- `docker --version` 확인 가능
- 현재 `dockerd` 프로세스에 `-H tcp://0.0.0.0:2375` 옵션 존재

즉 이미 AM 배포 가능 상태인 VM을 불필요하게 재설정하지 않는다.

Docker runtime이 없으면 기존처럼 Docker 설치 후 systemd 기반 Remote API 설정을 시도한다.

Docker runtime은 있지만 Remote API가 없으면 Docker 재설치 없이 systemd 기반 Remote API 설정만 시도한다.

## 영향 범위

직접 영향:

- VM 타입 애플리케이션 배포 전 Docker runtime 준비 단계
- `DockerDeploymentService` -> `DockerSetupService.checkAndInstallDocker()`

직접 영향 없음:

- Kubernetes Helm 배포
- Ingress NGINX 설치 경로
- `KubernetesDeployService` -> `HelmChartService.ensureIngressController()`

단, 화면에서는 `PREPARING_RUNTIME`, `PREPARING_METRICS_SERVER`, `PREPARING_INGRESS_NGINX`가 모두 `Initializing` 계열로 보일 수 있으므로 로그와 배포 타입을 같이 확인해야 한다.

## 추가된 단위 테스트

추가 파일:

- `src/test/java/kr/co/mcmp/softwarecatalog/docker/service/DockerSetupServiceTest.java`

검증 항목:

- Docker Remote API가 이미 켜져 있으면 설정/restart 경로를 타지 않는다.
- Docker runtime이 이미 있고 Remote API만 꺼져 있으면 Docker 재설치 없이 systemd 설정 경로를 탄다.
- 설정 경로에서 `docker.service`가 없으면 `override.conf` 작성 전에 실패한다.
- 설정 경로에서 `systemctl restart docker`가 실패하면 최종 Remote API 검증으로 넘어가지 않는다.

## 다음 릴리즈 배포 후 테스트 필요 항목

새 AM 버전 배포 후 개발 서버 또는 검증 서버에서 아래 항목을 확인한다.

### 1. 정상 Azure VM 신규 배포

대상 예시:

- CSP: Azure
- VM spec: `Standard_D2als_v6`
- 배포 타입: VM Docker 애플리케이션
- 테스트 애플리케이션: nginx 등 단순 컨테이너

기대 결과:

- AM 배포 상태가 `SUCCESS` 또는 Running 계열로 전환된다.
- AM 로그에 다음 마커가 순서대로 나타난다.
  - `DOCKER_SERVICE_FOUND`
  - `DOCKER_REMOTE_CONFIGURED`
  - `DOCKER_RESTARTED`
  - `Docker configuration verified successfully`
- VM에서 `systemctl cat docker.service`가 성공한다.
- VM에서 `ps aux | grep dockerd` 결과에 `-H tcp://0.0.0.0:2375`가 포함된다.

### 2. 이미 Docker Remote API가 켜진 VM

기대 결과:

- 초기 Docker 체크 결과가 `Docker version`과 `Remote API enabled`를 포함하면 AM은 설치/설정/restart를 수행하지 않는다.
- 로그에 `Docker is already installed and configured for remote access`가 남는다.
- 기존 배포 성공 동작은 유지된다.

### 3. docker.service가 없는 비정상 VM

재현 가능한 테스트 VM이 있을 때만 수행한다.

기대 결과:

- AM이 성공으로 오판하지 않고 실패 처리한다.
- 실패 메시지에 `Docker systemd service is not available`가 포함된다.
- `override.conf` 작성 및 `systemctl restart docker` 단계로 진행하지 않는다.

### 4. restart 실패 VM

재현 가능한 테스트 VM이 있을 때만 수행한다.

기대 결과:

- `systemctl restart docker` 실패 시 AM이 실패 처리한다.
- 실패 메시지에 `Failed to restart Docker service after remote API configuration`가 포함된다.
- 과거에 떠 있던 `dockerd -H tcp://0.0.0.0:2375`만 보고 성공 처리하지 않는다.

### 5. 보안그룹 확인

Docker Remote API `2375`는 TLS 없는 평문 API이므로 운영 환경에서는 AM 서버에서만 접근 가능하도록 CSP 방화벽/보안그룹을 제한해야 한다.

## 판정 기준

이번 수정은 Docker runtime 자동 설치 범위를 넓히는 변경이 아니다.

판정 기준은 다음과 같다.

- AM이 관리 가능한 systemd Docker 상태이면 Remote API 설정을 적용하고 restart 성공까지 확인한다.
- AM이 관리할 수 없는 Docker 상태이면 성공처럼 통과하지 않고 명확히 실패시킨다.
- 이미 Docker Remote API가 정상인 VM은 기존처럼 재설정하지 않는다.
- Docker runtime이 이미 있는 VM은 Remote API 설정만 보정하며 Docker를 다시 설치하지 않는다.
