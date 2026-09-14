# MC-WEB-CONSOLE Workload → AM Install SW 연동 가이드

## 1. 목적과 적용 범위

MC-WEB-CONSOLE의 Infra/K8s Workload 화면에서 `Install SW`를 누르면 Console이 팝업을 만들고, 그 안에 MC-APPLICATION-MANAGER(이하 AM)의 설치 전용 화면을 iframe으로 표시하기 위한 연동 규약이다.

이번 작업에서 AM이 제공하는 범위는 다음과 같다.

- 설치 전용 iframe URL 제공
- Console이 지정한 VM 또는 K8s Cluster를 변경할 수 없도록 화면에서 고정
- Web Console이 전달한 Project의 CB-Tumblebug namespace만 사용
- 기존 AM Catalog·Spec Check·VM 배포·K8s Helm 배포 API 재사용
- 배포 준비/시작/성공/실패 상태를 부모 창에 `postMessage`로 반환
- MC-WEB-CONSOLE 없이 확인할 수 있는 로컬 연동 테스트 페이지 제공

이번 범위에서 제외한 항목은 다음과 같다.

- Kubernetes `nodeGroup`을 대상으로 하는 스케줄링
- Kubernetes 내부 namespace 선택 기능
- MC-WEB-CONSOLE의 팝업 및 `Install SW` 버튼 구현
- AM 수신 `postMessage`의 출처(origin/source) 제한
- 자원 ID를 서명하는 별도 연동 토큰

VM 설치 대상은 `MCI + VM` 또는 `MCI + NodeGroup`으로 고정할 수 있다. `MCI + VM + NodeGroup`을 전달하면 VM 한 대를 대상으로 하되 NodeGroup 소속을 검증한다. K8s 설치 대상은 `Cluster`로 고정한다.

## 2. 역할 분담

### AM에서 구현된 부분

1. `/web/softwareCatalog/install` 설치 전용 화면
2. URL 파라미터 형식·중복·누락 검증
3. Project namespace를 기준으로 대상 MCI/VM/Cluster 재조회
4. 지정 대상의 선택 UI 잠금
5. 기존 Spec Check와 배포 API 호출
6. 부모 화면으로 진행 결과 이벤트 전송

### MC-WEB-CONSOLE에서 구현할 부분

1. Infra Workload 또는 K8s Workload 화면에 `Install SW` 버튼 추가
2. 버튼 클릭 시 Console 자체 popup/modal 생성
3. 대상에 맞는 AM iframe URL 구성
4. iframe에 현재 access token, Workspace, Project 컨텍스트 전달
5. AM 결과 이벤트를 받아 진행 상태 표시 및 성공 시 목록 갱신
6. 팝업 닫기 정책 결정

AM이 MC-WEB-CONSOLE의 팝업을 API로 직접 여는 구조가 아니다. **팝업은 Console이 만들고, AM은 팝업 안에 넣을 iframe URL을 제공한다.**

## 3. 기존 AM 배포와 iframe 배포의 차이

### 기존 AM 자체 Deploy

```text
사용자가 AM Catalog 화면에서 Deploy 클릭
→ AM 내부 Bootstrap modal 표시
→ 사용자가 VM/K8s, MCI/VM 또는 Cluster 선택
→ Catalog와 옵션 선택
→ Spec Check
→ 기존 VM 또는 K8s 배포 API 호출
```

### Workload 연동 Deploy

```text
사용자가 MC-WEB-CONSOLE Workload에서 Install SW 클릭
→ Console이 popup과 AM iframe 생성
→ Console이 대상 ID를 iframe URL에 설정
→ Console이 현재 Workspace/Project/token을 postMessage로 전달
→ AM이 Project namespace에서 해당 대상을 다시 조회
→ 대상 선택값을 고정한 설치 화면 표시
→ Catalog와 옵션 선택
→ Spec Check
→ 기존 VM 또는 K8s 배포 API 호출
→ AM이 결과 이벤트를 Console에 반환
```

달라지는 것은 **화면 진입 위치와 배포 대상 선택 방식**이다. 실제 VM 설치 로직, Docker/SSH 방식, K8s Helm 배포 방식은 변경하지 않는다.

## 4. iframe URL 규약

### VM 대상

