# MC-WEB-CONSOLE Install SW 연동 요청서

## 1. 요청 목적

MC-WEB-CONSOLE의 Workload 화면에서 `Install SW`를 누르면 팝업을 열고, 팝업 안에 MC-APPLICATION-MANAGER(AM)의 설치 화면을 iframe으로 표시해 주세요.

```text
Workload → Install SW
→ Web Console popup
→ AM 설치 iframe
→ 배포 결과 수신
→ Workload 또는 Apps Status 갱신
```

팝업은 Web Console이 생성하고, AM은 설치 전용 URL과 기존 배포 기능을 제공합니다.

## 2. 적용 대상

### Infra Workload

Web Console에서 다음 값을 확인할 수 있어야 합니다.

- MCI/Infra ID
- VM/Node ID 또는 NodeGroup ID

AM URL:

```text
{AM_BASE_URL}/web/softwareCatalog/install?targetType=VM&mciId={MCI_ID}&vmId={VM_ID}&requestId={REQUEST_ID}
```

NodeGroup 전체에 각각 독립 설치할 때:

```text
{AM_BASE_URL}/web/softwareCatalog/install?targetType=VM&mciId={MCI_ID}&nodeGroupId={NODE_GROUP_ID}&requestId={REQUEST_ID}
```

VM과 NodeGroup을 모두 알고 있으면 두 값을 함께 전달할 수 있습니다. 이 경우에는 VM 한 대가 배포 대상이며, AM이 해당 VM의 NodeGroup 소속을 검증합니다.

```text
{AM_BASE_URL}/web/softwareCatalog/install?targetType=VM&mciId={MCI_ID}&nodeGroupId={NODE_GROUP_ID}&vmId={VM_ID}&requestId={REQUEST_ID}
```

### K8s Workload

Web Console에서 Cluster ID를 확인할 수 있어야 합니다.

AM URL:

```text
{AM_BASE_URL}/web/softwareCatalog/install?targetType=K8S&clusterId={CLUSTER_ID}&requestId={REQUEST_ID}
```

K8s NodeGroup은 이번 연동 범위에서 제외합니다. K8s 애플리케이션 배포 대상은 Cluster입니다.

## 3. iframe에 Project 정보 전달

AM iframe이 `IFRAME_READY` 이벤트를 보내면 현재 Web Console 상단에서 선택한 Workspace/Project 정보를 기존 메시지 형식으로 전달해 주세요.

```javascript
function sendProjectContext() {
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
}
```

중요 사항:

- `accessToken`, Workspace, Project 전달 형식은 기존 Web Console → AM iframe 연동 형식입니다.
- namespace는 `projectInfo.ns_id`로 전달합니다.
- namespace와 access token을 iframe URL query string에 넣지 않습니다.
- 팝업을 연 상태에서 Project가 변경되면 기존 팝업을 닫고 새 Project 기준으로 다시 열어 주세요.

## 4. AM 결과 이벤트 수신

`MCMP_AM_INSTALL_EVENT` 형식은 이번 Install SW 연동을 위해 새로 추가된 AM → Web Console 이벤트입니다.

```javascript
window.addEventListener('message', (event) => {
  if (event.source !== iframe.contentWindow) return
  if (event.origin !== amOrigin) return
  if (event.data?.type !== 'MCMP_AM_INSTALL_EVENT') return
  if (event.data.requestId !== requestId) return

  switch (event.data.status) {
    case 'IFRAME_READY':
      sendProjectContext()
      break
    case 'FORM_READY':
      hideLoading()
      break
    case 'FORM_ERROR':
    case 'CONFIGURATION_ERROR':
      showError(event.data.message)
      break
    case 'DEPLOY_STARTED':
      showDeploying()
      break
    case 'DEPLOY_SUCCEEDED':
      refreshWorkload()
      closeInstallPopup()
      break
    case 'DEPLOY_FAILED':
      showError(event.data.message)
      break
    case 'CANCELLED':
      closeInstallPopup()
      break
  }
})
```

이벤트 기본 형식:

```javascript
{
  type: 'MCMP_AM_INSTALL_EVENT',
  version: 1,
  requestId: '요청 생성 시 사용한 ID',
  status: 'IFRAME_READY',
  target: {
    targetType: 'VM',
    mciId: 'mci-01',
    nodeGroupId: 'group-01',
    vmId: 'vm-01'
  }
}
```

`DEPLOY_SUCCEEDED`는 AM 배포 API가 성공했다는 의미입니다. 애플리케이션의 최종 Running/Healthy 여부는 기존 Apps Status에서 확인합니다.

## 5. Web Console 구현 항목

- Workload 화면에 `Install SW` 버튼 추가
- 버튼 클릭 시 popup/modal 생성
- VM 또는 K8s 대상에 맞는 iframe URL 생성
- 메시지 listener를 등록한 후 iframe 로드
- `IFRAME_READY` 수신 시 현재 Project 정보 전달
- 로딩·배포 중·오류 상태 표시
- 성공 시 Workload/Apps Status 갱신 후 팝업 닫기
- 팝업 종료 시 message listener 제거

팝업을 여는 별도 AM REST API는 없습니다. Web Console이 위 설치 URL을 iframe의 `src`로 지정하면 됩니다.

## 6. 완료 확인 기준

- Infra Workload에서 선택한 MCI/VM 또는 MCI/NodeGroup이 AM 화면에 고정됨
- VM과 NodeGroup을 함께 전달하면 VM 소속 관계가 검증됨
- NodeGroup만 전달하면 실행 중인 모든 그룹 VM이 Spec Check 및 Standalone 배포 대상이 됨
- K8s Workload에서 선택한 Cluster가 AM 화면에 고정됨
- 다른 Target 또는 namespace로 화면에서 변경할 수 없음
- Project 정보가 없거나 대상이 Project namespace에 없으면 오류 표시
- VM과 K8s 각각 Spec Check 및 Deploy 실행 가능
- 성공/실패/취소 시 Web Console이 해당 상태를 처리함
- URL과 브라우저 로그에 access token이 노출되지 않음

AM 상세 연동 규약은 `AM_INSTALL_SW_IFRAME_INTEGRATION_GUIDE.md`를 참고해 주세요.
