# IBM Ingress 및 자체 도메인 인증서 자동 준비

> 현재 HTTP 경로는 문서 하단 **2026-09-11: hosts 파일 기반 HTTP 배포**를 따릅니다. 아래 IBM API 키/프로필 및 인증서 준비 항목은 기존 HTTPS 자동화 경로에만 해당하며, HTTP 배포에는 필요하지 않습니다.

## 적용 범위

SW Catalog의 Deploy에서 **선택한 Project/클러스터와 입력한 Host**를 사용한다.
특정 IBM 클러스터 ID나 도메인을 코드에 고정하지 않는다. 실제 CSP ID와 connectionName은
Tumblebug에서 조회하며, IBM VPC Kubernetes의 관리형 NGINX ALB를 대상으로 한다.
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

- 동일 클러스터의 같은 준비 요청은 실행 중 job ID를 재사용한다. 다른 설정은 중복 실행을 거부한다.
  AM 내부 작업자 2개/대기 4개, 클러스터 ConfigMap 잠금으로 공유 변경을 직렬화한다.
- IBM PATCH 전에 변경 의도를 영속 기록한다. 응답 유실/실패 뒤 IBM 설정이 여전히 false이면
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