```text
https://{AM_HOST}/web/softwareCatalog/install
  ?targetType=VM
  &mciId={MCI_ID}
  &vmId={VM_ID}
  &requestId={REQUEST_ID}
```

예시:

```text
https://am.example.com/web/softwareCatalog/install?targetType=VM&mciId=mci-01&vmId=vm-01&requestId=req-20260831-001
```

NodeGroup 전체에 Standalone으로 설치:

```text
https://am.example.com/web/softwareCatalog/install?targetType=VM&mciId=mci-01&nodeGroupId=group-01&requestId=req-20260831-002
```

특정 VM의 NodeGroup 소속도 검증:

```text
https://am.example.com/web/softwareCatalog/install?targetType=VM&mciId=mci-01&nodeGroupId=group-01&vmId=vm-01&requestId=req-20260831-003
```

### K8s 대상

```text
https://{AM_HOST}/web/softwareCatalog/install
  ?targetType=K8S
  &clusterId={CLUSTER_ID}
  &requestId={REQUEST_ID}
```

예시:

```text
https://am.example.com/web/softwareCatalog/install?targetType=K8S&clusterId=cluster-01&requestId=req-20260831-002
```

### 파라미터 규칙

| 파라미터 | VM | K8S | 설명 |
| --- | --- | --- | --- |
| `targetType` | 필수, `VM` | 필수, `K8S` | 대소문자는 정규화됨 |
| `mciId` | 필수 | 금지 | CB-Tumblebug MCI ID 또는 name |
| `vmId` | 조건부 필수 | 금지 | 대상 VM ID 또는 name. VM 대상에서는 `vmId` 또는 `nodeGroupId` 중 하나 이상 필요 |
| `nodeGroupId` | 조건부 필수 | 금지 | CB-Tumblebug VM subGroup ID. `vmId`가 없으면 그룹 전체 Standalone 배포 |
| `clusterId` | 금지 | 필수 | 대상 Cluster ID 또는 name |
| `requestId` | 선택 | 선택 | Console과 AM 이벤트 연결용. 생략 시 AM 생성 |

다음 값은 URL로 전달하면 AM이 오류로 거부한다.

- `namespace`, `namespaceId`: namespace는 반드시 현재 Project 컨텍스트에서 가져온다.
- K8s 요청의 `nodeGroupId`: K8s 애플리케이션은 Cluster를 대상으로 하므로 금지한다.
- 동일한 대상 파라미터의 중복 입력
- 계약에 정의되지 않은 임의의 URL 파라미터
- `/`, `?`, `#`, 제어 문자가 들어간 ID
- 200자를 초과한 ID

access token, 비밀번호, 인증 키는 URL에 넣지 않는다. URL은 브라우저 기록, 프록시 로그, 서버 접근 로그에 남을 수 있기 때문이다.

## 5. Console → AM Project 컨텍스트 전달

iframe이 로드되면 기존 Web Console 연동 형식으로 메시지를 보낸다.

```javascript
const iframe = document.querySelector('#am-install-frame')
const amOrigin = new URL(iframe.src).origin

iframe.contentWindow.postMessage({
  accessToken: currentAccessToken,
  workspaceInfo: {
    id: selectedWorkspace.id,
    name: selectedWorkspace.name
  },
  projectInfo: {
    id: selectedProject.id,
    name: selectedProject.name,
    ns_id: selectedProject.ns_id
  },
  operationId: requestId
}, amOrigin)
```

AM은 다음 네 값이 모두 있어야 설치 폼을 표시한다.

- 유효한 `accessToken`
- `workspaceInfo.id`
- `projectInfo.id`
- `projectInfo.ns_id`

AM API 요청에는 다음 값이 자동으로 붙는다.

```text
Authorization: Bearer {accessToken}
X-MCMP-Workspace-ID: {workspaceInfo.id}
X-MCMP-Project-ID: {projectInfo.id}
X-MCMP-Namespace-ID: {projectInfo.ns_id}
```

백엔드는 IAM을 통해 access token 사용자가 해당 Workspace/Project/namespace에 접근 가능한지 검증하고, 실제 배포 요청의 namespace가 검증된 Project namespace와 같은지도 확인한다.

## 6. AM → Console 결과 이벤트

