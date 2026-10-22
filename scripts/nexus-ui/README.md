# Nexus Browse UI 수정 (3.97.0-13)

52번 서버에서 확인한 증상: 로그인 후 권한 로딩이 끝나지 않고 Browse가 404로 이동했다.
서버 내부 권한 API는 정상이지만 외부에서 쿼리 없는 `POST /service/extdirect`는 시간 초과되고,
같은 요청에 쿼리 문자열을 추가하면 정상 응답했다. 어느 네트워크 장비가 이 차이를 만드는지는 확인되지 않았다.

`extdirect-transport.js`는 원래 ExtDirect Remoting provider URL에 페이지마다 쿼리 값을 추가한다.
기존 권한 조회, 배치, 콜백, 인증 세션, CSRF 처리와 polling 주소는 유지한다.
Velocity 템플릿의 정적 리소스 버전도 변경하여 브라우저가 수정 코드를 다시 받도록 한다.
AM Java/Vue 코드의 변경은 없으며, Nexus 파생 이미지에 적용되는 수정이다.

## 빌드

Linux/WSL에서 Docker와 Python 3를 사용한다.

```sh
bash scripts/nexus-ui/build.sh
node scripts/nexus-ui/test-extdirect-transport.cjs
docker compose up -d --no-deps --pull never sonatype-nexus
```

원본 Nexus 이미지는 digest로 고정하며 패치 도구가 JAR 해시를 검사한다.
지원하지 않는 버전에는 적용하지 않는다. `build/`의 JAR은 Git에 포함하지 않는다.
현재 Compose Nexus 서비스는 `mcmp/nexus-ui-fixed:3.97.0-20261021.3` 이미지를 사용한다.
다른 서버에 배포할 때 먼저 이미지를 빌드하거나 전달해야 한다.

## 52번 서버 검증

- 브라우저 로그인 → Browse → docker-test 진입
- 테스트 이미지의 Docker Registry V2 API 업로드 및 manifest/layer 다운로드 무결성 검증
- Browse에 테스트 이미지가 표시되는지 확인 후 해당 테스트 컴포넌트 정리

2026-10-22에 이 디렉터리의 스크립트로 52번 서버에서 이미지를 다시 빌드했다.
생성된 JAR과 실행 중인 Nexus JAR의 SHA-256은 모두
`981e9774c9cc074587982fd5d29b8611d16e177c577e9be850b7851a51ab3c3e`로 일치했다.
ExtDirect transport 회귀 검사와 Nexus 컨테이너 상태 검사도 통과했다.

52번 서버의 Compose 서비스 이름은 `mc-application-manager-sonatype-nexus`이다.
원본 이미지, Compose 파일, 정지 상태의 데이터 백업은 서버의
`conf/docker/nexus-ui-fix-20261021`에 보존한다. `compose.rollback.yaml`은 원본 이미지를 지정한다.

```sh
docker compose -p mcc -f docker-compose.yaml -f nexus-ui-fix-20261021/compose.rollback.yaml up -d --no-deps --pull never mc-application-manager-sonatype-nexus
```

이 롤백은 컨테이너 이미지만 되돌리고 기존 Nexus 데이터 볼륨은 유지한다.
