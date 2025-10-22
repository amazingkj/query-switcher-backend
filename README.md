# SQL Query Switcher 🔄

데이터베이스 간 SQL 쿼리를 쉽게 변환하는 웹 애플리케이션

## 🌟 주요 기능

- **다중 데이터베이스 지원**: MySQL, PostgreSQL, Oracle, Tibero 간 SQL 변환
- **실시간 변환**: 자동 변환 모드로 입력과 동시에 결과 확인
- **스마트 경고**: 변환 시 주의사항과 호환성 경고 제공
- **변환 히스토리**: 이전 변환 기록 저장 및 재사용
- **SQL 스니펫**: 자주 사용하는 SQL 예제 제공
- **분석 대시보드**: 사용 통계 및 패턴 분석

## 🚀 Docker로 빠르게 시작하기

### 사전 요구사항
- Docker 및 Docker Compose 설치
- (선택) Docker Hub 계정

### 로컬 실행

```bash
# 1. 프로젝트 클론
git clone https://github.com/yourusername/sql-converter.git
cd sql-converter

# 2. Docker Compose로 실행
docker-compose up -d

# 3. 브라우저에서 접속
# Frontend: http://localhost
# Backend API: http://localhost:8080
```

### Docker Hub에서 실행

```bash
# docker-compose.prod.yml 파일에서 'yourusername'을 실제 Docker Hub 사용자명으로 변경 후
docker-compose -f docker-compose.prod.yml up -d
```

## 🛠️ 개발 환경 설정

### Backend (Spring Boot + Kotlin)

```bash
cd backend
./gradlew bootRun
```

### Frontend (React + TypeScript + Vite)

```bash
cd frontend
npm install
npm run dev
```

## 📦 Docker 이미지 빌드 및 배포

### Windows
```batch
# docker-build.bat 파일에서 DOCKER_USERNAME 수정 후
docker-build.bat
```

### Linux/Mac
```bash
# docker-build.sh 파일에서 DOCKER_USERNAME 수정 후
chmod +x docker-build.sh
./docker-build.sh
```

## 🏗️ 기술 스택

### Backend
- **Language**: Kotlin
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle
- **SQL Parser**: JSQLParser
- **Testing**: JUnit 5, MockK

### Frontend
- **Language**: TypeScript
- **Framework**: React 18
- **Build Tool**: Vite
- **UI Library**: Tailwind CSS
- **Editor**: Monaco Editor
- **State Management**: Zustand
- **API Client**: TanStack Query

### DevOps
- **Container**: Docker
- **Orchestration**: Docker Compose
- **Web Server**: Nginx
- **CI/CD**: GitHub Actions (선택사항)

## 📁 프로젝트 구조

```
sql-converter/
├── backend/
│   ├── src/
│   │   └── main/kotlin/com/sqlswitcher/
│   │       ├── api/           # REST Controllers
│   │       ├── converter/     # SQL 변환 엔진
│   │       ├── parser/        # SQL 파싱 서비스
│   │       ├── model/         # 데이터 모델
│   │       └── service/       # 비즈니스 로직
│   ├── Dockerfile
│   └── build.gradle.kts
├── frontend/
│   ├── src/
│   │   ├── components/       # React 컴포넌트
│   │   ├── hooks/           # Custom Hooks
│   │   ├── stores/          # 상태 관리
│   │   ├── types/           # TypeScript 타입
│   │   └── utils/           # 유틸리티 함수
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── docker-compose.yml        # 개발용
├── docker-compose.prod.yml   # 프로덕션용
└── README.md
```

## 🔄 지원되는 SQL 변환

### 함수 변환
- Oracle `SYSDATE` → MySQL `NOW()`, PostgreSQL `CURRENT_TIMESTAMP`
- Oracle `NVL()` → MySQL `IFNULL()`, PostgreSQL `COALESCE()`
- Oracle `LISTAGG()` → MySQL `GROUP_CONCAT()`, PostgreSQL `STRING_AGG()`
- 그 외 다수

### 데이터 타입 변환
- Oracle `NUMBER` → MySQL `DECIMAL`, PostgreSQL `NUMERIC`
- Oracle `VARCHAR2` → MySQL/PostgreSQL `VARCHAR`
- Oracle `CLOB` → MySQL `LONGTEXT`, PostgreSQL `TEXT`
- 그 외 다수

### 구문 변환
- 인용 문자 변환 (Oracle `"` → MySQL `` ` ``, PostgreSQL 제거)
- LIMIT/OFFSET 구문 변환
- 조인 구문 최적화

## 🤝 기여하기

기여를 환영합니다! Pull Request를 보내주세요.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 있습니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 📞 문의

프로젝트 관련 문의사항이 있으시면 이슈를 등록해주세요.

---

Made with ❤️ by SQL Converter Team