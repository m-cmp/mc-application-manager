# NHN Cinder CSI 검증 결과

## 2026-10-16 클러스터 위임 인증과 52번 실환경 설치

기본 `credential-source: cluster` 경로를 추가했다. AM은 선택한 NKS의 기존 `kube-system/os-trustee`로 공식 HTTPS Identity v3 토큰을 발급받으며, AM용 OpenBao 토큰 파일·AppRole·DB 암호화 키·프로젝트별 OpenBao 바인딩을 사용하지 않는다. NKS의 내부 HTTP 인증 주소와 `RegionOne`은 공개 API 호출에 사용하지 않는다. 기존 `openbao` 방식은 명시적으로 선택할 때만 작동한다.

| 검사 | 결과 |
|---|---|
| Kubernetes/Jupyter/IBM 및 NHN controller 회귀 테스트 | 596건 통과, 실패/오류/제외 0 |
| 위 결과에 포함된 NHN 테스트 | 81건 통과 |
| 기본 인증 로컬 REST 통합 테스트 | OpenBao URL/토큰 파일/바인딩 없이 Identity v3 → NKS 설치 → CSI 등록 → StorageClass 생성 통과 |
| 토큰 만료 임박 처리 | 단위 테스트에서 재인증·대상 프로젝트 재확인 및 설치 POST 1회 유지 검증 |
| 52번 AM 교체 후 인증 | 공식 Identity/NKS 조회 성공 |
| AM 컨테이너 강제 재생성 후 인증 | 추가 인증 설정/마운트 없이 재인증 및 NKS 조회 성공 |
| 실제 AM 설치 API | 작업 `ccdadb80-aad5-4579-948f-8d75d89510e2`가 `READY`로 완료 |
| NHN 관리형 add-on | `v1.27.102-nks4`, `CREATE_COMPLETE` |
| 실제 CSI 상태 | 컨트롤러/노드 Pod 모든 컨테이너 Ready, 전체 워커 1개 등록 |
| 통합 배포 구성/다른 서비스 | mc-admin-cli 원본 파일과 다른 서비스 컨테이너 50개 유지 |
| 프론트 | 수정 없음. 기존 dist 43개 파일이 배포 JAR 내용과 일치 |

실제 대상은 `default/k8s-mariadb-data-init-nhn-01`, NKS UUID `8ff98fcd-0b26-467b-befb-3b16951dff0a`이다. 설치한 Cinder add-on은 사용할 수 있도록 유지했다. 이번 NHN 검증에서는 Jupyter 배포나 실제 PVC/볼륨 쓰기 테스트를 실행하지 않았다. 토큰의 실제 12시간 만료를 기다리는 대신 만료 임박 분기 단위 테스트와 컨테이너 재생성 후 실 API 재인증을 확인했다. 별도 새 물리 서버에서 전체 mc-admin-cli 통합 배포를 실행한 것은 아니다.

52번 테스트 이미지: `mc-application-manager:nhn-identity-20261016`. AM 프로젝트만 수정·재배포했으며 Git 커밋/푸시나 공용 이미지 발행은 하지 않았다. 다른 서버는 이번 변경을 포함한 AM 이미지와 정상 등록된 NKS kubeconfig 및 클러스터 위임 Secret 접근 권한이 필요하다.

빌드 산출물 `build/libs/am.jar`의 SHA-256: `f8f780952d9882df5b42f49752750a09b215e7e3dac5f22fb9223148d4afc8ba`.

---

# 2026-10-06 기존 OpenBao 방식 로컬 검증

검증일: 2026-10-06. AM 기준 커밋은 `8f2181605822260caa97c118c0e1b1eae2ddbcdb`, 작업 브랜치는 `codex/nhn-cinder-addon`이다.

## 구현 범위

NHN 클러스터에만 Cinder CSI 설치 버튼과 AM 비동기 설치 API를 추가했다. Tumblebug에서 선택한 클러스터와 연결 정보를 확인하고, 기존 OpenBao NHN 인증정보로 IaaS 토큰을 발급받아 해당 리전의 NKS API를 호출한다. Tumblebug/Spider의 인증정보 조회 응답은 비밀 값이 가려져 있으므로 클라우드 인증정보는 서버에서 OpenBao의 기존 KV v2 값을 읽는다. 설정과 권한 예시는 [설정 안내](nhn-cinder-addon.md)에 있다.

설치된 CSI가 정상 동작하면 인증정보 조회와 설치 요청을 생략한다. 진행 중인 관리형 addon은 재사용하며, NKS 설치 완료와 모든 CSI 노드의 드라이버 등록을 확인한 후 기존 StorageClass 생성·선택 흐름을 진행한다. 기존 StorageClass가 있어도 드라이버가 준비되지 않으면 스토리지를 요구하는 Jupyter 배포를 막는다. 다른 CSP에는 NKS 요청을 보내지 않는다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| 전체 백엔드 테스트 | 총 729개: 728개 통과, 실패 0, 오류 0, 제외 1 |
| 새 NHN addon 백엔드 테스트 | 41개 통과 |
| 새 NHN 프론트엔드 테스트 | 15개 통과 |
| 프론트엔드 회귀 스크립트 | NHN을 포함한 10개 스크립트 모두 통과 |
| TypeScript 검사 및 Vite 프로덕션 빌드 | 통과 (`npm.cmd run build`) |
| 프론트엔드를 포함한 AM 실행 JAR | 생성 성공 (`-PincludeFrontend test bootJar`) |
| 변경 내용 공백 검사 | `git diff --check` 통과 |

제외된 테스트는 기존 `ObjectStorageTunnelSshSmokeTest.forwardsOverRealSshAndRecoversAfterReconnect()`이며 외부 SSH 테스트 환경이 필요하다.

`NhnCinderAddonLocalIntegrationTest`는 로컬 HTTP 서버에 OpenBao·NHN Identity·NKS 응답을 구성하고 Fabric8 Kubernetes 모의 서버를 사용한다. AM REST 설치 요청부터 작업 상태 조회, 인증정보 읽기, 토큰 발급, 프로젝트·리전 검증, 호환 버전 조회, Cinder 설치 요청, CSI 등록 확인, 준비 완료까지 검증했다. 반복 요청에서 추가 설치가 발생하지 않는 것과 기존 StorageClass API의 HDD/ext4/Retain/WaitForFirstConsumer 설정, Jupyter의 StorageClass 검증도 확인했다.

이 테스트에서는 실제 NHN 계정에 요청하지 않았으며 실제 Jupyter 컨테이너를 기동하지 않았다. 실환경 설치와 워크로드의 볼륨 연결은 검증 범위에 포함되지 않는다. 기능은 기본 비활성 상태이며 사용하려면 서버의 OpenBao 연결과 프로젝트·연결·리전 매핑을 설정해야 한다.

## 실행 산출물

파일: `build/libs/am.jar`

SHA-256:

```text
59C0A99BD330855C12DFF4D4E6109A11876A3472BE471588DAA9C12EE0CA9F50
```

백엔드 테스트 보고서는 `build/reports/tests/test/index.html`, 최종 Gradle 로그는 `build/nhn-addon-validation.log`에 있다.
