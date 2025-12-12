# SQL Switcher 변환 규칙 문서

## 개요

SQL Switcher는 Oracle, MySQL, PostgreSQL 간 SQL 변환을 지원합니다.
이 문서는 지원되는 변환 규칙과 제한사항을 정리합니다.

---

## 1. 데이터타입 변환

### 1.1 Oracle → MySQL

| Oracle | MySQL | 비고 |
|--------|-------|------|
| `VARCHAR2(n BYTE)` | `VARCHAR(n)` | BYTE 키워드 제거 |
| `VARCHAR2(n CHAR)` | `VARCHAR(n)` | CHAR 키워드 제거 |
| `NUMBER(1-3)` | `TINYINT` | 정밀도 기반 |
| `NUMBER(4-5)` | `SMALLINT` | |
| `NUMBER(6-9)` | `INT` | |
| `NUMBER(10+)` | `BIGINT` | |
| `NUMBER(p,s)` | `DECIMAL(p,s)` | 소수점 포함 |
| `DATE` | `DATETIME` | Oracle DATE는 시간 포함 |
| `CLOB` | `LONGTEXT` | |
| `BLOB` | `LONGBLOB` | |
| `FLOAT(1-24)` | `FLOAT` | 단정밀도 |
| `FLOAT(25-53)` | `DOUBLE` | 배정밀도 |
| `BINARY_FLOAT` | `FLOAT` | |
| `BINARY_DOUBLE` | `DOUBLE` | |

### 1.2 Oracle → PostgreSQL

| Oracle | PostgreSQL | 비고 |
|--------|------------|------|
| `VARCHAR2(n)` | `VARCHAR(n)` | |
| `NUMBER(1-4)` | `SMALLINT` | |
| `NUMBER(5-9)` | `INTEGER` | |
| `NUMBER(10+)` | `BIGINT` | |
| `NUMBER(p,s)` | `NUMERIC(p,s)` | |
| `DATE` | `TIMESTAMP` | (기본값에서) |
| `CLOB` | `TEXT` | |
| `BLOB` | `BYTEA` | |
| `FLOAT(1-24)` | `REAL` | |
| `FLOAT(25-53)` | `DOUBLE PRECISION` | |
| `BINARY_FLOAT` | `REAL` | |
| `BINARY_DOUBLE` | `DOUBLE PRECISION` | |

### 1.3 MySQL → PostgreSQL

| MySQL | PostgreSQL | 비고 |
|-------|------------|------|
| `TINYINT(1)` | `BOOLEAN` | 불리언으로 해석 |
| `TINYINT` | `SMALLINT` | |
| `MEDIUMINT` | `INTEGER` | |
| `INT UNSIGNED` | `BIGINT` | |
| `BIGINT UNSIGNED` | `NUMERIC(20)` | |
| `INT AUTO_INCREMENT` | `SERIAL` | |
| `BIGINT AUTO_INCREMENT` | `BIGSERIAL` | |
| `LONGTEXT` | `TEXT` | |
| `MEDIUMTEXT` | `TEXT` | |
| `TINYTEXT` | `TEXT` | |
| `LONGBLOB` | `BYTEA` | |
| `MEDIUMBLOB` | `BYTEA` | |
| `TINYBLOB` | `BYTEA` | |
| `BLOB` | `BYTEA` | |
| `VARBINARY(n)` | `BYTEA` | |
| `DATETIME` | `TIMESTAMP` | |
| `YEAR` | `SMALLINT` | |
| `DOUBLE` | `DOUBLE PRECISION` | |
| `FLOAT` | `REAL` | |
| `ENUM(...)` | `VARCHAR(255)` | 열거형 미지원 |
| `SET(...)` | `VARCHAR(255)` | |
| `JSON` | `JSONB` | |

### 1.4 PostgreSQL → MySQL

