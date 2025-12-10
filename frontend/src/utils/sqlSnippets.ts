import { DialectType } from '../types';

// SQL 스니펫 정의
export interface SqlSnippet {
  label: string;
  description: string;
  sql: string;
  dialect: DialectType[];
}

// 기본 SQL 스니펫들
export const sqlSnippets: SqlSnippet[] = [
  {
    label: 'SELECT 기본',
    description: '기본 SELECT 쿼리',
    sql: 'SELECT * FROM table_name WHERE condition;',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE]
  },
  {
    label: 'INSERT 기본',
    description: '기본 INSERT 쿼리',
    sql: 'INSERT INTO table_name (column1, column2) VALUES (value1, value2);',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE]
  },
  {
    label: 'UPDATE 기본',
    description: '기본 UPDATE 쿼리',
    sql: 'UPDATE table_name SET column1 = value1 WHERE condition;',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE]
  },
  {
    label: 'DELETE 기본',
    description: '기본 DELETE 쿼리',
    sql: 'DELETE FROM table_name WHERE condition;',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE]
  },
  {
    label: 'JOIN 쿼리',
    description: 'INNER JOIN 예제',
    sql: `SELECT a.*, b.*
FROM table_a a
INNER JOIN table_b b ON a.id = b.table_a_id
WHERE a.status = 'active';`,
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE]
  },
  {
    label: '서브쿼리',
    description: '서브쿼리 예제',
    sql: `SELECT *
FROM table_name
WHERE id IN (
    SELECT id FROM other_table WHERE condition
);`,
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE]
  },
  {
    label: 'MySQL LIMIT',
    description: 'MySQL LIMIT 구문',
    sql: 'SELECT * FROM table_name LIMIT 10 OFFSET 20;',
    dialect: [DialectType.MYSQL]
  },
  {
    label: 'PostgreSQL LIMIT',
    description: 'PostgreSQL LIMIT 구문',
    sql: 'SELECT * FROM table_name LIMIT 10 OFFSET 20;',
    dialect: [DialectType.POSTGRESQL]
  },
  {
    label: 'Oracle ROWNUM',
    description: 'Oracle ROWNUM 구문',
    sql: `SELECT * FROM (
    SELECT a.*, ROWNUM rn 
    FROM table_name a 
    WHERE ROWNUM <= 30
) WHERE rn > 20;`,
    dialect: [DialectType.ORACLE]
  },
  {
    label: 'MySQL DATE_FORMAT',
    description: 'MySQL 날짜 포맷팅',
    sql: "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') as date FROM table_name;",
    dialect: [DialectType.MYSQL]
  },
  {
    label: 'PostgreSQL TO_CHAR',
    description: 'PostgreSQL 날짜 포맷팅',
    sql: "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') as date FROM table_name;",
    dialect: [DialectType.POSTGRESQL]
  },
  {
    label: 'Oracle TO_CHAR',
    description: 'Oracle 날짜 포맷팅',
    sql: "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') as date FROM table_name;",
    dialect: [DialectType.ORACLE]
  },
  {
    label: 'Oracle FETCH FIRST',
    description: 'Oracle 12c+ FETCH FIRST 구문',
    sql: `SELECT * FROM employees
WHERE salary > 5000
ORDER BY hire_date DESC
FETCH FIRST 10 ROWS ONLY;`,
    dialect: [DialectType.ORACLE]
  },
  {
    label: 'Oracle FETCH with OFFSET',
    description: 'Oracle OFFSET과 FETCH 함께 사용',
    sql: `SELECT * FROM products
ORDER BY price DESC
OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY;`,
    dialect: [DialectType.ORACLE]
  },
  {
    label: 'MySQL GROUP_CONCAT',
    description: 'MySQL 문자열 집계 함수',
    sql: `SELECT department_id,
       GROUP_CONCAT(employee_name ORDER BY employee_name SEPARATOR ', ') as employees
FROM employees
GROUP BY department_id;`,
    dialect: [DialectType.MYSQL]
  },
  {
    label: 'PostgreSQL STRING_AGG',
    description: 'PostgreSQL 문자열 집계 함수',
    sql: `SELECT department_id,
       STRING_AGG(employee_name, ', ' ORDER BY employee_name) as employees
FROM employees
GROUP BY department_id;`,
    dialect: [DialectType.POSTGRESQL]
  },
  {
    label: 'Oracle LISTAGG',
    description: 'Oracle 문자열 집계 함수',
    sql: `SELECT department_id,
       LISTAGG(employee_name, ', ') WITHIN GROUP (ORDER BY employee_name) as employees
FROM employees
GROUP BY department_id;`,
    dialect: [DialectType.ORACLE]
  },
  {
    label: 'MySQL IFNULL',
    description: 'MySQL NULL 처리',
    sql: `SELECT
    employee_name,
    IFNULL(phone_number, 'N/A') as phone,
    IFNULL(email, 'no-email@company.com') as email
FROM employees;`,
    dialect: [DialectType.MYSQL]
  },
  {
    label: 'PostgreSQL COALESCE',
    description: 'PostgreSQL NULL 처리',
    sql: `SELECT
    employee_name,
    COALESCE(phone_number, 'N/A') as phone,
    COALESCE(email, 'no-email@company.com') as email
FROM employees;`,
    dialect: [DialectType.POSTGRESQL]
  },
  {
    label: 'Oracle NVL',
    description: 'Oracle NULL 처리',
    sql: `SELECT
    employee_name,
    NVL(phone_number, 'N/A') as phone,
    NVL(email, 'no-email@company.com') as email
FROM employees;`,
    dialect: [DialectType.ORACLE]
  },
  {
    label: 'MySQL NOW',
    description: 'MySQL 현재 시간',
    sql: `SELECT
    order_id,
    created_at,
    NOW() as current_time,
    TIMESTAMPDIFF(DAY, created_at, NOW()) as days_since_order
FROM orders;`,
    dialect: [DialectType.MYSQL]
  },
  {
    label: 'PostgreSQL CURRENT_TIMESTAMP',
    description: 'PostgreSQL 현재 시간',
    sql: `SELECT
    order_id,
    created_at,
    CURRENT_TIMESTAMP as current_time,
    EXTRACT(DAY FROM CURRENT_TIMESTAMP - created_at) as days_since_order
FROM orders;`,
    dialect: [DialectType.POSTGRESQL]
  },
  {
    label: 'Oracle SYSDATE',
    description: 'Oracle 현재 시간',
    sql: `SELECT
    order_id,
    created_at,
    SYSDATE as current_time,
    TRUNC(SYSDATE - created_at) as days_since_order
FROM orders;`,
    dialect: [DialectType.ORACLE]
  },
  {
    label: '복잡한 SELECT (MySQL)',
    description: '여러 함수와 구문을 포함한 복합 쿼리',
    sql: `SELECT
    e.employee_name,
    e.department_id,
    DATE_FORMAT(e.hire_date, '%Y-%m-%d') as hire_date,
    IFNULL(e.salary, 0) as salary,
    NOW() as query_time
FROM employees e
WHERE e.status = 'active'
    AND e.salary > 3000
ORDER BY e.hire_date DESC
LIMIT 20;`,
    dialect: [DialectType.MYSQL]
  },
  {
    label: '복잡한 SELECT (Oracle)',
    description: '여러 함수와 구문을 포함한 복합 쿼리',
    sql: `SELECT
    e.employee_name,
    e.department_id,
    TO_CHAR(e.hire_date, 'YYYY-MM-DD') as hire_date,
    NVL(e.salary, 0) as salary,
    SYSDATE as query_time
FROM employees e
WHERE e.status = 'active'
    AND e.salary > 3000
ORDER BY e.hire_date DESC
FETCH FIRST 20 ROWS ONLY;`,
    dialect: [DialectType.ORACLE]
  }
];

// 방언별 스니펫 필터링
export const getSnippetsByDialect = (dialect: DialectType): SqlSnippet[] => {
  return sqlSnippets.filter(snippet => 
    snippet.dialect.includes(dialect) || snippet.dialect.length === 0
  );
};

// 스니펫 검색
export const searchSnippets = (query: string, dialect?: DialectType): SqlSnippet[] => {
  const snippets = dialect ? getSnippetsByDialect(dialect) : sqlSnippets;
  const lowerQuery = query.toLowerCase();
  
  return snippets.filter(snippet => 
    snippet.label.toLowerCase().includes(lowerQuery) ||
    snippet.description.toLowerCase().includes(lowerQuery) ||
    snippet.sql.toLowerCase().includes(lowerQuery)
  );
};