AM은 부모 창에 아래 형식으로 이벤트를 보낸다.

```javascript
{
  type: 'MCMP_AM_INSTALL_EVENT',
  version: 1,
  requestId: 'req-20260831-001',
  status: 'FORM_READY',
  target: {
    targetType: 'VM',
    mciId: 'mci-01',
    nodeGroupId: 'group-01',
    vmId: 'vm-01'
  }
}
```

지원 상태값:

| 상태 | 의미 | Console 권장 처리 |
| --- | --- | --- |
| `IFRAME_READY` | AM 자바스크립트가 준비됨 | Project 컨텍스트 전송 |
| `CONTEXT_READY` | AM이 token/Workspace/Project/namespace를 받음 | 로딩 상태 유지 |
| `FORM_READY` | Project 대상 조회와 설치 폼 초기화 완료 | 사용자 입력 허용 |
| `FORM_ERROR` | 자원 또는 Catalog 조회 중 폼 초기화 실패 | 오류 표시, 설정/연결 확인 후 재시도 |
| `CONFIGURATION_ERROR` | iframe URL 파라미터 오류 | 오류 표시 후 팝업 닫기 제공 |
| `DEPLOY_STARTED` | 기존 배포 API 요청 시작 | 중복 클릭 방지 및 진행 표시 |
| `DEPLOY_SUCCEEDED` | 배포 API가 성공 결과를 반환 | 성공 표시, Workload/Apps Status 갱신 |
| `DEPLOY_FAILED` | 배포 API 실패 | 메시지 표시, 재시도 허용 |
| `CANCELLED` | 사용자가 Cancel 선택 | 팝업 닫기 |

`DEPLOY_SUCCEEDED`는 기존 AM 배포 API가 성공 결과를 반환했다는 뜻이다. 이후 애플리케이션의 지속적인 Running/Healthy 상태는 기존 Apps Status 화면에서 별도로 확인한다.

부모 화면 수신 예시:

```javascript
window.addEventListener('message', (event) => {
  if (event.source !== iframe.contentWindow) return
  if (!event.data || event.data.type !== 'MCMP_AM_INSTALL_EVENT') return
  if (event.data.requestId !== requestId) return

  switch (event.data.status) {
    case 'IFRAME_READY':
      sendProjectContext()
      break
    case 'DEPLOY_SUCCEEDED':
      refreshWorkload()
      closeInstallPopup()
      break
    case 'DEPLOY_FAILED':
      showDeployError(event.data.message)
      break
    case 'CANCELLED':
      closeInstallPopup()
      break
  }
})
```

AM이 부모에게 보내는 상태 이벤트에는 access token, 비밀번호, Object Storage 키를 넣지 않는다.

## 7. Project 범위와 보안 경계

iframe URL의 `mciId`, `vmId`, `nodeGroupId`, `clusterId`는 화면 진입 대상 정보이며 인증 정보가 아니다. 사용자가 URL을 수정하더라도 AM은 IAM에서 검증한 Project namespace의 자원 목록 안에서만 대상을 다시 찾는다. 다른 Project namespace로 바꾼 배포 요청은 백엔드에서 거부된다.

현재 권한 경계는 Project namespace 단위다. 따라서 같은 Project namespace 안의 자원별로 서로 다른 권한을 부여해야 한다면 추가 정책 또는 서명된 단기 연동 토큰 설계가 필요하다. 이는 이번 범위에 포함하지 않는다.

AM의 기존 수신 메시지에는 `event.origin` 또는 `event.source` 허용 목록 검증을 추가하지 않았다. MC-WEB-CONSOLE 코드를 함께 변경할 수 없는 현재 범위에서 기존 메시지 형식과 동작을 유지하기 위한 결정이다. 대신 백엔드 IAM 검증이 최종 권한 판단을 수행한다. 향후 Web Console과 AM의 운영 origin이 확정되면 두 서비스의 배포 설정을 합의한 뒤 허용 origin을 환경 변수로 관리하는 방식을 별도 적용할 수 있다.

## 8. 테스트 방법

### 8.1 자동 테스트

프론트 빌드는 Node.js 20 이상 사용을 권장한다.

