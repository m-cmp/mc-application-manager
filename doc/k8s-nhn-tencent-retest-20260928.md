# NHN / Tencent 재시험 — 2026-09-28

> 실배포 시험 종료 후 사용자의 요청으로 내장 차트 버전은 `0.1.0`으로 유지하고,
> 제안했던 `0.1.1` DB 자동 갱신을 제거했다. 아래 실배포 결과와 JAR 해시는 변경 전
> 시험 빌드의 기록이다. NHN/Alibaba 기능 수정은 유지하며, 최종 버전 정책 변경은
> 로컬 회귀 검증 대상이다. 이 조정을 위해 클라우드 자원을 다시 생성하지 않았다.

## 범위와 환경

- 재설치된 `52.24.20.63`에서 수행. Tumblebug 0.13.4, Spider 0.13.9.
- 테스트 전용 namespace `amfix-0928`, 별도 AM / PostgreSQL 사용.
  테스트 AM은 `127.0.0.1:19884`에만 바인딩했다.
- 이전 NHN/Alibaba 수정이 포함된 로컬 JAR을 별도 컨테이너에 마운트했다.
  JAR SHA-256: `a732bf5e6ee43892872fadbc97f6da2bc644f5878fe4466c388e1bb0727c5c69`.
- 운영 AM 이미지 교체, 공유 서비스 재시작, 공유 클러스터 설정 변경은 하지 않았다.
- 비용을 줄이기 위해 CSP별 워커 1대, 앱 순차 배포, NHN 정리 후 Tencent 생성 순서로 진행했다.
- 배포는 Spec Check → AM deployment submission → 실제 Pod/Service 검증으로 진행했다.
  Spec Check의 거부를 무시하거나 카탈로그 권장 사양을 낮추지 않았다.
- Ingress와 애플리케이션 외부 포트는 사용하지 않았다. 공개 Ingress/CIDR, 브라우저 UI,
  HA/다중 노드, 부하 시험의 통과를 의미하지 않는다.

## NHN — 5종 통과, 클라우드 자원 정리 확인

- `kr1`, `m2.c4m8` 1대, Ubuntu 22.04.5, Kubernetes v1.34.3.
- Apache HTTP Server / Tomcat: HTTP 200 응답 확인.
- Redis / MariaDB / PostgreSQL: 정상 인증, 잘못된 비밀번호 거부, 데이터 기록 후
  Pod 교체 및 데이터 재조회 확인.
- Cinder CSI 설치 전 capability는 `driverReady=false`, 설치 후에는 `true`였다.
  관리형 `cinder_csi_plugin` v1.27.102-nks4를 **이번에 만든 클러스터에만** 설치했다.
- AM StorageClass 생성 API의 ext4 / Retain / WaitForFirstConsumer / 비기본 클래스 설정,
  중복 이름 거부 및 기존 클래스 UID 보존을 확인했다. 잘못된 이름·빈 이름·지원하지 않는
  디스크 타입 3가지 입력도 HTTP 400으로 거부됐다.
- 실제 DB 3종은 파일시스템 타입을 생략한 별도 Cinder 클래스로 시험했다.
  세 앱에만 제한된 `prepare-data-volume` 초기화가 적용되고, 본 컨테이너는 non-root였다.
  Apache/Tomcat에는 해당 초기화가 없었다.
- API가 만든 Retain 클래스는 설정 검증 후 **PVC가 없음을 확인하고 삭제**했다.
  실제 반복 시험에는 명시적인 Delete 정책의 테스트 클래스를 사용해 볼륨을 회수했다.
  운영 Retain 볼륨의 자동 삭제를 시험한 것은 아니다.
- 클러스터 `a2d23590-d315-49ac-9e08-7962eb26bce3`, 테스트 VM, 볼륨, SG, SSH 키,
  VPC/서브넷이 제거됐음을 Spider 목록과 NHN 원본 API로 대조했다.
  원래 있던 네트워크/보안그룹과 floating IP 연결 상태는 보존됐다.

새 NHN 클러스터에는 여전히 **관리형 Cinder CSI 사전 준비가 필요**하다.
이번 AM 수정은 필요한 스토리지 검사와 앱 볼륨 권한 초기화이며, 공유 클러스터의
관리형 CSI 애드온을 무조건 자동 설치하는 변경은 아니다.

## Tencent — 일반 클라이언트 경로 5종 통과

- `ap-seoul-1`, `SA2.LARGE4` 1대. MariaDB의 기존 4 vCPU 요구량을 만족하는 저가 사양 선택.
- 노드 이미지 `ubuntu22.04x86_64`를 명시했다. 실제 워커는 Ubuntu 22.04.5,
  Kubernetes v1.34.1-tke.8이며, kube-proxy 1/1 Ready 및 재시작 0회를 확인했다.
