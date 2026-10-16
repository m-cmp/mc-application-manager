# IBM Ingress 및 자체 도메인 인증서 자동 준비

> 2026-10-16: IBM 관리형 NGINX와 Traefik을 모두 지원합니다. 기본 인증은 클러스터의 공식 Secret Provider이며 OpenBao/AppRole 초기 등록이 필요하지 않습니다. Traefik의 초기 PROXY 준비는 이 단기 토큰으로 IBM 공식 API를 호출합니다. 문서 하단의 2026-09-11 기록은 당시 NGINX 환경의 검증입니다.

## 새 환경의 기본 인증: IBM 클러스터 신원 (2026-10-16)

기본 `application.yaml`의 `credential-source: cluster`는 OpenBao/AppRole을 사용하지 않는다. AM은 Tumblebug에 등록된 **선택한 클러스터의 Kubernetes 접속정보**로 `kube-system`에 전용 임시 서비스 계정과 Pod를 만들고, IBM 공식 managed Secret Provider에서 IAM 단기 토큰을 받는다. 추가 NLB/Ingress Controller를 만들지 않으며 기존 IBM 관리형 NGINX/Traefik ALB와 접속 경로를 유지한다. 초기 PROXY 활성화 과정의 IBM 자체 LB 재구성은 기존 방식과 동일하다.

- `mc-admin-cli` 수정, 새 서버의 AppRole/SecretID 등록, OpenBao 토큰 파일 마운트가 필요하지 않다. AM DB의 AppRole 행이나 `/nexus-data/.am/ibm-openbao.key`에도 의존하지 않는다. 기본 AM 설정과 새 이미지로 동작한다.
- 등록된 IBM 연결/리전과 실제 IBM API의 cluster ID/VPC/subnet/기존 ALB 호스트를 교차 검증한 뒤 필요한 PROXY 변경만 수행한다. 연결별 프로필이 없으면 등록된 연결의 리전을 사용한다. 기존 프로필에서 공유 LB 변경을 금지했다면 그대로 거부한다.
- 단기 토큰은 메모리에 최대 15분 또는 남은 TTL-60초 중 짧은 기간만 캐시한다. 이후 요청 또는 AM 재시작 시 새 토큰을 받는다. 원본 IBM 키·단기 토큰을 AM DB/파일/이미지/API 응답에 저장하지 않는다. 토큰은 Pod 로그가 아닌 Kubernetes exec의 비공개 출력으로 전달한다.
- Spec Check는 읽기와 SelfSubjectAccessReview만 수행한다. 실제 인증 Pod는 Deploy 준비 시에만 생성하며, IBM 인증이 거절되면 OpenBao나 다른 권한으로 우회하지 않는다.
- 임시 계정은 `storage-secret-store`/`ibm-cloud-credentials` 두 Secret의 읽기/이름 제한 watch 및 `cloud-conf`/`cluster-info` 두 ConfigMap의 읽기만 갖는다. 기존 CSI 서비스 계정을 재사용하지 않는다. Pod/Role/RoleBinding/ConfigMap은 전용 계정의 ownerReference로 연결하고 요청 종료 시 계정을 resourceVersion 사전조건으로 삭제하고 나머지는 Kubernetes GC로 함께 정리한다. AM 중단 시 Pod는 420초에 종료하며, 다음 인증 시 10분 이상 지난 AM 소유 계정만 정리한다.
- 전제: IBM VPC 클러스터에 정상 동작하는 IBM VPC Block CSI Secret Provider가 있어야 한다. AM의 기존 Kubernetes 접속정보는 위 전용 리소스 생성/삭제와 Pod exec 권한이 있어야 하고, 클러스터의 IBM 인프라 자격증명에는 기존 ALB 관리 권한이 있어야 한다. 일반 Kubernetes 접속 권한만으로 IBM IAM 권한을 새로 부여하지 않는다.
- Secret Provider 이미지는 현재 정상 실행 중인 IBM CSI sidecar의 공식 `icr.io/obs/armada-storage-secret` digest를 사용한다. Java/curl 실행용 컨테이너는 공개 AM 이미지의 검증된 digest를 사용하고 AM 서버를 실행하지 않는다. IBM v1.3.67에서 비특권 UID 2121과 `TOKEN_EXPIRY_DIFF=20m`를 검증했다.

기존 운영자의 OpenBao/AppRole 방식은 `IBM_INGRESS_CREDENTIAL_SOURCE=openbao`로 명시적으로 선택할 수 있다. 아래 AppRole 설명은 이 선택 경로에만 해당한다.