```bash
cd applicationFE
yarn test:install-integration
yarn type-check
yarn build

cd ..
./gradlew test
./gradlew bootJar
```

설치 대상 파서는 기존 VM, VM+NodeGroup 검증, NodeGroup 전체, K8s 정상 입력뿐 아니라 필수값 누락, 혼합 대상, 중복 파라미터, namespace 주입, K8s NodeGroup 오용, 경로 문자 입력을 검증한다.

### 8.2 로컬 iframe 연동 테스트

AM을 `http://localhost:18084`에서 실행한 뒤 별도 터미널에서 테스트 페이지를 제공한다.

```bash
python3 -m http.server 19090 --directory doc
```

브라우저에서 다음 주소를 연다.

```text
http://localhost:19090/install-sw-iframe-test.html
```

입력할 값:

- 실행 중인 AM 주소
- 실제 Workspace ID
- 실제 Project ID
- 그 Project에 매핑된 CB-Tumblebug namespace
- 만료되지 않은 Keycloak access token
- VM 테스트: 해당 namespace의 MCI ID와 VM ID 및 선택적인 NodeGroup ID
- VM NodeGroup 테스트: 해당 namespace의 MCI ID와 실행 중인 VM이 있는 NodeGroup ID
- K8s 테스트: 해당 namespace의 Cluster ID

확인 순서:

1. `iframe 열기`를 눌러 고정 대상 요약이 맞는지 확인한다.
2. Project 컨텍스트를 보내 `IFRAME_READY → CONTEXT_READY → FORM_READY`가 기록되는지 확인한다.
3. Target Infra, MCI/VM, MCI/NodeGroup 또는 Cluster 선택값이 잠겨 있는지 확인한다.
4. Catalog와 배포 옵션을 선택하고 Spec Check를 실행한다.
5. Deploy 후 `DEPLOY_STARTED`와 성공/실패 이벤트가 오는지 확인한다.
6. Apps Status에서 같은 Project namespace의 결과만 보이는지 확인한다.

테스트 HTML은 입력한 토큰을 출력하거나 파일에 저장하지 않는다.

### 8.3 실제 MC-WEB-CONSOLE 통합 테스트

AM 단독으로 설치 전용 화면과 API까지 검증할 수 있지만, 실제 Workload 버튼부터 팝업 닫기까지의 완전한 E2E 테스트는 MC-WEB-CONSOLE 작업 후 가능하다. Console 팀이 2절의 항목을 구현하면 VM 1건과 K8s 1건에 대해 위 이벤트 순서를 확인한다.

## 9. 배포 전 체크리스트

- [ ] AM과 Web Console이 접근 가능한 URL로 iframe이 로드되는가
- [ ] Web Console이 `IFRAME_READY` 수신 후 Project 컨텍스트를 보내는가
- [ ] Project의 `ns_id`가 실제 CB-Tumblebug namespace와 일치하는가
- [ ] VM URL에는 `mciId`와 `vmId` 또는 `nodeGroupId`가 있고 `clusterId`가 없는가
- [ ] `vmId`와 `nodeGroupId`를 함께 전달한 경우 VM 소속 관계가 검증되는가
- [ ] `nodeGroupId`만 전달한 경우 실행 중인 그룹 VM 전체가 Spec Check 대상이 되는가
- [ ] K8s URL에는 `clusterId`만 있고 `mciId`, `vmId`가 없는가
- [ ] URL과 로그에 access token 또는 비밀값이 없는가
- [ ] 다른 Project namespace 요청이 403 또는 접근 불가로 처리되는가
- [ ] K8s 요청에 `nodeGroupId`를 전달하지 않는가
- [ ] 기존 AM Catalog의 Deploy modal도 동일하게 동작하는가

## 10. 관련 파일

- `applicationFE/src/views/softwareCatalog/InstallSoftwareIframe.vue`: 설치 전용 iframe 화면
- `applicationFE/src/views/softwareCatalog/components/applicationInstallationForm.vue`: 기존 modal/iframe 공용 설치 폼
- `applicationFE/src/integration/installTarget.ts`: URL 대상 파라미터 검증
- `applicationFE/src/permission.ts`: 기존 Web Console Project 컨텍스트 수신
- `doc/install-sw-iframe-test.html`: AM 단독 연동 테스트 페이지
