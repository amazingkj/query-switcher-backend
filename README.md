# SQL Switcher (SQL2SQL) 🔄

데이터베이스 간 SQL 쿼리를 쉽게 변환하는 웹 애플리케이션

🌐 **Live Demo**: [https://sql2sql.kr](https://sql2sql.kr)

📦 **GitHub**: [https://github.com/amazingkj/query-switcher-backend](https://github.com/amazingkj/query-switcher-backend)

## 🌟 주요 기능

- **다중 데이터베이스 지원**: Oracle, MySQL, PostgreSQL 간 SQL 변환
- **실시간 변환**: 자동 변환 모드로 입력과 동시에 결과 확인
- **스마트 경고**: 변환 시 주의사항과 호환성 경고 제공
- **구문 하이라이팅**: 데이터베이스별 맞춤 SQL 구문 강조
- **SQL 차이 비교**: 원본과 변환 결과 비교 뷰어
- **SQL 검증/실행**: 변환된 SQL 문법 검증 및 테스트 실행
- **변환 히스토리**: 이전 변환 기록 저장 및 재사용
- **SQL 스니펫**: 자주 사용하는 SQL 예제 제공
- **다크 모드**: 눈의 피로를 줄이는 다크 테마 지원
- **분석 대시보드**: 사용 통계 및 패턴 분석

## 🚀 Docker로 빠르게 시작하기

### 사전 요구사항
- Docker 및 Docker Compose 설치
- (선택) Docker Hub 계정

### 로컬 실행

```bash
# 1. 프로젝트 클론
git clone https://github.com/amazingkj/query-switcher-backend.git
cd query-switcher-backend

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
- **Testing**: JUnit 5, Parameterized Tests
- **Caching**: Caffeine

### Frontend
- **Language**: TypeScript
- **Framework**: React 18
- **Build Tool**: Vite
- **UI Library**: Tailwind CSS
- **Editor**: Monaco Editor (구문 하이라이팅 포함)
- **State Management**: Zustand
- **API Client**: TanStack Query

### DevOps
- **Container**: Docker
- **Orchestration**: Docker Compose
- **Web Server**: Nginx
- **CI/CD**: GitHub Actions (선택사항)

## 📁 프로젝트 구조

```
sql-switcher/
├── backend/
│   ├── src/
│   │   ├── main/kotlin/com/sqlswitcher/
│   │   │   ├── api/               # REST Controllers
│   │   │   ├── converter/         # SQL 변환 엔진
│   │   │   │   └── feature/       # 기능별 변환 서비스
│   │   │   │       ├── procedure/ # 프로시저 변환
│   │   │   │       ├── view/      # 뷰 변환
│   │   │   │       └── function/  # 함수 변환
│   │   │   ├── parser/            # SQL 파싱 서비스
│   │   │   │   └── error/         # 에러 처리
│   │   │   ├── model/             # 데이터 모델
│   │   │   └── service/           # 비즈니스 로직
│   │   └── test/kotlin/           # 단위 테스트
│   ├── Dockerfile
│   └── build.gradle.kts
├── frontend/
│   ├── src/
│   │   ├── components/            # React 컴포넌트
│   │   ├── hooks/                 # Custom Hooks
│   │   ├── stores/                # 상태 관리
│   │   ├── types/                 # TypeScript 타입
│   │   └── utils/                 # 유틸리티 함수
│   │       └── sqlLanguageConfig.ts # 구문 하이라이팅 설정
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── docs/
│   └── SQL_CONVERSION_RULES.md    # 변환 규칙 문서
├── docker-compose.yml             # 개발용
├── docker-compose.prod.yml        # 프로덕션용
└── README.md
```

## 🔄 지원되는 SQL 변환

### 데이터 타입 변환

| Oracle | MySQL | PostgreSQL |
|--------|-------|------------|
| `VARCHAR2(n BYTE/CHAR)` | `VARCHAR(n)` | `VARCHAR(n)` |
| `NUMBER(p)` | `TINYINT/SMALLINT/INT/BIGINT` | `SMALLINT/INTEGER/BIGINT` |
| `NUMBER(p,s)` | `DECIMAL(p,s)` | `NUMERIC(p,s)` |
| `CLOB` | `LONGTEXT` | `TEXT` |
| `BLOB` | `LONGBLOB` | `BYTEA` |
| `DATE` | `DATETIME` | `TIMESTAMP` |
| `FLOAT(p)` | `FLOAT/DOUBLE` | `REAL/DOUBLE PRECISION` |

| MySQL | PostgreSQL |
|-------|------------|
| `TINYINT(1)` | `BOOLEAN` |
| `INT AUTO_INCREMENT` | `SERIAL` |
| `BIGINT AUTO_INCREMENT` | `BIGSERIAL` |
| `JSON` | `JSONB` |
| `ENUM(...)` | `VARCHAR(255)` |

### 함수 변환

| Oracle | MySQL | PostgreSQL |
|--------|-------|------------|
| `SYSDATE` | `NOW()` | `CURRENT_TIMESTAMP` |
| `NVL(a, b)` | `IFNULL(a, b)` | `COALESCE(a, b)` |
| `SUBSTR(s, p, l)` | `SUBSTRING(s, p, l)` | `SUBSTRING(s, p, l)` |
| `NVL2(a, b, c)` | `IF(a, b, c)` | `CASE WHEN a THEN b ELSE c END` |
| `DECODE(...)` | `CASE ...` | `CASE ...` |

| MySQL | PostgreSQL |
|-------|------------|
| `NOW()` | `CURRENT_TIMESTAMP` |
| `CURDATE()` | `CURRENT_DATE` |
| `RAND()` | `RANDOM()` |
| `GROUP_CONCAT(...)` | `STRING_AGG(...)` |
| `TRUNCATE(n, d)` | `TRUNC(n, d)` |
| `LAST_INSERT_ID()` | `LASTVAL()` |

### Oracle DDL 옵션 자동 제거

MySQL/PostgreSQL 변환 시 다음 Oracle 전용 옵션들이 자동으로 제거됩니다:

- `TABLESPACE`, `PCTFREE`, `PCTUSED`, `INITRANS`, `MAXTRANS`
- `STORAGE (INITIAL, NEXT, MINEXTENTS, MAXEXTENTS, ...)`
- `LOGGING/NOLOGGING`, `COMPRESS/NOCOMPRESS`, `CACHE/NOCACHE`
- `PARALLEL/NOPARALLEL`, `MONITORING/NOMONITORING`
- `SEGMENT CREATION IMMEDIATE/DEFERRED`
- `ENABLE/DISABLE ROW MOVEMENT`
- `FLASHBACK ARCHIVE`, `SECUREFILE/BASICFILE`
- 스키마 접두사 (`"SCHEMA_OWNER"."TABLE_NAME"` → `"TABLE_NAME"`)

### 수동 변환 필요 항목

다음 항목들은 데이터베이스별 문법 차이가 크므로 경고 메시지와 함께 수동 검토가 필요합니다:

- **파티션 테이블**: 데이터베이스별 파티션 문법 상이
- **시퀀스**: Oracle 시퀀스 → MySQL AUTO_INCREMENT / PostgreSQL SERIAL
- **트리거**: 트리거 문법이 완전히 다름
- **계층 쿼리**: Oracle `CONNECT BY` → `WITH RECURSIVE` CTE
- **패키지 함수**: `DBMS_OUTPUT`, `DBMS_RANDOM`, `UTL_FILE` 등

자세한 변환 규칙은 `docs/SQL_CONVERSION_RULES.md` 문서를 참고하세요.

## 🧪 테스트

```bash
# Backend 테스트
cd backend
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "com.sqlswitcher.converter.*Test"
```

테스트 커버리지:
- 데이터 타입 변환 테스트 (`DataTypeConversionTest`)
- 함수 변환 테스트 (`FunctionConversionTest`)
- Oracle DDL 옵션 제거 테스트 (`OracleDDLOptionsTest`)
- 통합 테스트 (`SqlConverterEngineIntegrationTest`)

## 🤝 기여하기

기여를 환영합니다! Pull Request를 보내주세요.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request


## 📞 문의

프로젝트 관련 문의사항이 있으시면 이슈를 등록해주세요.

---

Made with ❤️ by SQL2SQL 