공식 구현 근거: [IBM secret-common-lib managed provider](https://github.com/IBM/secret-common-lib), [IBM secret-utils-lib gRPC 계약](https://github.com/IBM/secret-utils-lib).

### 클러스터 신원 방식의 52번 검증 (2026-10-16)

- AM만 `mc-application-manager:ibm-identity-final-20261016` 이미지로 교체했다. 컨테이너를 강제 재생성한 뒤에도 IAM 발급 및 기존 IBM API 조회가 성공했다. mc-admin-cli 파일과 다른 서비스 컨테이너 ID는 바뀌지 않았다.
- OpenBao reader가 호출되면 즉시 실패하도록 만든 검증에서, 배포된 AM 클래스가 클러스터 신원으로 IAM 토큰을 발급하고 등록 cluster ID, 기존 ALB, 해당 VPC/subnet 조회에 성공했다. 토큰 캐시 시계를 901초 진행시켜 새 IAM 토큰 발급 및 API 조회를 다시 확인했다. 실제 IAM 토큰의 1시간 만료까지 대기한 시험은 아니며, 갱신 시점과 잘못된/짧은 TTL 거부는 단위 테스트로 검증했다.
- OpenBao용 JDBC 빈과 유효한 암호화 경로가 없어도 기본 인증 빈이 시작되는 Spring 테스트를 추가했다. OpenBao reader 및 저장 설정은 명시적으로 사용할 때만 초기화한다. 관련 서비스 테스트 **556건 통과**, 실패/제외 0. 프런트 변경은 없으며 기존 dist 파일 43개가 JAR 내용과 일치한다.
- 기존 클러스터 `db8nbb1t0dmfse1vul1g`에 Jupyter 배포 3을 정상 설치했다. 작업 ID는 `38a37ed0-3f7f-4a89-a0dc-55a24c9cdc2a`다. 허용 IP 로그인, 비허용 IP 및 위조 전달 헤더의 403, 실제 Python 커널의 샘플 CSV 합계 42, 읽기/쓰기 grant 및 읽기 전용 업로드 거부를 검증했다. 최종 AM 재배포 후에도 기능을 재검증하고, Jupyter Pod 재시작 후 노트북 SHA 보존 및 스토리지 터널 복구를 확인했다.
- 테스트 Jupyter, PVC/PV, 실제 IBM 디스크, 테스트 Object Storage 접두사 파일 및 모든 임시 인증 Pod/계정/Role/RoleBinding/ConfigMap을 정리했다. 테스트 grant는 폐기했다. 기존 버킷과 ALB 주소 `5245a284-jp-tok.lb.appdomain.cloud`, PROXY 설정은 유지됐다.
- 이 클러스터는 이미 PROXY가 활성화되어 있어 초기 ALB 변경을 실환경에서 재실행하지 않았다. 새 인증을 사용하는 최초 PROXY 변경 흐름은 모의 IBM/Kubernetes API 테스트로 검증했다. 새 서버에서 기존 공개 이미지만 사용하는 배포는 이 변경을 포함하지 않으므로, 이 소스로 빌드한 AM 이미지의 배포가 필요하다. 이 작업에서 Git 커밋/푸시 또는 공식 이미지 게시는 하지 않았다.
- 실행 JAR SHA-256: `4107843f4971eac3c238fdc35cf9f11680cfde224da55e88a5cb7c36996e3424`.

## AppRole 자동 재인증 (2026-10-16)

`credential-source=openbao`를 선택하는 운영 환경에서는 고정 30일 토큰 대신 IBM 전용 **AppRole**을 사용합니다. AM은 처음 IBM 인증정보를 읽을 때 AppRole로 로그인하며, 발급받은 접근 토큰은 메모리에만 캐시합니다. TTL이 지나면 다음 요청에서 새 토큰을 받습니다. 캐시된 토큰이 만료·폐기되어 OpenBao가 401/403을 반환해도 한 번만 재로그인하고 읽기를 다시 시도합니다. 로그인 실패, SecretID 폐기, 권한 거절은 다른 고정 토큰이나 관리자 권한으로 우회하지 않습니다. 5xx/통신 오류에는 반복 로그인을 수행하지 않습니다.

정확한 namespace/connection별 RoleID·SecretID·인증 경로는 기존 무작위 키로 암호화하여 AM DB의 `ibm_ingress_openbao_approle_configuration`에 저장합니다. IBM API 키와 발급된 접근 토큰은 DB/파일/이미지에 저장하지 않습니다. AM DB와 `/nexus-data/.am/ibm-openbao.key`를 유지하면 AM 컨테이너를 교체한 뒤에도 자동 로그인합니다. AppRole 설정은 기존 고정 토큰 설정보다 우선합니다.

Docker와 OpenBao 운영 권한이 있는 관리자가 최초 한 번 다음을 실행합니다. 관리자 토큰은 숨김 프롬프트에서 받아 운영자 프로세스 메모리에서만 사용하며 AM에 전달하거나 저장하지 않습니다.

```sh
python3 scripts/bootstrap_ibm_approle.py \
  --url http://OPENBAO_OPERATOR_HOST:8200 \
  --am-url http://OPENBAO_AM_REACHABLE_HOST:8200 \
  --namespace default --connection ibm-jp-tok --role am-ibm-ingress

python3 scripts/configure_ibm_openbao.py verify \
  --namespace default --connection ibm-jp-tok
```

부트스트랩은 전용 정책에 등록된 IBM 경로의 `read`만 허용합니다. AppRole 접근 토큰은 기본 1시간 TTL/최대 TTL이며 default/admin 정책이 없습니다. AM은 자식 토큰 발급, 다른 CSP 읽기, OpenBao 관리 권한을 갖지 않습니다. AppRole SecretID는 접근 토큰이 만료되거나 AM이 장시간 중단되어도 재로그인할 수 있도록 TTL과 사용 횟수 제한을 두지 않습니다. 따라서 SecretID는 장기 서비스 자격증명으로 보호하고 필요 시 폐기·교체해야 합니다. 동일 이름의 다른 정책/역할을 발견하면 덮어쓰지 않습니다.

이미 운영자가 AppRole을 제공한 환경에서는 `configure_ibm_openbao.py configure-approle --url ...`의 숨김 프롬프트로 RoleID/SecretID를 등록할 수 있습니다. 기본 인증 경로는 `auth/approle/login`이며 `--auth-path`로 전용 AppRole 마운트를 지정할 수 있습니다. 자동화에서는 `--from-stdin` JSON의 `roleId`, `secretId`, `authPath`를 사용합니다. 비밀 값을 명령행 인자나 셸 기록에 넣지 않습니다.

**OpenBao 재시작/재배포 시 데이터가 유지되면 AppRole을 재사용합니다. OpenBao 데이터가 완전히 초기화되면 AppRole과 IBM 비밀 값도 복원해야 합니다.** 자동 재인증은 유효한 서비스 신원으로 새 접근 토큰을 받는 기능이며, 삭제된 권한이나 IBM 비밀 값을 임의로 재생성하는 기능은 아닙니다. `mc-admin-cli` 소스와 기본 배포 파일은 변경하지 않습니다.

### 52번 서버 AppRole 실환경 검증 (2026-10-16)

- AM 이미지 `mc-application-manager:ibm-approle-20261016`을 AM 서비스에만 배포했다. 컨테이너를 한 번 더 강제 재생성한 뒤에도 암호화된 설정과 원래 암호화 키가 유지되고 IBM 인증정보 읽기·IBM IAM 로그인이 성공했다. 다른 서비스 컨테이너는 교체하지 않았고 mc-admin-cli 소스와 기본 Compose는 변경하지 않았다.
- 실제 OpenBao 접근 토큰 TTL을 5초로 설정하고 7.5초 대기해 이전 토큰의 403을 확인했다. 배포된 AM 클래스가 AppRole로 새 토큰을 발급받아 IBM 인증정보 읽기·IAM 로그인에 성공했다. 별도로 TTL 내 토큰을 운영자가 폐기해 403 응답 후 1회 재로그인하는 경로도 검증했다. 운영 TTL/최대 TTL은 검증 후 1시간으로 복원했다.
- IBM 경로 이외 CSP 경로와 OpenBao 관리 API는 403이었다. AppRole에는 IBM 경로 읽기 전용 정책만 있으며 default/admin 정책은 없다. 발급 토큰과 IBM API 키는 출력하거나 저장하지 않았다.
- 기존 IBM 클러스터 `k8s-mariadb-data-init-ibm-01`에 AM의 정상 비동기 배포 API로 Jupyter를 한 번 설치해 `SUCCEEDED`를 확인했다. Traefik PROXY protocol/HTTP 준비도 AM이 수행했다. 허용 IP의 로그인 페이지 접근, 비허용 IP 및 위조 X-Forwarded-For 요청의 403, Jupyter 인증 API, 샘플 노트북 Python 커널 실행을 확인했다.
- 전용 테스트 Prefix에 READ_WRITE 업로드·READ_ONLY 다운로드가 성공하고 READ_ONLY 업로드는 거절됐다. 기존 공통 예외 처리기는 이 거절을 HTTP 200 / 본문 `code=9999`, `detail=This Object Storage grant is read-only.`로 반환하며 URL 데이터는 발급하지 않는다. 검증은 본문 오류도 확인했다.
- AM의 Restart 후 실제 Pod UID가 바뀌어도 저장 노트북의 SHA-256과 마커 파일이 유지됐고 Object Storage SSH 터널이 복구됐다. 테스트 완료 후 정상 Uninstall, 테스트 PVC/PV 및 실제 IBM 디스크 삭제, 전용 테스트 객체·버전 정리, grant 회수를 확인했다. 기존 클러스터·공유 Ingress·원래 버킷은 유지했다.
- Kubernetes 서비스 회귀 테스트 539건이 실패·제외 없이 통과했다. 이번 작업에는 프런트엔드 소스 변경이 없으며, 배포 JAR에 현재 dist 43개 파일이 모두 같은 내용으로 포함됐음을 확인했다.

이 검증은 HTTP와 Object Storage 연동을 대상으로 했다. DNS/TLS 인증서 발급이나 다른 CSP의 실배포를 추가 검증한 결과는 아니다. 현재 52번 이미지는 로컬 검증 태그이며, 통합 배포에서 동일 기능을 사용하려면 이 소스로 빌드한 AM 이미지를 배포해야 한다.

## 신규 환경의 인증정보 재사용 조사 (2026-10-16)

이 절은 초기 조사 기록이다. 이후 위의 IBM managed Secret Provider 경로에서 IAM 발급 및 기존 ALB/VPC 조회를 검증해 기본 인증 경로로 추가했다. 추가 NLB 대안은 채택하지 않았다.

52번의 실제 배포 버전은 Tumblebug 0.13.6 / Spider 0.13.17이다. AM이 원래 받는 Tumblebug Basic 인증만 사용해 IBM 연결과 등록 credential 조회 범위를 확인했다. `POST /tumblebug/forward/credential/ibm`이 조회하는 Spider credential의 `ApiKey`, `S3AccessKey`, `S3SecretKey`는 모두 `Hidden for security.`로 반환됐다. Tumblebug의 OpenBao 연결은 정상이어도 이 조회에서 원문 키를 제공하지 않는다.

- Spider REST `GetCredential`은 마스킹된 조회 함수를 사용한다. 내부 `GetCredentialDecrypt`는 Go 내부 함수이며 원문 조회 REST API로 노출되지 않는다.
- Spider의 IBM `GenerateClusterToken`은 명시적으로 미지원 오류를 반환한다. AWS/GCP용 클러스터 토큰 API를 IBM IAM 토큰 발급에 사용할 수 없다.
- Tumblebug `forward/*`는 Spider로 GET을 전달한다. 임의의 IBM Cloud API나 OpenBao 관리 API에 대한 인증 프록시가 아니다. 현재 IBM 드라이버에는 AnyCall 확장도 없다.
- NHN Cinder 설치는 등록된 NHN 자격증명을 재사용하지만 AM용 OpenBao 읽기 토큰과 마운트는 별도로 준비한 방식이다. 이 성공은 새 환경에서 추가 인증 설정이 불필요하다는 증거가 아니다.
- IBM이 문서화한 선택 클러스터의 `kube-system/storage-secret-store`도 읽기 전용으로 확인했다. 현재 클러스터의 `g2_api_key`와 `iam_api_key`는 일반 IAM 키 형식이 아니어서 원문 자격증명 재사용 경로로 채택하지 않았다. 비밀 값 출력·저장 및 복호화 우회는 수행하지 않았다.

별도 OpenBao 등록을 없애는 대안으로 AM이 Tumblebug의 기존 Kubernetes 접속정보를 사용해 전용 Traefik과 `externalTrafficPolicy: Local`인 VPC NLB Service를 생성할 수 있다. IBM Cloud Controller가 NLB를 생성하므로 AM에 IBM API 키를 추가 전달하지 않는 구조다. 이 대안은 추가 NLB 비용과 새 접속 엔드포인트가 발생하며, 기존 IBM 관리형 ALB 준비 방식과 구조가 다르다. 구현 전에 이 구조 선택이 필요하다. 현행 배포에 추가 NLB나 새 Ingress Controller를 생성한 상태는 아니다.

조사 근거:

- [Spider credential 조회 구현](https://github.com/cloud-barista/cb-spider/blob/v0.13.17/cloud-info-manager/credential-info-manager/CredentialInfoManager.go)
- [Spider IBM ClusterHandler](https://github.com/cloud-barista/cb-spider/blob/v0.13.17/cloud-control-manager/cloud-driver/drivers/ibm/resources/ClusterHandler.go)
- [Tumblebug forward REST 구현](https://github.com/cloud-barista/cb-tumblebug/blob/v0.13.6/src/interface/rest/server/common/utility.go)
- [IBM 클러스터 storage-secret-store 설명](https://cloud.ibm.com/docs/containers?topic=containers-missing_permissions)
- [IBM Kubernetes Service를 통한 VPC NLB 생성](https://cloud.ibm.com/docs/containers?topic=containers-setup_vpc_nlb)
- [Traefik Kubernetes 설치](https://doc.traefik.io/traefik/setup/kubernetes/)

## 기존 mc-admin-cli 통합 배포에서 인증 설정 유지 (2026-10-16)

`mc-admin-cli` 소스나 기본 Compose를 변경하지 않습니다. AM 이미지에 포함된 운영자 명령으로 **OpenBao 주소와 IBM 읽기 전용 토큰을 최초 등록**하면, AM DB의 `ibm_ingress_openbao_configuration`에 namespace/connection별로 보관합니다. 토큰은 AES-256-GCM으로 암호화하며 IBM API 키 자체는 저장하지 않습니다. 일반 애플리케이션 배포 요청이나 공개 HTTP 설정 API로 토큰을 받지 않습니다.

암호화 키는 통합 배포가 이미 AM에 마운트하는 `/nexus-data/.am/ibm-openbao.key`에 최초 생성됩니다. 디렉터리와 파일 권한은 각각 700/600입니다. 키 원문은 DB, 이미지, Git, 명령행 인자에 포함되지 않습니다. 저장 설정은 정확히 일치하는 namespace/connection의 기존 토큰 파일 설정보다 우선합니다. 저장 설정이 없으면 아래의 기존 토큰 파일 방식도 사용할 수 있습니다. 저장 설정이 손상되거나 읽기 권한이 거절된 경우 다른 인증정보로 자동 우회하지 않습니다.

Docker 접근 권한이 있는 운영자가 AM 저장소의 다음 명령을 실행합니다. Python 3과 Docker CLI가 필요하며, Java/JDBC 드라이버는 실행 중인 AM 이미지의 것을 사용합니다. Spring 서버나 스케줄러를 추가 기동하지 않습니다.

```sh
# AM의 기존 네트워크에서 접근 가능한 OpenBao 주소를 지정합니다.
# 비밀 토큰은 숨김 프롬프트에 입력하며 명령행이나 셸 기록에 넣지 않습니다.
python3 scripts/configure_ibm_openbao.py configure \
  --namespace default --connection ibm-jp-tok \
  --url http://OPENBAO_REACHABLE_HOST:8200

python3 scripts/configure_ibm_openbao.py verify \
  --namespace default --connection ibm-jp-tok
```

OpenBao가 AM과 Docker 네트워크를 공유하지 않으면 내부 서비스명은 해석되지 않습니다. 이 경우 OpenBao가 이미 공개한 포트에 연결 가능한 사설 호스트 주소 등을 지정합니다. 주소는 환경별 설정이며 특정 서버 IP나 Docker gateway를 소스에 고정하지 않습니다. HTTP는 신뢰할 수 있는 내부 연결에서 사용하고, 외부 연결에는 신뢰되는 인증서의 HTTPS 주소를 사용합니다. 등록 전에는 해당 경로의 IBM 자격증명 읽기를 검증하며, 실패한 값으로 기존 설정을 교체하지 않습니다.

토큰은 `secret/data/csp/ibm` 읽기 전용이어야 합니다. 기본 경로가 아닌 등록 경로를 사용하는 경우 `--secret-path`를 지정하고 `application.yaml`의 IBM 프로필 경로도 일치시킵니다. namespace/connection/region 프로필과 IBM 계정 권한 설정은 여전히 필요합니다. 관리자 토큰을 AM에 등록하지 않습니다. AM은 토큰 발급이나 권한 확대를 수행하지 않습니다.

그 뒤에는 **새 AM 이미지를 기존 mc-admin-cli 통합 배포로 실행**하면 저장한 설정을 다시 읽습니다. 고정 토큰 대신 위의 AppRole을 등록하면 접근 토큰 만료도 자동으로 처리합니다. 별도 AM 배포 스크립트나 OpenBao 토큰 파일 마운트가 필요하지 않습니다. 다음 항목은 유지해야 합니다.

- AM PostgreSQL 데이터와 `/nexus-data/.am/ibm-openbao.key`를 함께 유지·백업합니다. 키가 없어지면 기존 토큰을 복호화할 수 없으며 AM은 자동으로 새 키를 덮어쓰지 않습니다.
- 다른 체크아웃 경로가 새 데이터 디렉터리를 사용하는 경우 DB와 키도 해당 환경으로 복원해야 합니다. 컨테이너 교체와 데이터 초기화는 다릅니다.
- OpenBao와 등록된 IBM 비밀 값이 유지되고 저장 주소에 계속 접근할 수 있어야 합니다. 고정 토큰 방식에서는 토큰 만료/폐기 후 `configure`로 유효한 읽기 토큰을 다시 등록합니다. AppRole 방식은 접근 토큰 만료/폐기를 자동 처리합니다. OpenBao 데이터 초기화로 AppRole이나 IBM 비밀 값이 없어지면 복원이 필요합니다.

`IBM_INGRESS_CREDENTIAL_KEY_FILE`로 키 경로를 변경할 수 있지만, 변경 경로도 기존 통합 배포에서 영속화되는 위치여야 합니다. 자동화 입력은 `--from-stdin`의 JSON을 사용할 수 있으며 키는 `namespace`, `connectionName`, `openBaoUrl`, `token`, `secretPath`입니다. 비밀 값을 파일이나 명령행에 기록하지 않고 표준입력으로 전달합니다.

## OpenBao를 사용하는 HTTP 자동 준비 (환경 공통)

공통 `application.yaml`의 `app.ibm-ingress.automation`을 사용합니다. AM은 선택한 클러스터의 실제 ID와 connectionName을 Tumblebug에서 조회하며 특정 서버나 클러스터 ID를 사용하지 않습니다. 일치하는 Project/connection 프로필의 `allow-shared-lb-changes=true`이면 필요한 PROXY protocol을 자동 활성화하고 실제 Controller/Service 준비 후 앱을 배포합니다. 이미 준비된 클러스터에는 같은 변경을 반복하지 않습니다. Spec Check는 변경 없이 검사만 합니다.

IBM 키 원문은 OpenBao의 `IC_API_KEY`에서 읽어 메모리에서만 사용합니다. AM 서버 파일이나 배포 요청에 IBM 키를 넣지 않습니다. OpenBao용 인증 연결은 최초 등록해야 합니다. 위 AppRole 방식에서는 AM이 읽기 토큰을 자동 발급받으며 관리자 토큰은 사용하지 않습니다.

기본 통합 배포에서는 위의 AM DB 저장 방식을 사용합니다. 다음은 기존 토큰 파일/Compose override를 유지하는 환경에서의 대체 설정입니다. 두 방식 모두 최초 연결 설정을 생략하면 자동 준비는 동작하지 않습니다.

1. 해당 환경의 OpenBao에 IBM 자격증명이 등록되어 있어야 합니다.
2. `secret/data/csp/ibm` 읽기만 허용하는 토큰 파일을 준비합니다. 30일 토큰이면 만료 전에 새 토큰으로 교체합니다. 토큰 파일은 Git 밖의 절대 경로, 소유자 전용 권한으로 관리합니다.
3. [docker-compose.ibm-openbao.yaml](examples/ibm-ingress-automation/docker-compose.ibm-openbao.yaml)을 기존 Compose 파일에 추가합니다. 이 파일은 원래 AM 네트워크를 유지하며 OpenBao 네트워크와 토큰 읽기 전용 마운트를 추가합니다.
4. 환경변수에 실제 Project/connection/region 및 OpenBao 주소·네트워크·토큰 파일 경로를 지정하고 최신 AM 이미지를 사용해 AM만 다시 생성합니다.

| 환경변수 | 기본값 / 의미 |
| --- | --- |
| `IBM_INGRESS_NAMESPACE` | `default` — Tumblebug Project namespace |
| `IBM_INGRESS_CONNECTION_NAME` | `ibm-jp-tok` — 등록된 IBM connectionName |
| `IBM_INGRESS_REGION` | `jp-tok` |
| `IBM_INGRESS_ALLOW_SHARED_LB_CHANGES` | `true` — 자동 PROXY 변경 허용; `false`로 끄기 |
| `IBM_INGRESS_SECRET_PATH` | `secret/data/csp/ibm` — 해당 환경의 등록 IBM 자격증명 |
| `IBM_INGRESS_OPEN_BAO_URL` | `http://mc-infra-manager-openbao:8200` |
| `IBM_INGRESS_OPEN_BAO_NETWORK` | OpenBao가 연결된 실제 Docker 네트워크 이름; override 사용 시 필수 |
| `IBM_INGRESS_OPEN_BAO_TOKEN_HOST_FILE` | 읽기 전용 토큰 파일의 호스트 절대 경로; override 사용 시 필수 |

예를 들어 Compose project 이름이 `mcc`이면 OpenBao 네트워크는 `mcc_mc-infra-manager-network`일 수 있습니다. 다른 환경에서는 실제 이름을 사용합니다. 여러 Project/connection에는 추가 Spring 설정 파일의 `profiles` 배열로 각각의 namespace, connection-name, region, secret-path, allow-shared-lb-changes를 지정합니다. 프로필은 정확히 일치해야 하며 자격증명을 다른 연결에 자동으로 공유하지 않습니다.

```bash
docker compose -f /absolute/path/docker-compose.yaml \
  -f /absolute/path/docker-compose.ibm-openbao.yaml \
  up -d --no-deps mc-application-manager
```

실제 IBM 계정에는 클러스터 조회, 해당 리전의 LB/서브넷 조회와 Ingress LB 설정 변경 권한이 있어야 합니다. 준비 시 공유 LB가 재구성되어 기존 연결이 잠시 끊길 수 있습니다. HTTP 배포에는 인증서나 cert-manager 설정은 필요하지 않습니다. NGINX의 기존 Kubernetes 기반 HTTP 준비와 명시적 `api-key-file` 방식도 유지합니다.

## 적용 범위

SW Catalog의 Deploy에서 **선택한 Project/클러스터와 입력한 Host**를 사용한다.
특정 IBM 클러스터 ID나 도메인을 코드에 고정하지 않는다. 실제 CSP ID와 connectionName은
Tumblebug에서 조회하며, IBM VPC Kubernetes의 관리형 NGINX 또는 Traefik ALB를 대상으로 한다.
AWS 등 다른 CSP의 기존 배포 흐름에는 이 자동화가 개입하지 않는다.

기능 구현은 끝났지만 운영 계정/DNS 권한이 자동으로 생기지는 않는다. 관리자가 AM 서버에
프로필과 인증정보를 최초 등록해야 한다. 일반 배포 사용자는 매번 권한을 요청하거나
TLS Secret을 입력하지 않는다. 기본 도메인 자동 입력 및 `Use IBM domain` 버튼은 없다.

## 사용자 흐름

1. Project, K8s 클러스터, Catalog, Host/Path, 허용 IPv4 CIDR을 선택·입력한다.
2. Spec Check: 경로 충돌, IBM Controller/인증서 상태, 자동화 허용 여부, Kubernetes 권한 및
   필요한 DNS Secret 참조, LB 서브넷 여유 IP를 검사한다. 설치/발급/LB 변경은 하지 않는다.
   단, IAM 토큰 요청과 Kubernetes SelfSubjectAccessReview는 수행할 수 있다.
3. Deploy: 비동기 준비 작업을 시작한다. 현재 UI는 세부 준비 문구 없이 버튼의 작업 중 상태만 표시한다.
   - IBM 기본 인증서의 SAN에 포함되는 Host: 기존 Secret 재사용.
   - 수동 등록 Host: 기존 매핑/Secret 재사용. 만료·설정 오류는 임의 교체하지 않고 차단.
   - 자동 발급 허용 Host: cert-manager 확인 → 없으면 허가된 경우에만 설치 → DNS01
     ClusterIssuer/자격증명 Secret 준비 → Certificate 발급 대기 → 검증된 Secret 매핑 등록.
   - PROXY 미준비: IBM API로 실제 LB/서브넷 조회 → 여유 IP 확인 → 변경 의도 기록 →
     PROXY 활성화 요청 → 실제 Kubernetes Controller/Service 상태가 준비될 때까지 대기.
4. 준비가 성공해야 앱 배포 API를 호출한다. 이 API도 경로·인증서·PROXY 조건을 다시 검사한다.
   앱별 차트 매핑으로 Ingress를 만들고 허용 CIDR을 적용한다. 앱 Service의 NodePort/LB 우회 노출은 거부한다.

PROXY 준비는 입력한 사용자 CIDR을 신뢰 프록시 범위에 넣는 방식이 아니다.
**LB 서브넷 CIDR은 PROXY 헤더 신뢰 범위**, 사용자가 입력한 CIDR은 **앱 접근 허용 범위**다.
AM이 워커 포트를 공개하거나 LB를 직접 삭제하지 않는다. IBM의 설정 변경 작업이 LB를 교체하므로
같은 클러스터의 기존 앱도 일시적으로 연결이 끊길 수 있다. 소요 시간은 고정 보장하지 않는다.
공유 public/private LB가 서로 다른 PROXY 상태이면 자동 정렬하지 않고 운영자 검토를 요구한다.

## 관리자 최초 설정

예제 디렉토리: [examples/ibm-ingress-automation](examples/ibm-ingress-automation/).

- `application-ibm-ingress.yaml`: Project(Tumblebug namespace) + connectionName별 허용 범위.
  같은 연결의 새 클러스터도 선택한 실제 ID로 처리한다. 중복 프로필은 거부한다.
- `cluster-issuer.yaml`: Let's Encrypt DNS01 ClusterIssuer. DNS 제공자 설정을 실제 환경에 맞춘다.
  예제는 Cloudflare DNS이며 IBM 클라우드를 사용한다고 DNS 제공자도 IBM일 필요는 없다.
  HTTP01은 CIDR 제한에 막힐 수 있어 이 자동화에서는 지원하지 않는다.
- `dns-secret.yaml.example`: DNS API 자격증명. 실제 값은 Git 밖의 파일에 저장하고 0600으로 관리한다.
  Secret은 cert-manager namespace에 생성한다. 기존 값이 다르면 덮어쓰지 않는다.
- IBM API 키는 별도의 원문 텍스트 파일로 마운트한다. 인증서 개인키/DNS 토큰/IBM 키를
  요청 본문이나 프런트엔드에 전달하지 않는다. 서버 측 파일은 각각 최대 64 KiB다.

프로필 변경은 AM 재시작이 필요하다. 프로필이 가리키는 파일은 준비 시 읽는다.
기존 클러스터 DNS Secret 값이 변경된 경우에는 관리자가 안전하게 회전·정합성 확인해야 한다.

### 필요한 권한과 실행 환경

- AM의 Project/IAM 권한 검증을 유지한다(`app.project-scope.enabled=true`, 기본값).
  AM 접근자를 신뢰하지 않는 상태에서 검증을 끄고 자동화 계정을 연결하면 안 된다.
- IBM 계정: 선택한 클러스터 읽기·Ingress LB 설정 변경, 해당 리전의 VPC LB 목록/서브넷 읽기.
  사용하지 않는 계정 전체 관리자 키 대신 필요한 리소스/작업으로 제한한 서비스 ID를 사용한다.
  GET 허용만으로 PATCH 권한까지 증명되지는 않는다. 실제 PATCH 403은 준비 실패로 처리한다.
- Kubernetes: 기존 AM 배포 권한에 더해 필요한 IngressClass/Deployment/Service/ConfigMap/Secret 읽기,
  `kube-system/am-ingress-automation-lock` 생성·조회·갱신·삭제, `am-ibm-proxy-intent` 생성·조회.
- 인증서: cert-manager v1 CRD 조회, ClusterIssuer 생성·조회, cert-manager namespace의 DNS Secret
  생성·조회, 앱 namespace의 Certificate 생성·조회, TLS Secret 조회 및
  `am-ingress-tls-bindings` ConfigMap 생성·조회·갱신.
- cert-manager가 없을 때의 최초 설치는 CRD, ClusterRole/Binding, WebhookConfiguration,
  Namespace와 차트 내 namespaced 리소스 생성 등 클러스터 범위 권한이 필요하다.
  SelfSubjectAccessReview로 주요 권한을 사전 검사하지만 Helm admission/정책까지 보장하지는 않는다.
  기존 cert-manager가 있으면 AM은 업그레이드/재설치하지 않는다. 부분 설치/비지원 v1 API는 차단한다.
- 기본 차트 `v1.20.2`는 Kubernetes 1.32–1.35에서만 신규 설치를 허용한다.
  `v1.21.x`를 설정하면 1.33–1.36 범위를 허용한다. 다른 버전은 운영자가 검증한 설치를 먼저 준비한다.
  기존 cert-manager는 ClusterIssuer의 리소스 namespace가 `cert-manager`여야 한다.
- AM 실행 환경에 Helm이 필요하다(현재 Dockerfile에 포함). AM은 IBM IAM/Containers/VPC API와
  quay.io, 클러스터 API에 접근해야 한다. 클러스터는 cert-manager 이미지 저장소,
  Let's Encrypt, DNS 제공자 API 및 DNS 확인에 접근해야 한다.

AM은 권한을 스스로 부여하지 않는다. 클러스터 관리자가 허용한 kubeconfig와 서버 프로필을 사용한다.
DNS01 제공자에 별도 webhook이 필요한 경우 그 제공자 플러그인은 관리자가 먼저 설치해야 한다.

### DNS 설정은 별도

`allowed-domains`에는 실제 소유·관리하는 DNS 영역만 등록한다. `company.com`은
`app.company.com`과 더 깊은 하위 도메인을 허용하지만 `evilcompany.com`은 허용하지 않는다.
입력 Host의 와일드카드·IP·URL은 자동 발급 대상이 아니다.

DNS API 계정은 `_acme-challenge` TXT 검증에 사용한다. **cert-manager는 앱의 A/CNAME 레코드를
자동 생성하지 않는다.** 배포 전에 Host 또는 관리하는 와일드카드 DNS를 해당 클러스터의
IBM Ingress 엔드포인트에 연결해야 한다. IBM LB 교체 뒤 IP를 수동 고정한 A 레코드는 낡을 수 있으므로
지원되는 IBM Ingress 도메인/호스트명 기준 CNAME을 사용하고 교체 후 재검증한다.
DNS 통제권이 없는 `rclone.test.com` 같은 임의 주소는 입력만으로 발급되지 않는다.

처음에는 별도 테스트 Host/클러스터에서 Let's Encrypt staging issuer로 검증한다.
staging 인증서는 브라우저가 신뢰하지 않는다. 운영은 production issuer를 사용해야 하며,
기존 issuer/Certificate를 자동 변경하지 않으므로 테스트용과 운영용 리소스를 구분한다.

## mc-admin-cli / Compose 연결

기존 `conf/docker/docker-compose.yaml`에는 이 신규 자동화용 비밀 파일이 들어 있지 않다.
`installAll.sh`가 이 파일이나 DNS 권한을 대신 생성하지 않는다. 예제
`docker-compose.ibm-ingress.yaml`을 **추가 override**로 사용해 AM에만 읽기 전용 마운트를 추가한다.
기존 DB/다른 서비스/공유 네트워크를 변경할 필요는 없다.

예제는 새 소스로 빌드한 로컬 이미지 태그를 `AM_LOCAL_IMAGE`로 지정하도록 되어 있다.
기존 `pull_policy: always` 때문에 과거 이미지를 다시 받지 않도록 override는 `never`를 사용한다.
실제 운영 이미지 관리 정책이 레지스트리 기반이면 해당 불변 태그와 pull 정책으로 조정한다.
Compose 상대 경로는 첫 번째 Compose 파일의 디렉토리 기준임에 유의한다.

새 화면까지 JAR에 포함하는 로컬 빌드 순서(프로젝트 루트):

```bash
npm --prefix applicationFE run build
bash ./gradlew bootJar -PincludeFrontend
```

`-PincludeFrontend`는 방금 빌드한 `applicationFE/dist`를 JAR 리소스에 넣는다.
기존 `src/main/resources/static`의 파일이나 다른 미커밋 수정은 덮어쓰지 않는다.
프런트엔드를 먼저 빌드하지 않으면 오래된 dist가 포함될 수 있으므로 두 단계를 순서대로 실행한다.

## 재시도·중단·수명

- PROXY 요청 기록은 `SUBMITTED` → `ACCEPTED`로 갱신됩니다. IBM이 HTTP 400/401/403/404/405/406/415/422로 명확히 거절하면 `REJECTED`와 안전한 HTTP 상태/오류 코드가 저장되고, 다음 Deploy에서 실제 IBM 상태·LB 준비·서브넷을 다시 검사한 뒤 재시도합니다. 응답 본문이나 인증정보는 기록하지 않습니다.
- 타임아웃, 408/409/429/5xx 또는 요청 결과를 확인할 수 없는 오류는 `UNKNOWN`으로 남기며 자동 재전송하지 않습니다. 이전 버전의 `SUBMITTED` 기록도 같은 보호 대상입니다. 코드 재배포만으로 이런 기록을 삭제하거나 성공으로 바꾸지 않습니다.
- 운영자가 기존 요청이 반영되지 않았고 진행 중인 LB 작업/AM 잠금이 없음을 확인한 경우에만, 해당 클러스터의 AM 소유 `am-ibm-proxy-intent`를 백업하고 `state=OPERATOR_REVIEWED_RETRY`로 갱신할 수 있습니다. 이후 사용자의 Deploy가 잠금을 획득하고 다시 확인한 뒤 한 번 요청합니다. 이전 요청 시각·상태·오류는 새 기록에 남습니다. `proxyProtocol=false` 조회 한 번만으로 결과 불명확 기록을 지우지 않습니다.

- 동일 클러스터의 같은 준비 요청은 실행 중 job ID를 재사용한다. 다른 설정은 중복 실행을 거부한다.
  AM 내부 작업자 2개/대기 4개, 클러스터 ConfigMap 잠금으로 공유 변경을 직렬화한다.
- IBM PATCH 전에 변경 의도를 영속 기록한다. 응답 유실/결과 불명확 오류 뒤 IBM 설정이 여전히 false이면
  자동 재전송하지 않는다. 운영자가 실제 IBM 작업 결과를 확인한 뒤에만
  `kube-system/am-ibm-proxy-intent` 재시도 여부를 판단한다. 무조건 삭제해서 우회하면 안 된다.
- 잠금은 정상 종료 시 해당 작업의 것만 해제한다. 프로세스 비정상 종료 시 최대 3.5시간 만료를 기다리거나,
  실제 작업이 끝났음을 확인한 운영자가 정리한다. LB/발급 실패에 대한 공유 리소스 자동 롤백은 하지 않는다.
- HTTP 준비 API는 `/applications/k8s/ingress/preparations` POST와 `/{id}?namespace=...` GET이다.
  상태는 QUEUED/RUNNING/READY/FAILED. 메모리 상태는 재시작 시 유실된다.
  준비 리소스/변경 의도는 클러스터에 남으며 재시도는 그것을 검사한다. 완료 job은 2시간 보관한다.
- 화면 취소/닫기/Project 변경은 **뒤따르는 앱 배포를 중단**한다. 이미 요청한 공유 LB 변경·인증서 발급은
  중간 취소하지 않고 준비 작업이 끝날 수 있다. 앱 배포 API 호출 뒤의 취소는 기존 동작과 같다.
- 각 준비 대기의 기본 제한은 40분, 설정 상한은 60분이다. 인증서 발급 지연·DNS 오류 등은
  실패로 알려주고 기존 Certificate 요청을 재사용한다. 반복 요청으로 무조건 새 인증서를 발급하지 않는다.
- cert-manager는 앱의 하위 Helm 차트가 아닌 클러스터 공용 릴리스다. 앱 삭제 시 cert-manager,
  ClusterIssuer, DNS Secret, Certificate, TLS Secret/매핑을 자동 삭제하지 않는다.
  이후 재사용과 인증서 갱신을 위해 유지하며, 불필요해지면 관리자가 사용 여부 확인 후 정리한다.
- 인증서 갱신은 cert-manager가 담당한다. AM은 배포 시 SAN·유효기간·키 일치를 확인한다.
  운영자는 Certificate Ready/만료일, DNS API 권한, 발급 오류·할당량을 모니터링한다.
  클러스터 삭제 시 내부 리소스는 사라지지만 외부 DNS 레코드/서버에 저장한 API 키는 별도 정리 대상이다.

## 검증 수준과 운영 전 확인

로컬 테스트는 실제 Fabric8 HTTP 직렬화와 모의 Kubernetes API, 모의 IBM/CA 응답을 사용한다.
실제 IBM LB 재생성, 실제 Let's Encrypt 발급/갱신, 인터넷 허용/비허용 IP 접속을 대체하지 않는다.
운영 전에 소유한 테스트 도메인과 승인된 IBM 클러스터로 다음을 검증해야 한다.

1. Spec Check만으로 리소스가 바뀌지 않는지, Deploy가 선택한 클러스터에만 적용되는지.
2. LB 교체 후 기존 앱, 새 Host의 DNS/HTTPS 신뢰체인이 정상인지.
3. 허용 IP의 HTTP/HTTPS 성공, 비허용 IP의 접근 거부, 위조 X-Forwarded-For 우회 불가.
4. 동일 Host 재시도에서 추가 LB 교체/불필요한 Certificate 생성이 없는지.
5. DNS 인증 오류·권한 부족 시 앱이 외부에 노출되지 않고 안전하게 실패하는지.

## 공식 근거

- [IBM Ingress PROXY protocol 및 LB 교체 조건](https://cloud.ibm.com/docs/containers?locale=en&topic=containers-comm-ingress-annotations)
- [IBM 공식 SDK의 Get/Patch LB API 계약](https://github.com/IBM-Cloud/bluemix-go/blob/master/api/container/containerv2/alb.go)
- [cert-manager Helm 설치](https://cert-manager.io/docs/installation/helm/)
- [cert-manager DNS01](https://cert-manager.io/docs/configuration/acme/dns01/)
- [cert-manager 지원 Kubernetes 버전](https://cert-manager.io/docs/releases/)
# 2026-09-11: hosts 파일 기반 HTTP 배포

- 현재 IBM 설치 UI는 **HTTP를 기본으로 사용하고 Protocol 선택창을 제공하지 않습니다.** 카탈로그에 TLS 기본값이 있어도 Spec Check·준비·Deploy 요청 모두 `ingressTlsEnabled=false`, `ingressTlsSecret=null`로 전달합니다. 입력한 Host를 그대로 사용하며 인증서 조회/발급·IBM API 키·cert-manager가 필요하지 않습니다. 기존 백엔드 HTTPS API와 이미 배포된 HTTPS 앱·인증서는 변경하지 않습니다.
- IBM Ingress 준비는 내부적으로 계속 수행하지만 버튼 아래의 세부 준비 상태 문구는 표시하지 않습니다. 준비 실패는 오류 알림으로 안내하고 앱 배포를 차단합니다.
- IBM/다른 CSP/VM 공통으로 작업 중 입력과 재클릭을 막고, 배포 API 성공 시 완료 화면에 `Close`와 `View Apps Status`를 제공합니다. 성공 후 같은 폼에서 다시 배포할 수 없으며, 실패 시에만 재시도할 수 있습니다. 새 팝업을 열거나 Project가 바뀌면 초기화합니다. 취소·Project 변경 후 도착한 이전 응답은 새 폼을 완료 상태로 바꾸지 않습니다. 완료는 배포 API의 성공을 뜻하며 지속적인 Running/Healthy 상태는 Apps Status에서 확인합니다.
- Spec Check는 읽기/권한 확인만 수행합니다. Deploy 준비 단계에서 선택한 IBM 클러스터의 기존 Kubernetes 인증 정보로 managed LoadBalancer Service의 HTTP 80 및 PROXY feature와 `ibm-k8s-controller-config`를 설정합니다. MCMP의 다른 서비스 소스·이미지는 변경하지 않습니다.
- 신뢰할 PROXY CIDR은 사용자 허용 CIDR이 아니라, MCMP 등록 VPC/서브넷과 실제 worker의 subnet-id/InternalIP를 교차 검증하여 얻습니다. 불일치·미등록·혼합 PROXY 상태는 배포 전에 거부합니다.
- 기존 LB/컨트롤러를 삭제하거나 새로 만들지 않고, 기존 HTTPS 포트·인증서·다른 annotation은 보존합니다. 단, 동일 클러스터의 shared controller 설정이므로 기존 연결은 재설정될 수 있습니다.
- 준비 완료에는 새 `EnsuredLoadBalancer` 이벤트와 안정적인 HTTP 응답이 필요합니다. 실패 시 AM이 바꾼 필드만 원복하고 앱 배포를 중단합니다. 재시작/동시 변경으로 결과가 불확실하면 `kube-system/am-ibm-kubernetes-ingress-setup`의 백업과 상태를 운영자가 확인해야 합니다. 원복 후에도 IBM 측 반영에는 시간이 걸릴 수 있습니다.
- IBM Provider 1.35의 기존 LB에 포트를 추가하는 경로(`CreateLoadBalancerPool`)는 PROXY 옵션을 누락합니다. 새 HTTP listener가 만들어지면 AM 소유 annotation으로 한 차례 추가 reconciliation을 요청하여 기존 pool 갱신 경로가 PROXY를 반영하게 합니다. PROXY를 끄거나 LB를 삭제하지 않으며, 두 번째 provider 완료 이벤트와 HTTP 응답까지 기다립니다. 이미 HTTP가 있는 클러스터에는 이 추가 단계를 반복하지 않습니다.
- HTTP에는 암호화가 없습니다. hosts 파일은 DNS 해석만 대체합니다. 운영 로그인·민감 데이터에는 유효한 인증서가 있는 HTTPS를 사용합니다.

구현 근거: [IBM Cloud Provider release-1.35의 기존 LB pool PROXY 갱신 처리](https://github.com/IBM-Cloud/cloud-provider-ibm/blob/release-1.35/pkg/vpcctl/vpc_lbaas.go), [IBM Ingress 설정 문서](https://cloud.ibm.com/docs/containers?topic=containers-comm-ingress-annotations).

### 실제 검증 (2026-09-11)

#### HTTP 전용 UI 및 완료 상태 후속 배포

- 34번 서버 이미지: `mc-application-manager:local-ui-http-complete-20260911-45391105`.
- 실행 JAR SHA-256: `45391105f0964b89974cf81bca1746cbda455a448e2745f09108fa3382d34d33`.
- SW Catalog / Apps Status / Repository / Install SW 진입 URL은 모두 HTTP 200이며 새 `index-BKFNv1Gc.js`를 제공한다. 설치 폼 번들 `applicationInstallationForm-CJ8rrk5z.js`도 로컬 빌드와 SHA-256이 일치한다.
- HTTP/완료 UI 23건, 준비/완료 흐름 38건, Spec Check 33건 통과. VM 선택·클러스터링 24개 카탈로그/288개 렌더링 조건 및 iframe 연동 회귀 테스트도 통과. 백엔드 807건 중 실패/오류 0, 제외 1, 프런트엔드 타입 검사·운영 빌드 성공.
- AM healthy 및 `/readyz` 200, 기존 환경변수/볼륨/포트/네트워크 동일, 다른 49개 컨테이너 ID 변경 없음. `pull_policy: never` override로 기존 원격 이미지 재다운로드를 방지한다.
- 이 후속 작업의 성공/실패/재클릭/취소/화면 이동은 실제 프런트엔드 함수를 모의 API와 실행하고 실제 footer를 렌더링해 검증했다. IBM 앱을 추가 생성하는 실배포 테스트는 반복하지 않았다. 아래 IBM 실클러스터 검증은 앞선 HTTP 기능 배포 때의 기록이다.
- 배포 설정: `/home/ubuntu/am-ui-http-complete-20260911-CxFseW/override.yaml`. 이전 AM 이미지와 Compose override는 롤백용으로 유지한다. 현재 화면을 새로고침해야 새 번들이 적용된다.

#### 앞선 HTTP 기능 실클러스터 검증

- 34번 서버 최종 이미지: `mc-application-manager:local-ibm-http-20260911-e617759a`. 로컬 HEAD `1401073`에 미커밋 변경을 포함한 JAR이며, 컨테이너 내 JAR SHA-256은 `e617759adef12b331bc4ef4debfce70d4826413fb6089b808e9359bdabf1ceea`와 일치한다. 다른 MCMP 컨테이너 49개는 변경하지 않았다.
- 백엔드 807건: 실패/오류 0, 제외 1. 이 중 실제 오프라인 Helm 렌더링 200건 포함. 프런트엔드 관련 70건 통과.
- 실제 IBM 클러스터 `k8s-mariadb-data-init-ibm-01`에서 HTTP 임의 Host 3종, 전체 공개 CIDR 거부, 잘못된 class 거부, 인증서 없는 HTTPS 거부를 확인했다. Spec Check 전후 실제 Service/ConfigMap 설정은 동일했다.
- 최초 실증 중 IBM의 새 HTTP pool PROXY 누락을 로그로 발견했다. AM 소유 annotation을 이용한 진단용 추가 동기화로 HTTP 회복을 확인한 뒤, 같은 추가 동기화를 자동화한 최종 이미지로 교체했다. 최종 이미지에서는 기존 준비 설정의 무변경 재사용 및 실제 앱 배포를 검증했다. 최종 이미지로 초기 상태의 클러스터를 다시 만들지는 않았다.
- Grafana를 `am-http-validation.test`에 HTTP로 배포해 실제 Ingress에 TLS가 없고 CIDR/HTTP redirect annotation이 적용됨을 확인했다. LB IP 두 개 각각에서 허용 출발지는 `/api/health` 200(database=ok), 비허용 출발지는 403이었다. 허용 IP로 X-Forwarded-For/X-Real-IP/Forwarded를 위조해도 비허용 출발지는 403이었다(총 8회).
- 동일 Host/Path의 Spec Check 중복 차단을 확인한 뒤 테스트 Grafana만 AM의 UNINSTALL API로 제거했다(배포 이력 12, 상태 ID 10). 실제 Pod/Service/Ingress 제거 및 기존 MariaDB Ready 상태를 재확인했다. 테스트 앱 자체는 재배포해야 다시 사용할 수 있으며 배포/삭제 이력은 남는다. 공유 IBM 설정은 유지했고, LB IP 두 개 모두에서 기존 IBM 인증서를 검증한 HTTPS health 응답이 200이었다.