| PostgreSQL | MySQL | 비고 |
|------------|-------|------|
| `SERIAL` | `INT AUTO_INCREMENT` | |
| `BIGSERIAL` | `BIGINT AUTO_INCREMENT` | |
| `SMALLSERIAL` | `SMALLINT AUTO_INCREMENT` | |
| `TEXT` | `LONGTEXT` | |
| `BYTEA` | `LONGBLOB` | |
| `TIMESTAMP` | `DATETIME` | |
| `TIMESTAMP WITH TIME ZONE` | `DATETIME` | 타임존 정보 손실 |
| `INTERVAL` | `VARCHAR(255)` | |
| `DOUBLE PRECISION` | `DOUBLE` | |
| `REAL` | `FLOAT` | |
| `JSONB` | `JSON` | |
| `UUID` | `CHAR(36)` | |
| `BOOLEAN` | `TINYINT(1)` | |
| `[]` (배열) | (제거됨) | 배열 미지원 |

### 1.5 MySQL → Oracle

| MySQL | Oracle | 비고 |
|-------|--------|------|
| `VARCHAR(n)` | `VARCHAR2(n)` | |
| `TINYINT` | `NUMBER(3)` | |
| `SMALLINT` | `NUMBER(5)` | |
| `MEDIUMINT` | `NUMBER(7)` | |
| `INT` | `NUMBER(10)` | |
| `BIGINT` | `NUMBER(19)` | |
| `DATETIME` | `DATE` | |
| `LONGTEXT` | `CLOB` | |
| `LONGBLOB` | `BLOB` | |
| `TEXT` | `CLOB` | |

### 1.6 PostgreSQL → Oracle

| PostgreSQL | Oracle | 비고 |
|------------|--------|------|
| `VARCHAR(n)` | `VARCHAR2(n)` | |
| `SERIAL` | `NUMBER GENERATED ALWAYS AS IDENTITY` | |
| `BIGSERIAL` | `NUMBER GENERATED ALWAYS AS IDENTITY` | |
| `INTEGER` | `NUMBER(10)` | |
| `SMALLINT` | `NUMBER(5)` | |
| `BIGINT` | `NUMBER(19)` | |
| `TEXT` | `CLOB` | |
| `BYTEA` | `BLOB` | |
| `TIMESTAMP` | `DATE` | |
| `BOOLEAN` | `NUMBER(1)` | |
| `DOUBLE PRECISION` | `BINARY_DOUBLE` | |
| `REAL` | `BINARY_FLOAT` | |

---

## 2. 함수 변환

### 2.1 Oracle → MySQL

| Oracle | MySQL | 비고 |
|--------|-------|------|
| `SYSDATE` | `NOW()` | |
| `NVL(a, b)` | `IFNULL(a, b)` | |
| `SUBSTR(s, p, l)` | `SUBSTRING(s, p, l)` | |
| `NVL2(a, b, c)` | `IF(a, b, c)` | |
| `DECODE(...)` | `CASE ...` | 수동 조정 필요 |

### 2.2 Oracle → PostgreSQL

| Oracle | PostgreSQL | 비고 |
|--------|------------|------|
| `SYSDATE` | `CURRENT_TIMESTAMP` | |
| `NVL(a, b)` | `COALESCE(a, b)` | |

### 2.3 MySQL → PostgreSQL

| MySQL | PostgreSQL | 비고 |
|-------|------------|------|
| `NOW()` | `CURRENT_TIMESTAMP` | |
| `CURDATE()` | `CURRENT_DATE` | |
| `CURTIME()` | `CURRENT_TIME` | |
| `IFNULL(a, b)` | `COALESCE(a, b)` | |
| `IF(cond, a, b)` | `CASE WHEN cond THEN a ELSE b END` | |
| `GROUP_CONCAT(...)` | `STRING_AGG(...)` | 구분자 문법 다름 |
| `RAND()` | `RANDOM()` | |
| `TRUNCATE(n, d)` | `TRUNC(n, d)` | |
| `LAST_INSERT_ID()` | `LASTVAL()` | |
| `UNIX_TIMESTAMP()` | `EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::INTEGER` | |
| `FROM_UNIXTIME(t)` | `TO_TIMESTAMP(t)` | |
| `DATE_FORMAT(d, f)` | `TO_CHAR(d, f)` | 포맷 문자열 다름 |
| `STR_TO_DATE(s, f)` | `TO_DATE(s, f)` | |
| `LOCATE(s, str)` | `POSITION(s IN str)` | |
| `INSTR(str, s)` | `POSITION(s IN str)` | |

### 2.4 PostgreSQL → MySQL

