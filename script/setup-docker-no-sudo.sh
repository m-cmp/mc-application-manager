#!/bin/bash

# Docker 그룹 생성 (이미 존재하는 경우 무시)
sudo groupadd docker

# 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER

# Docker 데몬 재시작
sudo systemctl restart docker

# Docker 소켓 파일의 권한 변경
sudo chmod 666 /var/run/docker.sock

# 새 그룹 멤버십 적용
newgrp docker << EOF

# Docker 버전 확인
docker -v

# 실행 중인 컨테이너 확인
docker ps

echo "Docker 설정이 완료되었습니다. 이제 sudo 없이 Docker를 사용할 수 있습니다."
echo "주의: Docker 소켓 파일의 권한을 666으로 변경했습니다. 이는 보안상 권장되지 않을 수 있습니다."
echo "시스템 전체에 영구적으로 적용하려면 로그아웃 후 다시 로그인하는 것이 좋습니다."

# 사용자가 현재 셸을 계속 사용할 수 있도록 함
$SHELL
EOF