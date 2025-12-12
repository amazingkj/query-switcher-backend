#!/bin/bash

#######################################################
# SQL Converter 재배포 스크립트
# Docker Hub에서 최신 이미지를 가져와 재배포합니다.
#######################################################

set -e

# 설정
COMPOSE_FILE="docker-compose.prod.yml"
COMPOSE_DIR="/home/ec2-user/backend"
BACKEND_IMAGE="jiin724/sql-converter-backend:latest"
FRONTEND_IMAGE="jiin724/sql-converter-frontend:latest"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# docker compose 명령어 래퍼
dc() {
  if command -v docker compose &>/dev/null; then
    docker compose -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose &>/dev/null; then
    docker-compose -f "$COMPOSE_FILE" "$@"
  else
    echo -e "${RED}❌ docker compose(또는 docker-compose)를 찾을 수 없습니다.${NC}" >&2
    exit 1
  fi
}

# 로깅 함수
info()  { echo -e "${BLUE}▶${NC} $*"; }
ok()    { echo -e "${GREEN}✅${NC} $*"; }
warn()  { echo -e "${YELLOW}⚠${NC}  $*"; }
error() { echo -e "${RED}❌${NC} $*"; }

# 시작 메시지
echo "======================================"
echo "  SQL Converter 재배포 시작"
echo "======================================"
echo ""

# 1) 프로젝트 디렉토리로 이동
info "프로젝트 디렉토리로 이동: ${COMPOSE_DIR}"
if [ ! -d "$COMPOSE_DIR" ]; then
  error "디렉토리를 찾을 수 없습니다: ${COMPOSE_DIR}"
  exit 1
fi
cd "$COMPOSE_DIR"

if [ ! -f "$COMPOSE_FILE" ]; then
  error "Docker Compose 파일을 찾을 수 없습니다: ${COMPOSE_FILE}"
  exit 1
fi
ok "디렉토리 이동 완료"
echo ""

# 2) 기존 컨테이너 중지 및 삭제
info "기존 컨테이너 중지/삭제 (docker compose down)"
dc down || true
ok "컨테이너 중지/삭제 완료"
echo ""

# 3) 로컬 이미지 제거
info "캐시된 로컬 이미지 제거 중..."

remove_local_image() {
  local IMAGE_NAME="$1"
  if docker image inspect "$IMAGE_NAME" &>/dev/null; then
    local IMG_ID
    IMG_ID="$(docker images -q "$IMAGE_NAME" | head -n1)"
    if [[ -n "${IMG_ID}" ]]; then
      info "  - 이미지 제거: ${IMAGE_NAME} (${IMG_ID})"
      docker rmi -f "$IMG_ID" || true
    fi
  else
    warn "  - 로컬에 ${IMAGE_NAME} 이미지가 없습니다. (건너뜀)"
  fi
}

remove_local_image "$BACKEND_IMAGE"
remove_local_image "$FRONTEND_IMAGE"
ok "로컬 이미지 제거 완료"
echo ""

# 4) 최신 이미지 Pull
info "Docker Hub에서 최신 이미지 가져오는 중..."
dc pull
ok "최신 이미지 Pull 완료"
echo ""

# 5) 컨테이너 기동
info "컨테이너 기동 중 (docker compose up -d)"
dc up -d
ok "컨테이너 기동 완료"
echo ""

# 6) 컨테이너 상태 확인
info "컨테이너 상태 확인 중..."
sleep 3
dc ps
echo ""

# 7) 헬스체크 대기
info "서비스 헬스체크 대기 중 (최대 60초)..."
WAIT_TIME=0
MAX_WAIT=60

while [ $WAIT_TIME -lt $MAX_WAIT ]; do
  BACKEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' sql-converter-backend 2>/dev/null || echo "starting")
  FRONTEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' sql-converter-frontend 2>/dev/null || echo "starting")

  if [ "$BACKEND_HEALTH" = "healthy" ] && [ "$FRONTEND_HEALTH" = "healthy" ]; then
    ok "모든 서비스가 정상 상태입니다!"
    break
  fi

  echo -n "."
  sleep 2
  WAIT_TIME=$((WAIT_TIME + 2))
done
echo ""

if [ $WAIT_TIME -ge $MAX_WAIT ]; then
  warn "헬스체크 타임아웃. 로그를 확인하세요."
  info "로그 확인: docker compose -f ${COMPOSE_FILE} logs"
fi

# 8) 간단한 연결 테스트
info "서비스 연결 테스트 중..."
echo ""

# Backend 테스트
if curl -s -f http://localhost:8080/health &>/dev/null || curl -s -f http://localhost:8080 &>/dev/null; then
  ok "Backend (8080) 연결 성공"
else
  warn "Backend (8080) 연결 실패 - 컨테이너 로그 확인 필요"
fi

# Frontend 테스트
if curl -s -f http://localhost:80 &>/dev/null; then
  ok "Frontend (80) 연결 성공"
else
  warn "Frontend (80) 연결 실패 - 컨테이너 로그 확인 필요"
fi

echo ""
echo "======================================"
echo "  재배포 완료! 🚀"
echo "======================================"
echo ""
info "접속 URL:"
echo "  - Frontend: http://sql2sql.kr (http://localhost:80)"
echo "  - Backend:  http://localhost:8080"
echo ""
info "로그 확인: docker compose -f ${COMPOSE_FILE} logs -f"
info "상태 확인: docker compose -f ${COMPOSE_FILE} ps"
echo ""
