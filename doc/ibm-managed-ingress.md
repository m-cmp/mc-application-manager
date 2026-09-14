# IBM 관리형 Ingress: HTTPS 인증서 선택

IBM Kubernetes 클러스터의 앱 배포는 기존 IBM NGINX ALB를 사용합니다. 준비가 완료된
클러스터는 기존 설정을 그대로 재사용합니다. 관리자가 자동화 프로필을 설정하면 AM이
PROXY protocol 준비 및 자체 도메인 인증서 발급을 수행할 수 있습니다.
[자동화 설정·권한·재시도 안내](ibm-ingress-automation.md)를 참고하세요.
다른 CSP의 기존 NodePort 방식은 변경하지 않습니다. 기존 앱의 Ingress나 인증서를 자동 이전하지도 않습니다.

## 배포 사용자

- Host는 사용자가 직접 입력합니다. IBM 기본 도메인 자동 입력과 `Use IBM domain` 버튼은 제공하지 않습니다.
- IBM 인증서 범위의 Host, 관리자가 등록한 자체 도메인 또는 자동 발급을 허용한 도메인을 입력합니다. 설정 조회는 등록 도메인과
  경고만 표시하며 사용자가 입력한 Host를 변경하지 않습니다.
- TLS 체크박스와 Secret 입력은 없습니다. IBM에서는 서버가 HTTPS와 Secret을 결정합니다.
- 허용 IPv4 CIDR을 입력하고 Spec Check를 실행합니다. 실제 배포 시에도 같은 검사를 다시 합니다.
- API에 남은 TLS 필드는 다른 CSP의 호환성을 위해 유지합니다. IBM의 `false`/Secret 직접 지정은
  무시하고 관리형 선택 정책을 적용합니다. Host/Path와 앱 설정은 그대로 사용합니다.

## IBM 기본 인증서

AM은 실제 Tumblebug 클러스터의 provider를 확인한 후 Kubernetes API로 다음을 읽습니다.

1. IBM 소유의 `public-iks-k8s-nginx` 또는 `private-iks-k8s-nginx` IngressClass.
2. `kube-system` 내 해당 class의 Controller Deployment가 사용하는 `--default-ssl-certificate` 참조.
3. 참조된 Secret의 인증서 SAN에서 클러스터의 `*.…containers.appdomain.cloud` 도메인.

여러 Controller의 참조가 다르거나, Secret이 없거나, 인증서가 만료/미발효 상태이거나,
인증서와 키가 다르거나, Host가 인증서 SAN 범위 밖이면 자동 선택을 중단합니다.
개인키는 일치 검사 시 메모리에서만 사용하며 화면/API 응답/로그/AM DB에 저장하지 않습니다.
루트 신뢰체인 및 실제 DNS/HTTPS 연결까지 이 검사만으로 보장하지는 않습니다.

일반 Helm 앱의 Kubernetes namespace는 기존처럼 `default`입니다. IBM이 갱신하는 원본
기본 Secret을 참조하므로 복사본의 갱신이나 별도 인증서 발급 기능이 필요하지 않습니다.
Jupyter의 기존 workload namespace 정책은 유지합니다. 다른 namespace의 Jupyter에는
해당 namespace에 관리자가 동기화한 Secret과 아래 매핑이 필요합니다.

## 자체 도메인 수동 등록 (자동 발급을 사용하지 않을 때)

인증서 발급, 자동 갱신, Kubernetes Secret 동기화, DNS 설정을 먼저 준비합니다.
Secrets Manager 또는 별도 관리 체계를 사용하며 IBM 기본 Secret은 덮어쓰지 않습니다.
그다음 앱 namespace에 다음 ConfigMap을 등록합니다. 예시는 실제 운영 값으로 바꿔야 합니다.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: am-ingress-tls-bindings
  namespace: default
data:
  bindings.json: |
    [
      {"host": "grafana.company.com", "secretName": "grafana-company-tls"},
      {"host": "*.apps.company.com", "secretName": "apps-company-tls"}
    ]
```

- 같은 클러스터·같은 앱 namespace의 Secret만 참조합니다. `namespace/secret`은 허용하지 않습니다.
- Host는 소문자 DNS 이름 또는 맨 왼쪽 한 단계 와일드카드입니다. `*.apps.company.com`은
  `grafana.apps.company.com`을 포함하지만 `apps.company.com`, `dev.grafana.apps.company.com`은 포함하지 않습니다.
- 정확한 Host 매핑이 와일드카드보다 우선합니다. 중복 Host 또는 잘못된 JSON/필드는 거부합니다.
- 등록된 Host라도 실제 인증서 SAN에 포함되어야 합니다. 깨진 사용자 매핑을 IBM 기본 인증서로 대체하지 않습니다.
- Secret은 `kubernetes.io/tls` 타입의 `tls.crt`, 암호화되지 않은 `tls.key`를 포함해야 합니다.
- ConfigMap과 Secret은 매번 읽으므로 AM 재시작 없이 변경·갱신을 반영합니다.
- 수동 운영에서는 AM에 대상 ConfigMap/Secret get, IngressClass get, managed Controller Deployment list 등
  읽기 권한을 부여합니다. 자동 발급을 사용할 때에는 AM이 검증한 인증서를 이 ConfigMap에 추가하므로
  제한된 쓰기 권한도 필요합니다. 일반 사용자에게 ConfigMap/Secret 직접 수정 권한을 주지는 않습니다.
- 인증서 Secret 데이터는 이 ConfigMap에 넣지 않습니다. AM에는 별도 자체 도메인 등록 화면을 추가하지 않습니다.

기본 인증서를 찾지 못해도 유효한 자체 도메인 매핑은 사용할 수 있습니다. 클러스터 설정 조회
API는 도메인 목록과 안전한 경고만 반환하며, project 권한 검증 후 조회합니다.

## CIDR과 외부 접근은 별도 조건

인증서 자동 선택은 CIDR을 위한 원본 IP 전달 설정을 대신하지 않습니다. 관리형 LB와
Controller 양쪽의 PROXY protocol 및 제한된 신뢰 LB 서브넷 CIDR, HTTPS 443 listener가
필요합니다. 자동화 프로필이 없으면 AM은 조건 미충족 시 배포를 차단합니다.
프로필에서 공유 LB 변경을 허용한 경우에만 IBM 공식 API로 PROXY 설정을 요청합니다.
관리자는 활성화 전에 LB 교체/접속 중단 영향을 검토해야 합니다.

설정 완료 후 허용 IP의 실제 HTTPS 응답, 비허용 IP의 403, 위조 X-Forwarded-For 우회 방지,
DNS/인증서 신뢰체인, 기존 공유 경로의 정상 동작을 별도로 검증해야 합니다.

## 참고

- [IBM 기본/자체 도메인 인증서](https://cloud.ibm.com/docs/containers?topic=containers-secrets)
- [Secrets Manager 공개 인증서와 갱신](https://cloud.ibm.com/docs/secrets-manager?topic=secrets-manager-public-certificates)
- [IBM ALB 원본 IP 전달](https://cloud.ibm.com/docs/containers?locale=en&topic=containers-comm-ingress-annotations)
