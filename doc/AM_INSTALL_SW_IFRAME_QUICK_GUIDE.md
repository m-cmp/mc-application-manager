# AM Install SW iframe 간단 연동 가이드

## 1. 연동 방식

MC-WEB-CONSOLE이 팝업을 만들고, 팝업 안에 AM 설치 화면을 iframe으로 띄운다.

```text
Workload에서 Install SW 클릭
→ Web Console이 popup 생성
→ AM 설치 URL을 iframe으로 표시
→ Project 정보와 token 전달
→ 사용자가 Catalog와 옵션 선택 후 Deploy
→ AM이 성공/실패 결과 반환
```

AM은 iframe 화면과 기존 배포 기능을 제공한다. 팝업과 `Install SW` 버튼은 MC-WEB-CONSOLE에서 구현해야 한다.

## 2. iframe URL

VM:

```text
https://{AM_HOST}/web/softwareCatalog/install?targetType=VM&mciId={MCI_ID}&vmId={VM_ID}&requestId={REQUEST_ID}
```

VM NodeGroup 전체:

```text
https://{AM_HOST}/web/softwareCatalog/install?targetType=VM&mciId={MCI_ID}&nodeGroupId={NODE_GROUP_ID}&requestId={REQUEST_ID}
```

특정 VM과 소속 NodeGroup을 함께 전달:

```text
https://{AM_HOST}/web/softwareCatalog/install?targetType=VM&mciId={MCI_ID}&nodeGroupId={NODE_GROUP_ID}&vmId={VM_ID}&requestId={REQUEST_ID}
```

`nodeGroupId` 없이 기존 `mciId + vmId`만 전달하는 방식도 그대로 지원한다.

K8s:

```text
https://{AM_HOST}/web/softwareCatalog/install?targetType=K8S&clusterId={CLUSTER_ID}&requestId={REQUEST_ID}
```

- VM은 `mciId`와 `vmId` 또는 `nodeGroupId`가 필요하다.
- K8s는 `clusterId`가 필수다.
- `requestId`는 선택 사항이지만 요청과 결과를 연결하기 위해 사용하는 것을 권장한다.
- K8s 요청에는 `nodeGroupId`를 사용하지 않는다.
- namespace와 access token은 URL에 넣지 않는다.

## 3. Project 정보 전달

iframe이 준비되면 Web Console에서 기존 형식으로 전달한다.

```javascript
iframe.contentWindow.postMessage({
  accessToken,
  workspaceInfo: {
    id: workspaceId,
    name: workspaceName
  },
  projectInfo: {
    id: projectId,
    name: projectName,
    ns_id: tumblebugNamespace
  },
  operationId: requestId
}, amOrigin)
```

AM은 `projectInfo.ns_id`에 해당하는 CB-Tumblebug namespace의 자원만 조회하고, URL로 전달받은 VM, VM NodeGroup 또는 Cluster를 변경할 수 없도록 고정한다.

## 4. AM 결과 수신

```javascript
window.addEventListener('message', (event) => {
  if (event.source !== iframe.contentWindow) return
  if (event.data?.type !== 'MCMP_AM_INSTALL_EVENT') return
  if (event.data.requestId !== requestId) return

  switch (event.data.status) {
    case 'IFRAME_READY':
      sendProjectContext()
      break
    case 'DEPLOY_SUCCEEDED':
      refreshWorkload()
      closePopup()
      break
    case 'DEPLOY_FAILED':
      showError(event.data.message)
      break
    case 'CANCELLED':
      closePopup()
      break
  }
})
```

주요 상태값:

- `IFRAME_READY`: iframe 준비 완료
- `FORM_READY`: 설치 화면 준비 완료
- `FORM_ERROR`: 대상 또는 Catalog 조회 실패
- `DEPLOY_STARTED`: 배포 요청 시작
- `DEPLOY_SUCCEEDED`: 기존 AM 배포 API 성공
- `DEPLOY_FAILED`: 배포 요청 실패
- `CANCELLED`: 사용자가 취소

`DEPLOY_SUCCEEDED` 이후 실제 애플리케이션의 Running/Healthy 상태는 Apps Status에서 확인한다.

## 5. 테스트

AM을 실행하고 테스트 페이지를 연다.

```bash
python3 -m http.server 19090 --directory doc
```

```text
http://localhost:19090/install-sw-iframe-test.html
```

테스트에 필요한 값:

- AM 주소
- 유효한 access token
- Workspace ID, Project ID, Tumblebug namespace
- VM 테스트: MCI ID와 VM ID
- K8s 테스트: Cluster ID

상세 규약과 오류 조건은 `AM_INSTALL_SW_IFRAME_INTEGRATION_GUIDE.md`를 참고한다.
