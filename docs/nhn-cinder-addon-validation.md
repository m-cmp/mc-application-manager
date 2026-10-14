# NHN Cinder CSI 로컬 검증 결과

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
