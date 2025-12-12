@echo off
REM =====================================================
REM SQL Converter 재배포 스크립트 (Windows)
REM Docker Hub에서 최신 이미지를 가져와 재배포합니다.
REM =====================================================

setlocal EnableDelayedExpansion

set COMPOSE_FILE=docker-compose.prod.yml
set BACKEND_IMAGE=jiin724/sql-converter-backend:latest
set FRONTEND_IMAGE=jiin724/sql-converter-frontend:latest

echo ======================================
echo   SQL Converter 재배포 시작
echo ======================================
echo.

REM docker compose 명령어 확인
docker compose version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set DC_CMD=docker compose -f %COMPOSE_FILE%
) else (
    docker-compose version >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set DC_CMD=docker-compose -f %COMPOSE_FILE%
    ) else (
        echo [ERROR] docker compose(또는 docker-compose)를 찾을 수 없습니다.
        exit /b 1
    )
)

REM 1. 기존 컨테이너 중지/삭제
echo [INFO] 기존 컨테이너 중지/삭제 중...
%DC_CMD% down
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] 컨테이너 중지 중 오류 발생 (무시하고 계속)
)
echo [OK] 컨테이너 중지/삭제 완료
echo.

REM 2. 로컬 이미지 제거
echo [INFO] 캐시된 로컬 이미지 제거 중...

docker image inspect %BACKEND_IMAGE% >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo   - Backend 이미지 제거 중...
    docker rmi -f %BACKEND_IMAGE%
)

docker image inspect %FRONTEND_IMAGE% >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo   - Frontend 이미지 제거 중...
    docker rmi -f %FRONTEND_IMAGE%
)

echo [OK] 로컬 이미지 제거 완료
echo.

REM 3. 최신 이미지 Pull
echo [INFO] Docker Hub에서 최신 이미지 가져오는 중...
%DC_CMD% pull
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 이미지 Pull 실패
    exit /b 1
)
echo [OK] 최신 이미지 Pull 완료
echo.

REM 4. 컨테이너 기동
echo [INFO] 컨테이너 기동 중...
%DC_CMD% up -d
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] 컨테이너 기동 실패
    exit /b 1
)
echo [OK] 컨테이너 기동 완료
echo.

REM 5. 컨테이너 상태 확인
echo [INFO] 컨테이너 상태 확인 중...
timeout /t 3 /nobreak >nul
%DC_CMD% ps
echo.

REM 6. 간단한 연결 테스트
echo [INFO] 서비스 연결 테스트 중...
echo.

curl -s -f http://localhost:8080 >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Backend (8080) 연결 성공
) else (
    echo [WARN] Backend (8080) 연결 실패 - 컨테이너 로그 확인 필요
)

curl -s -f http://localhost:80 >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Frontend (80) 연결 성공
) else (
    echo [WARN] Frontend (80) 연결 실패 - 컨테이너 로그 확인 필요
)

echo.
echo ======================================
echo   재배포 완료! 🚀
echo ======================================
echo.
echo [INFO] 접속 URL:
echo   - Frontend: http://localhost:80
echo   - Backend:  http://localhost:8080
echo.
echo [INFO] 로그 확인: %DC_CMD% logs -f
echo [INFO] 상태 확인: %DC_CMD% ps
echo.

endlocal