| PostgreSQL | MySQL | 비고 |
|------------|-------|------|
| `CURRENT_TIMESTAMP` | `NOW()` | |
| `CURRENT_DATE` | `CURDATE()` | |
| `CURRENT_TIME` | `CURTIME()` | |
| `COALESCE(a, b)` | `IFNULL(a, b)` | |
| `STRING_AGG(...)` | `GROUP_CONCAT(...)` | |
| `RANDOM()` | `RAND()` | |
| `TRUNC(n, d)` | `TRUNCATE(n, d)` | |
| `TO_CHAR(d, f)` | `DATE_FORMAT(d, f)` | |
| `TO_DATE(s, f)` | `STR_TO_DATE(s, f)` | |
| `TO_TIMESTAMP(t)` | `FROM_UNIXTIME(t)` | |
| `::INTEGER` | (제거됨) | 캐스팅 제거 |
| `::TEXT` | (제거됨) | |
| `::VARCHAR` | (제거됨) | |
| `::NUMERIC` | (제거됨) | |
| `::TIMESTAMP` | (제거됨) | |

### 2.5 MySQL → Oracle

| MySQL | Oracle | 비고 |
|-------|--------|------|
| `NOW()` | `SYSDATE` | |
| `IFNULL(a, b)` | `NVL(a, b)` | |
| `COALESCE(a, b)` | `NVL(a, b)` | |
| `SUBSTRING(s, p, l)` | `SUBSTR(s, p, l)` | |

### 2.6 PostgreSQL → Oracle

| PostgreSQL | Oracle | 비고 |
|------------|--------|------|
| `CURRENT_TIMESTAMP` | `SYSDATE` | |
| `COALESCE(a, b)` | `NVL(a, b)` | |
| `RANDOM()` | `DBMS_RANDOM.VALUE` | |

---

## 3. Oracle DDL 옵션 처리

MySQL/PostgreSQL 변환 시 자동으로 제거되는 Oracle 전용 옵션:

| 옵션 | 설명 |
|------|------|
| `TABLESPACE "name"` | 테이블스페이스 지정 |
| `PCTFREE n` | 빈 공간 비율 |
| `PCTUSED n` | 사용 공간 비율 |
| `INITRANS n` | 초기 트랜잭션 슬롯 |
| `MAXTRANS n` | 최대 트랜잭션 슬롯 |
| `STORAGE (...)` | 저장소 옵션 |
| `LOGGING / NOLOGGING` | 리두 로그 설정 |
| `COMPRESS / NOCOMPRESS` | 압축 설정 |
| `CACHE / NOCACHE` | 캐시 설정 |
| `PARALLEL n / NOPARALLEL` | 병렬 처리 |
| `MONITORING / NOMONITORING` | 모니터링 |
| `ROWDEPENDENCIES / NOROWDEPENDENCIES` | 행 의존성 |
| `SEGMENT CREATION IMMEDIATE/DEFERRED` | 세그먼트 생성 |
| `ENABLE/DISABLE ROW MOVEMENT` | 행 이동 |
| `FLASHBACK ARCHIVE ...` | 플래시백 아카이브 |
| `ENABLE/DISABLE CONSTRAINT` | 제약조건 상태 |
| `SECUREFILE / BASICFILE` | LOB 저장 방식 |
| `LOCAL / GLOBAL` (인덱스) | 파티션 인덱스 유형 |

---

## 4. 특수 구문 처리

### 4.1 스키마 접두사

```sql
-- 변환 전 (Oracle)
"SCHEMA_OWNER"."TABLE_NAME"

-- 변환 후
"TABLE_NAME"
```

### 4.2 COMMENT ON (Oracle → MySQL)

```sql
-- 변환 전 (Oracle)
COMMENT ON COLUMN TB_USER.USER_ID IS '사용자 ID';

-- 변환 후 (MySQL)
-- (제거됨, 경고 메시지 출력)
-- MySQL에서는 ALTER TABLE ... MODIFY COLUMN ... COMMENT '...' 사용
```

### 4.3 DEFAULT 절

```sql
-- 변환 전 (Oracle)
CREATED_AT DATE DEFAULT SYSDATE

-- 변환 후 (MySQL/PostgreSQL)
CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
```

---

## 5. 제한사항 및 수동 검토 필요 항목