- 이전 Ubuntu 16.04 / iptables 경로 불일치로 클러스터가 정상화되지 않던 상태는
  이 조합에서 재현되지 않았다.
- 현재 Spider의 Tencent SG 생성에서 inbound/outbound를 동시에 보내면
  `InvalidParameter.Coexist`로 거부됐다. 시험 전용 SG를 inbound로 생성한 뒤
  outbound를 기존 Tumblebug 규칙 추가 API로 별도 등록했다. 실패 시 남았던 SG는
  요청 로그로 소유 ID를 확인하고 삭제했다. 공유 Spider 코드는 수정하지 않았다.
- 클러스터 생성 직후에는 관리 API가 준비되지 않아 노드그룹 생성이 거부됐다.
  원본 API의 `Running`과 미생성 노드그룹을 확인한 후 추가했다.
- 기본 제공 `cbs` StorageClass, 10Gi, RWO 사용. NHN 전용 초기화는 적용하지 않는다.
- Apache / Tomcat의 HTTP 200, Redis / MariaDB / PostgreSQL의 정상 인증·오류 인증 거부·
  Pod 교체 후 데이터 유지를 모두 확인했다. DB 3종의 기능 시험은 별도 클라이언트 Pod에서
  Service DNS로 접속했다. 시험 앱, PVC, 인증 Secret, 클라이언트 Pod 제거까지 확인했다.
- 클러스터 `cls-5x7nk0yx`, 워커 `ins-2mzc7zjp`, 루트/앱 디스크, 노드풀과 ASG/launch
  configuration, API용 LB `lb-m65qaq51`, 전용 SG/SSH 키/VPC/서브넷의 제거를 확인했다.
  클러스터 삭제 직후에는 LB 비동기 삭제가 끝나지 않아 SG 삭제가 한 차례 409로 거부됐다.
  LB가 사라진 것을 확인한 다음 SG 등 나머지 자원을 정상 삭제했다.
  다른 작업이 만든 default 프로젝트의 Tencent 자원은 삭제하지 않았다.

### 별도 관찰 사항: 자기 Service 접속

Redis Pod 안에서 직접 비교했다.

- localhost / 자기 Pod IP: `PONG`.
- Service DNS 조회: 정상, 올바른 ClusterIP 반환.
- 같은 Pod → 자기 ClusterIP / Service DNS: 8초 제한에서 시간 초과.
- 별도 클라이언트 Pod → Redis Pod IP / ClusterIP / Service DNS: 모두 `PONG`.

따라서 Redis 자체 미기동이나 DNS 해석 실패와는 구분된다. **자기 Service로 돌아오는
경로의 이상은 미해결**이며, 구체적인 CNI/커널 설정 원인까지 확정하지 않았다.
일반적인 별도 클라이언트 Pod → Service 경로의 인증·지속성 시험은 통과했다.
이 사실을 숨기거나 전체 네트워크가 무조건 정상이라고 판정하지 않는다.

### 단일 노드 제약

CoreDNS 2개 중 1개는 Ready, 나머지는 서로 다른 노드를 요구하는 anti-affinity 때문에
Pending이다. 비용 절감을 위한 단일 워커 시험의 가용성 제약으로 기록했다.
다른 시스템 Pod의 실패를 무시하지 않았고, 실제 Service DNS 기능은 별도로 검증했다.
클러스터의 CoreDNS 복제 수나 배치 정책을 임의로 수정하지 않았다.

## 로컬 재검증

- 관련 Gradle 테스트 17개: 실패/오류/skip 0.
- 프런트엔드 built-in 폼 검사 44개 시나리오 및 storage identity 검사 통과.
- `git diff --check` 통과.
- 9월 23일 전체 688개 테스트 결과와 구분한다. 이번에 전체 688개를 재실행한 것은 아니다.

## 정리 및 보호 대상 — 완료

- 두 CSP의 테스트 소유 클라우드 자원 삭제를 원본 API로 재확인했다.
- 테스트 AM/DB 컨테이너, 전용 Docker 네트워크/볼륨, 테스트 namespace와 전용 이미지
  참조를 제거했다. 테스트 데이터만 폐기했으며, 검증 기록은 별도로 보관한다.
- 원래 실행 중이던 **38개 컨테이너의 ID·이미지·시작 시각이 모두 동일**하고 실행 상태도
  유지됐다. 운영 AM 이미지 교체나 공유 서비스 재시작은 없었다.
