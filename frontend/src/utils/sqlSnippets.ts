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
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO]
  },
  {
    label: 'INSERT 기본',
    description: '기본 INSERT 쿼리',
    sql: 'INSERT INTO table_name (column1, column2) VALUES (value1, value2);',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO]
  },
  {
    label: 'UPDATE 기본',
    description: '기본 UPDATE 쿼리',
    sql: 'UPDATE table_name SET column1 = value1 WHERE condition;',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO]
  },
  {
    label: 'DELETE 기본',
    description: '기본 DELETE 쿼리',
    sql: 'DELETE FROM table_name WHERE condition;',
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO]
  },
  {
    label: 'JOIN 쿼리',
    description: 'INNER JOIN 예제',
    sql: `SELECT a.*, b.* 
FROM table_a a 
INNER JOIN table_b b ON a.id = b.table_a_id 
WHERE a.status = 'active';`,
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO]
  },
  {
    label: '서브쿼리',
    description: '서브쿼리 예제',
    sql: `SELECT * 
FROM table_name 
WHERE id IN (
    SELECT id FROM other_table WHERE condition
);`,
    dialect: [DialectType.MYSQL, DialectType.POSTGRESQL, DialectType.ORACLE, DialectType.TIBERO]
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
    dialect: [DialectType.ORACLE, DialectType.TIBERO]
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
    dialect: [DialectType.ORACLE, DialectType.TIBERO]
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