### 5.1 파티션 테이블

Oracle 파티션 구문은 감지되지만, DB별 문법 차이로 수동 조정 필요:

```sql
-- Oracle
PARTITION BY RANGE (created_at) (
  PARTITION p1 VALUES LESS THAN (TO_DATE('2024-01-01', 'YYYY-MM-DD')),
  PARTITION p2 VALUES LESS THAN (MAXVALUE)
)

-- MySQL (수동 변환 필요)
PARTITION BY RANGE (YEAR(created_at)) (
  PARTITION p1 VALUES LESS THAN (2024),
  PARTITION p2 VALUES LESS THAN MAXVALUE
)

-- PostgreSQL (수동 변환 필요)
-- 부모 테이블 + 자식 테이블 구조로 변경
```

### 5.2 시퀀스

```sql
-- Oracle
CREATE SEQUENCE seq_user_id START WITH 1 INCREMENT BY 1;

-- MySQL: AUTO_INCREMENT 사용 (테이블 컬럼에 적용)
-- PostgreSQL: SERIAL 또는 CREATE SEQUENCE 사용
```

### 5.3 트리거

트리거 문법은 DB별로 크게 다르므로 수동 변환 필요:

```sql
-- Oracle
CREATE TRIGGER tr_user_log
BEFORE INSERT ON tb_user
FOR EACH ROW
BEGIN
  :NEW.created_at := SYSDATE;
END;

-- MySQL
CREATE TRIGGER tr_user_log
BEFORE INSERT ON tb_user
FOR EACH ROW
SET NEW.created_at = NOW();

-- PostgreSQL
CREATE FUNCTION fn_user_log() RETURNS TRIGGER AS $$
BEGIN
  NEW.created_at := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_user_log
BEFORE INSERT ON tb_user
FOR EACH ROW EXECUTE FUNCTION fn_user_log();
```

### 5.4 계층 쿼리

```sql
-- Oracle (CONNECT BY)
SELECT * FROM employees
START WITH manager_id IS NULL
CONNECT BY PRIOR employee_id = manager_id;

-- PostgreSQL/MySQL (WITH RECURSIVE CTE로 변환 필요)
WITH RECURSIVE emp_hierarchy AS (
  SELECT * FROM employees WHERE manager_id IS NULL
  UNION ALL
  SELECT e.* FROM employees e
  JOIN emp_hierarchy h ON e.manager_id = h.employee_id
)
SELECT * FROM emp_hierarchy;
```

### 5.5 패키지 함수

Oracle 전용 패키지 함수는 대체 필요:

| Oracle | 대체 방법 |
|--------|----------|
| `DBMS_OUTPUT.PUT_LINE()` | MySQL: SELECT, PostgreSQL: RAISE NOTICE |
| `DBMS_RANDOM.VALUE` | MySQL: RAND(), PostgreSQL: RANDOM() |
| `UTL_FILE.*` | 애플리케이션 레벨에서 처리 |

### 5.6 힌트

DB별 힌트 문법이 완전히 다르므로 제거됨:

```sql
-- Oracle
SELECT /*+ INDEX(t idx_name) */ * FROM table t;

-- MySQL
SELECT /*+ USE_INDEX(t idx_name) */ * FROM table t;

-- PostgreSQL: 힌트 미지원 (pg_hint_plan 확장 필요)
```

---

## 6. 변환 품질 체크리스트

변환 후 다음 항목을 확인하세요:

- [ ] 데이터타입이 올바르게 변환되었는가?
- [ ] NULL/NOT NULL 제약조건이 유지되었는가?
- [ ] PRIMARY KEY, FOREIGN KEY가 유지되었는가?
- [ ] DEFAULT 값이 올바르게 변환되었는가?
- [ ] 함수 호출이 올바르게 변환되었는가?
- [ ] 문자열 인용 부호가 적절한가? (`"` vs `` ` ``)
- [ ] 예약어 충돌이 없는가?
- [ ] 인덱스가 올바르게 변환되었는가?
- [ ] 트리거/프로시저가 수동으로 검토되었는가?

---

## 7. 버전 정보

- 문서 버전: 1.0
- 최종 수정: 2024-12-12
- SQL Switcher 버전: 0.0.1-SNAPSHOT