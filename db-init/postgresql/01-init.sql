-- PostgreSQL 테스트용 초기화 스크립트
-- SQL 변환 테스트를 위한 샘플 테이블 및 데이터

-- 사용자 테이블
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    status CHAR(1) DEFAULT 'A',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 부서 테이블
CREATE TABLE departments (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    manager_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 직원 테이블
CREATE TABLE employees (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    dept_id INT,
    hire_date DATE,
    salary NUMERIC(10, 2),
    commission NUMERIC(10, 2),
    manager_id INT,
    FOREIGN KEY (dept_id) REFERENCES departments(id)
);

-- 주문 테이블
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INT,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(12, 2),
    status VARCHAR(20) DEFAULT 'pending',
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 샘플 데이터 삽입
INSERT INTO departments (name, description) VALUES
('영업부', '영업 및 마케팅'),
('개발부', '소프트웨어 개발'),
('인사부', '인사 관리'),
('재무부', '재무 및 회계');

INSERT INTO users (username, email, status) VALUES
('john_doe', 'john@example.com', 'A'),
('jane_smith', 'jane@example.com', 'A'),
('bob_wilson', 'bob@example.com', 'I'),
('alice_brown', NULL, 'A');

INSERT INTO employees (first_name, last_name, email, dept_id, hire_date, salary, commission, manager_id) VALUES
('김', '철수', 'kim@example.com', 1, '2020-01-15', 5000000, 500000, NULL),
('이', '영희', 'lee@example.com', 1, '2021-03-20', 4500000, 300000, 1),
('박', '민수', 'park@example.com', 2, '2019-07-01', 6000000, NULL, NULL),
('최', '지은', 'choi@example.com', 2, '2022-02-28', 4000000, NULL, 3),
('정', '현우', 'jung@example.com', 3, '2018-11-10', 5500000, NULL, NULL);

INSERT INTO orders (user_id, total_amount, status) VALUES
(1, 150000, 'completed'),
(1, 280000, 'pending'),
(2, 95000, 'completed'),
(3, 320000, 'cancelled');

-- 테스트용 뷰
CREATE VIEW v_employee_summary AS
SELECT
    e.id,
    e.first_name || ' ' || e.last_name AS full_name,
    d.name AS department,
    e.salary,
    COALESCE(e.commission, 0) AS commission,
    e.salary + COALESCE(e.commission, 0) AS total_compensation
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id;

-- updated_at 자동 업데이트 함수 및 트리거
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 테스트 완료 메시지
DO $$
BEGIN
    RAISE NOTICE 'PostgreSQL 초기화 완료!';
END $$;