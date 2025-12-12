-- Oracle 테스트용 초기화 스크립트
-- SQL 변환 테스트를 위한 샘플 테이블 및 데이터

-- 시퀀스 생성
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE departments_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE employees_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE orders_seq START WITH 1 INCREMENT BY 1;

-- 사용자 테이블
CREATE TABLE users (
    id NUMBER PRIMARY KEY,
    username VARCHAR2(50) NOT NULL,
    email VARCHAR2(100),
    status CHAR(1) DEFAULT 'A',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 부서 테이블
CREATE TABLE departments (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    description CLOB,
    manager_id NUMBER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 직원 테이블
CREATE TABLE employees (
    id NUMBER PRIMARY KEY,
    first_name VARCHAR2(50) NOT NULL,
    last_name VARCHAR2(50) NOT NULL,
    email VARCHAR2(100),
    dept_id NUMBER,
    hire_date DATE,
    salary NUMBER(10, 2),
    commission NUMBER(10, 2),
    manager_id NUMBER,
    FOREIGN KEY (dept_id) REFERENCES departments(id)
);

-- 주문 테이블
CREATE TABLE orders (
    id NUMBER PRIMARY KEY,
    user_id NUMBER,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMBER(12, 2),
    status VARCHAR2(20) DEFAULT 'pending',
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 샘플 데이터 삽입
INSERT INTO departments (id, name, description) VALUES (departments_seq.NEXTVAL, '영업부', '영업 및 마케팅');
INSERT INTO departments (id, name, description) VALUES (departments_seq.NEXTVAL, '개발부', '소프트웨어 개발');
INSERT INTO departments (id, name, description) VALUES (departments_seq.NEXTVAL, '인사부', '인사 관리');
INSERT INTO departments (id, name, description) VALUES (departments_seq.NEXTVAL, '재무부', '재무 및 회계');

INSERT INTO users (id, username, email, status) VALUES (users_seq.NEXTVAL, 'john_doe', 'john@example.com', 'A');
INSERT INTO users (id, username, email, status) VALUES (users_seq.NEXTVAL, 'jane_smith', 'jane@example.com', 'A');
INSERT INTO users (id, username, email, status) VALUES (users_seq.NEXTVAL, 'bob_wilson', 'bob@example.com', 'I');
INSERT INTO users (id, username, email, status) VALUES (users_seq.NEXTVAL, 'alice_brown', NULL, 'A');

INSERT INTO employees (id, first_name, last_name, email, dept_id, hire_date, salary, commission, manager_id)
VALUES (employees_seq.NEXTVAL, '김', '철수', 'kim@example.com', 1, TO_DATE('2020-01-15', 'YYYY-MM-DD'), 5000000, 500000, NULL);
INSERT INTO employees (id, first_name, last_name, email, dept_id, hire_date, salary, commission, manager_id)
VALUES (employees_seq.NEXTVAL, '이', '영희', 'lee@example.com', 1, TO_DATE('2021-03-20', 'YYYY-MM-DD'), 4500000, 300000, 1);
INSERT INTO employees (id, first_name, last_name, email, dept_id, hire_date, salary, commission, manager_id)
VALUES (employees_seq.NEXTVAL, '박', '민수', 'park@example.com', 2, TO_DATE('2019-07-01', 'YYYY-MM-DD'), 6000000, NULL, NULL);
INSERT INTO employees (id, first_name, last_name, email, dept_id, hire_date, salary, commission, manager_id)
VALUES (employees_seq.NEXTVAL, '최', '지은', 'choi@example.com', 2, TO_DATE('2022-02-28', 'YYYY-MM-DD'), 4000000, NULL, 3);
INSERT INTO employees (id, first_name, last_name, email, dept_id, hire_date, salary, commission, manager_id)
VALUES (employees_seq.NEXTVAL, '정', '현우', 'jung@example.com', 3, TO_DATE('2018-11-10', 'YYYY-MM-DD'), 5500000, NULL, NULL);

INSERT INTO orders (id, user_id, total_amount, status) VALUES (orders_seq.NEXTVAL, 1, 150000, 'completed');
INSERT INTO orders (id, user_id, total_amount, status) VALUES (orders_seq.NEXTVAL, 1, 280000, 'pending');
INSERT INTO orders (id, user_id, total_amount, status) VALUES (orders_seq.NEXTVAL, 2, 95000, 'completed');
INSERT INTO orders (id, user_id, total_amount, status) VALUES (orders_seq.NEXTVAL, 3, 320000, 'cancelled');

COMMIT;

-- 테스트용 뷰
CREATE VIEW v_employee_summary AS
SELECT
    e.id,
    e.first_name || ' ' || e.last_name AS full_name,
    d.name AS department,
    e.salary,
    NVL(e.commission, 0) AS commission,
    e.salary + NVL(e.commission, 0) AS total_compensation
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id;

-- updated_at 자동 업데이트 트리거
CREATE OR REPLACE TRIGGER users_updated_at_trg
BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/