- etcd endpoint health 정상, alarm 없음.
- 서버 컨테이너 비교용 증빙에서 불필요한 운영 환경변수/비밀값을 제거했다.
- 이번 검증에서 추가로 변경한 프로젝트 파일은 결과 문서다. 기존 미커밋 구현은 보존했으며,
  커밋/푸시/운영 AM 재배포는 하지 않았다.

다른 Jenkins 작업이 동시에 default 프로젝트 자원을 생성·삭제하고 있다.
Alibaba 초기화 인프라 `vm-csp-init-alibaba-01`의 DELETE는 `mc-workflow-manager-jenkins`
컨테이너 IP에서 시작된 요청으로 확인됐다. 이후 Tencent default 인프라 및
`default-shared-nhn-kr1-kr-pub-a` 네트워크 생성도 관찰됐다.
이 변경을 시험 정리 대상으로 삼거나 원복하지 않았다.
최종 etcd 비교에는 Alibaba/Azure 초기화 인프라와 일부 default SG/키/VPC 등 19개 기존
키의 차이가 기록됐다. 따라서 **서버 전체 메타데이터가 시험 시작과 완전히 동일하다고
주장하지 않는다**. 테스트 소유 자원 정리와 다른 작업의 변경을 분리해서 검토했다.

Tumblebug의 공용 이미지 카탈로그는 namespace 지정 등록 요청과 달리 system 범위로
조회/삭제되는 부분이 있다. 공용 카탈로그나 CSP 원본 이미지는 삭제하지 않는다.
남은 테스트 전용 etcd 참조만 소유 UID와 값을 확인하고 compare-and-swap으로 제거했다.

증빙은 서버의 `/home/ubuntu/am-retest-20260928/`에 제한 권한으로 보관한다.
이 폴더에는 kubeconfig 및 테스트 키가 포함되므로 원본 전체를 공개 저장소에 올리지 않는다.
최종 결과는 `public-summary.json`, `closeout.json`, `final-container-checks.json`,
`final-etcd-health.json`, `final-protected-metadata-diff.json`에 기록했다.
첫 성공 정리 시점의 클라우드 증빙과 이후 재확인(`recheck-*`)을 구분해 보존했다.

## 검증 도구에서 발견한 비배포 이슈

- 초기 도구가 Helm으로 직접 제거한 Apache/Tomcat의 AM 기록은 계속 활성 상태여서
  다음 Spec Check의 사용량에 포함됐다. 이미 제거된 테스트 릴리스만 검증해 기록을
  정리했고, 이후 삭제는 AM action API로 수행했다. 운영 DB를 직접 수정하지 않았다.
- `/api/applications/status/all`은 lazy-loaded `unifiedLogs`의 JSON 직렬화 오류를 반환했다.
  현재 프런트엔드가 사용하는 `/api/applications/status/groups?namespace=...`는 정상이며,
  도구도 그 경로로 맞췄다. 이 별도 API 오류에 대한 AM 코드 변경은 이번 시험에 포함하지 않았다.

## 후속 커밋 준비 — 내장 차트 0.1.0 유지

실배포 정리가 끝난 뒤 별도 사용자 요청에 따라 커밋할 소스를 정리했다.

- 내장 5종의 버전은 기존 `0.1.0` 유지. `0.1.1`로 갱신하는 startup SQL은 제거했다.
  기존 차트 매핑과 실행 중인 릴리스를 자동으로 바꾸지 않는다.
- NHN Cinder 준비 검사·조건부 볼륨 권한 초기화·ext4 StorageClass 생성과
  Alibaba `MemSizeMib` 처리 수정은 유지한다.
- 차트 패키지의 `version: 0.1.0`, 기존 DB 반복 초기화 시 버전/외부 매핑 보존,
  지원하지 않는 버전·사용자 지정 매핑을 내장 차트로 취급하지 않는 경우를 검증했다.
- **최종 소스로 전체 Gradle 테스트 688개를 다시 실행**했다.
  실패/오류 0, 별도 Docker/SSH fixture를 사용하는 opt-in smoke test 1개 제외.
  Helm lint/template 및 배포 values 조합 테스트도 포함한다.
- 프런트엔드 내장 차트 폼 52개 시나리오, Install SW parser 18개·VM 대상 해석 7개와
  iframe 연결 검사, VM clustering 카탈로그 24개·표시 조건 288개·전환 4개·제출 4개 통과.
  프런트엔드 타입 검사와 운영 빌드도 통과했다.
- 이 단계에서는 서버 이미지/DB를 변경하지 않았고, 클라우드 실배포도 반복하지 않았다.
  수정 전후 설치 구분에는 차트 버전 대신 AM 이미지 digest 또는 소스 커밋을 사용한다.
