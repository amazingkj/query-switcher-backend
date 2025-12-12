import type { Monaco } from '@monaco-editor/react';
import { DialectType } from '../types';

// Oracle 전용 키워드
const oracleKeywords = [
  'VARCHAR2', 'NUMBER', 'CLOB', 'BLOB', 'NVARCHAR2', 'NCLOB',
  'BINARY_FLOAT', 'BINARY_DOUBLE', 'ROWID', 'UROWID',
  'SYSDATE', 'SYSTIMESTAMP', 'NVL', 'NVL2', 'DECODE', 'ROWNUM',
  'CONNECT_BY_ROOT', 'CONNECT_BY_ISLEAF', 'CONNECT_BY_ISCYCLE',
  'START', 'CONNECT', 'PRIOR', 'NOCYCLE', 'SIBLINGS',
  'TABLESPACE', 'PCTFREE', 'PCTUSED', 'INITRANS', 'MAXTRANS',
  'STORAGE', 'INITIAL', 'NEXT', 'MINEXTENTS', 'MAXEXTENTS',
  'LOGGING', 'NOLOGGING', 'COMPRESS', 'NOCOMPRESS',
  'PARALLEL', 'NOPARALLEL', 'CACHE', 'NOCACHE',
  'SEGMENT', 'CREATION', 'IMMEDIATE', 'DEFERRED',
  'FLASHBACK', 'ARCHIVE', 'ROW', 'MOVEMENT',
  'ENABLE', 'DISABLE', 'VALIDATE', 'NOVALIDATE',
  'FORCE', 'NOFORCE', 'SECUREFILE', 'BASICFILE',
  'DBMS_OUTPUT', 'DBMS_RANDOM', 'UTL_FILE',
  'BULK', 'COLLECT', 'FORALL', 'RETURNING',
  'PLS_INTEGER', 'BINARY_INTEGER', 'SIMPLE_INTEGER',
  'EXCEPTION', 'RAISE', 'PRAGMA', 'AUTONOMOUS_TRANSACTION'
];

// MySQL 전용 키워드
const mysqlKeywords = [
  'TINYINT', 'SMALLINT', 'MEDIUMINT', 'BIGINT', 'INT',
  'FLOAT', 'DOUBLE', 'DECIMAL', 'NUMERIC',
  'TINYTEXT', 'MEDIUMTEXT', 'LONGTEXT', 'TEXT',
  'TINYBLOB', 'MEDIUMBLOB', 'LONGBLOB',
  'DATETIME', 'TIMESTAMP', 'YEAR',
  'ENUM', 'SET', 'JSON',
  'AUTO_INCREMENT', 'UNSIGNED', 'ZEROFILL',
  'NOW', 'CURDATE', 'CURTIME', 'UNIX_TIMESTAMP', 'FROM_UNIXTIME',
  'IFNULL', 'NULLIF', 'COALESCE', 'IF',
  'GROUP_CONCAT', 'CONCAT_WS',
  'LOCATE', 'INSTR', 'SUBSTRING', 'SUBSTR',
  'RAND', 'TRUNCATE', 'LAST_INSERT_ID',
  'DATE_FORMAT', 'STR_TO_DATE', 'DATE_ADD', 'DATE_SUB',
  'ENGINE', 'CHARSET', 'COLLATE', 'ROW_FORMAT',
  'DELIMITER', 'DEFINER', 'INVOKER',
  'SIGNAL', 'RESIGNAL', 'HANDLER', 'CONDITION',
  'ITERATE', 'LEAVE', 'LOOP', 'REPEAT', 'WHILE'
];

// PostgreSQL 전용 키워드
const postgresqlKeywords = [
  'SERIAL', 'BIGSERIAL', 'SMALLSERIAL',
  'INTEGER', 'SMALLINT', 'BIGINT', 'REAL', 'DOUBLE',
  'TEXT', 'BYTEA', 'UUID', 'JSONB', 'JSON',
  'ARRAY', 'HSTORE', 'INET', 'CIDR', 'MACADDR',
  'BOOLEAN', 'INTERVAL', 'MONEY',
  'CURRENT_TIMESTAMP', 'CURRENT_DATE', 'CURRENT_TIME',
  'COALESCE', 'NULLIF', 'GREATEST', 'LEAST',
  'STRING_AGG', 'ARRAY_AGG', 'JSON_AGG', 'JSONB_AGG',
  'RANDOM', 'TRUNC', 'CEIL', 'FLOOR',
  'TO_CHAR', 'TO_DATE', 'TO_TIMESTAMP', 'TO_NUMBER',
  'POSITION', 'OVERLAY', 'SIMILAR',
  'RETURNING', 'CONFLICT', 'NOTHING', 'EXCLUDED',
  'LATERAL', 'WITHIN', 'FILTER', 'OVER', 'PARTITION',
  'RAISE', 'NOTICE', 'EXCEPTION', 'DEBUG', 'INFO', 'WARNING',
  'PERFORM', 'EXECUTE', 'USING', 'STRICT',
  'LANGUAGE', 'PLPGSQL', 'RETURNS', 'SETOF',
  'VOLATILE', 'STABLE', 'IMMUTABLE', 'SECURITY',
  'LISTEN', 'NOTIFY', 'UNLISTEN',
  'VACUUM', 'ANALYZE', 'REINDEX', 'CLUSTER'
];

// 공통 SQL 키워드 (ANSI SQL)
const commonKeywords = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'EXISTS',
  'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
  'CREATE', 'ALTER', 'DROP', 'TRUNCATE',
  'TABLE', 'INDEX', 'VIEW', 'SEQUENCE', 'TRIGGER', 'PROCEDURE', 'FUNCTION',
  'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'UNIQUE', 'CHECK', 'DEFAULT',
  'NULL', 'NOT', 'CONSTRAINT', 'CASCADE', 'RESTRICT',
  'JOIN', 'INNER', 'LEFT', 'RIGHT', 'FULL', 'OUTER', 'CROSS', 'NATURAL',
  'ON', 'USING', 'GROUP', 'BY', 'HAVING', 'ORDER', 'ASC', 'DESC', 'NULLS', 'FIRST', 'LAST',
  'LIMIT', 'OFFSET', 'FETCH', 'NEXT', 'ROWS', 'ONLY', 'PERCENT',
  'UNION', 'INTERSECT', 'EXCEPT', 'ALL', 'DISTINCT',
  'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
  'AS', 'ALIAS', 'WITH', 'RECURSIVE',
  'BEGIN', 'COMMIT', 'ROLLBACK', 'SAVEPOINT', 'TRANSACTION',
  'GRANT', 'REVOKE', 'DENY',
  'TRUE', 'FALSE', 'UNKNOWN',
  'LIKE', 'ILIKE', 'BETWEEN', 'IS', 'ANY', 'SOME',
  'COUNT', 'SUM', 'AVG', 'MIN', 'MAX',
  'UPPER', 'LOWER', 'TRIM', 'LTRIM', 'RTRIM', 'LENGTH', 'REPLACE',
  'CAST', 'CONVERT', 'EXTRACT'
];

// 데이터타입 키워드
const dataTypeKeywords = [
  'INT', 'INTEGER', 'SMALLINT', 'BIGINT', 'TINYINT', 'MEDIUMINT',
  'FLOAT', 'DOUBLE', 'REAL', 'DECIMAL', 'NUMERIC', 'NUMBER',
  'CHAR', 'VARCHAR', 'VARCHAR2', 'NCHAR', 'NVARCHAR', 'NVARCHAR2',
  'TEXT', 'TINYTEXT', 'MEDIUMTEXT', 'LONGTEXT', 'CLOB', 'NCLOB',
  'BLOB', 'TINYBLOB', 'MEDIUMBLOB', 'LONGBLOB', 'BYTEA', 'BINARY', 'VARBINARY',
  'DATE', 'TIME', 'DATETIME', 'TIMESTAMP', 'YEAR', 'INTERVAL',
  'BOOLEAN', 'BOOL', 'BIT',
  'JSON', 'JSONB', 'XML',
  'UUID', 'UNIQUEIDENTIFIER',
  'SERIAL', 'BIGSERIAL', 'SMALLSERIAL',
  'MONEY', 'SMALLMONEY',
  'ENUM', 'SET', 'ARRAY'
];

/**
 * 방언에 따른 키워드 목록 반환
 */
export const getKeywordsForDialect = (dialect: DialectType): string[] => {
  const keywords = [...commonKeywords];

  switch (dialect) {
    case DialectType.ORACLE:
      keywords.push(...oracleKeywords);
      break;
    case DialectType.MYSQL:
      keywords.push(...mysqlKeywords);
      break;
    case DialectType.POSTGRESQL:
      keywords.push(...postgresqlKeywords);
      break;
  }

  return [...new Set(keywords)]; // 중복 제거
};

/**
 * Monaco Editor에 커스텀 SQL 언어 설정 등록
 */
export const registerSqlLanguage = (monaco: Monaco, dialect: DialectType): void => {
  const keywords = getKeywordsForDialect(dialect);
  const dataTypes = dataTypeKeywords;

  // SQL 언어의 토큰 프로바이더 설정
  monaco.languages.setMonarchTokensProvider('sql', {
    defaultToken: '',
    tokenPostfix: '.sql',
    ignoreCase: true,

    keywords,
    dataTypes,
    operators: [
      '=', '>', '<', '!', '~', '?', ':', '==', '<=', '>=', '!=',
      '<>', '&&', '||', '++', '--', '+', '-', '*', '/', '&', '|',
      '^', '%', '<<', '>>', '>>>'
    ],
    builtinFunctions: [
      'COUNT', 'SUM', 'AVG', 'MIN', 'MAX',
      'COALESCE', 'NULLIF', 'NVL', 'IFNULL', 'IF',
      'UPPER', 'LOWER', 'TRIM', 'LTRIM', 'RTRIM', 'LENGTH', 'SUBSTRING', 'REPLACE', 'CONCAT',
      'NOW', 'SYSDATE', 'CURRENT_TIMESTAMP', 'CURRENT_DATE', 'CURRENT_TIME',
      'DATE_FORMAT', 'TO_CHAR', 'TO_DATE', 'TO_NUMBER',
      'CAST', 'CONVERT', 'EXTRACT',
      'ROUND', 'FLOOR', 'CEIL', 'ABS', 'MOD', 'POWER', 'SQRT',
      'DECODE', 'CASE', 'NVL2'
    ],

    brackets: [
      { open: '[', close: ']', token: 'delimiter.square' },
      { open: '(', close: ')', token: 'delimiter.parenthesis' }
    ],

    tokenizer: {
      root: [
        // 주석
        { include: '@comments' },
        // 공백
        { include: '@whitespace' },
        // 괄호
        [/[{}()\[\]]/, '@brackets'],
        // 숫자
        [/\d*\.\d+([eE][-+]?\d+)?/, 'number.float'],
        [/\d+/, 'number'],
        // 문자열 (싱글쿼트)
        [/'/, { token: 'string.quote', bracket: '@open', next: '@string' }],
        // 문자열 (더블쿼트 - 식별자)
        [/"/, { token: 'identifier.quote', bracket: '@open', next: '@quotedIdentifier' }],
        // 백틱 식별자 (MySQL)
        [/`/, { token: 'identifier.quote', bracket: '@open', next: '@backtickIdentifier' }],
        // 연산자
        [/[;,.]/, 'delimiter'],
        [/[<>=!~?:&|+\-*\/^%]+/, 'operator'],
        // 식별자 및 키워드
        [/[a-zA-Z_]\w*/, {
          cases: {
            '@keywords': 'keyword',
            '@dataTypes': 'type',
            '@builtinFunctions': 'predefined',
            '@default': 'identifier'
          }
        }],
        // Oracle 바인드 변수
        [/:[a-zA-Z_]\w*/, 'variable'],
        // PostgreSQL 타입 캐스팅
        [/::/, 'operator'],
      ],

      comments: [
        [/--+.*/, 'comment'],
        [/\/\*/, { token: 'comment.quote', next: '@comment' }]
      ],

      comment: [
        [/[^/*]+/, 'comment'],
        [/\*\//, { token: 'comment.quote', next: '@pop' }],
        [/./, 'comment']
      ],

      whitespace: [
        [/\s+/, 'white']
      ],

      string: [
        [/[^']+/, 'string'],
        [/''/, 'string.escape'],
        [/'/, { token: 'string.quote', bracket: '@close', next: '@pop' }]
      ],

      quotedIdentifier: [
        [/[^"]+/, 'identifier'],
        [/""/, 'identifier.escape'],
        [/"/, { token: 'identifier.quote', bracket: '@close', next: '@pop' }]
      ],

      backtickIdentifier: [
        [/[^`]+/, 'identifier'],
        [/``/, 'identifier.escape'],
        [/`/, { token: 'identifier.quote', bracket: '@close', next: '@pop' }]
      ]
    }
  });

  // 언어 설정
  monaco.languages.setLanguageConfiguration('sql', {
    comments: {
      lineComment: '--',
      blockComment: ['/*', '*/']
    },
    brackets: [
      ['[', ']'],
      ['(', ')']
    ],
    autoClosingPairs: [
      { open: '[', close: ']' },
      { open: '(', close: ')' },
      { open: "'", close: "'", notIn: ['string'] },
      { open: '"', close: '"', notIn: ['string'] },
      { open: '`', close: '`', notIn: ['string'] }
    ],
    surroundingPairs: [
      { open: '[', close: ']' },
      { open: '(', close: ')' },
      { open: "'", close: "'" },
      { open: '"', close: '"' },
      { open: '`', close: '`' }
    ],
    folding: {
      markers: {
        start: /^\s*--\s*#?region\b/,
        end: /^\s*--\s*#?endregion\b/
      }
    }
  });
};

/**
 * 방언에 따른 자동완성 제안 항목 생성
 */
export const getSuggestionsForDialect = (dialect: DialectType): Array<{
  label: string;
  kind: number; // monaco.languages.CompletionItemKind
  insertText: string;
  detail: string;
}> => {
  const suggestions: Array<{
    label: string;
    kind: number;
    insertText: string;
    detail: string;
  }> = [];

  // 기본 SQL 스니펫
  suggestions.push(
    { label: 'SELECT', kind: 14, insertText: 'SELECT ${1:*} FROM ${2:table_name}', detail: 'SELECT 문' },
    { label: 'INSERT', kind: 14, insertText: 'INSERT INTO ${1:table_name} (${2:columns}) VALUES (${3:values})', detail: 'INSERT 문' },
    { label: 'UPDATE', kind: 14, insertText: 'UPDATE ${1:table_name} SET ${2:column} = ${3:value} WHERE ${4:condition}', detail: 'UPDATE 문' },
    { label: 'DELETE', kind: 14, insertText: 'DELETE FROM ${1:table_name} WHERE ${2:condition}', detail: 'DELETE 문' },
    { label: 'CREATE TABLE', kind: 14, insertText: 'CREATE TABLE ${1:table_name} (\n  ${2:column_name} ${3:data_type}\n)', detail: 'CREATE TABLE 문' }
  );

  // 방언별 스니펫
  switch (dialect) {
    case DialectType.ORACLE:
      suggestions.push(
        { label: 'NVL', kind: 1, insertText: 'NVL(${1:expr}, ${2:default_value})', detail: 'NULL 값 대체 (Oracle)' },
        { label: 'DECODE', kind: 1, insertText: 'DECODE(${1:expr}, ${2:search}, ${3:result})', detail: '조건 비교 (Oracle)' },
        { label: 'ROWNUM', kind: 14, insertText: 'ROWNUM', detail: '행 번호 (Oracle)' }
      );
      break;
    case DialectType.MYSQL:
      suggestions.push(
        { label: 'IFNULL', kind: 1, insertText: 'IFNULL(${1:expr}, ${2:default_value})', detail: 'NULL 값 대체 (MySQL)' },
        { label: 'GROUP_CONCAT', kind: 1, insertText: 'GROUP_CONCAT(${1:column} SEPARATOR ${2:\', \'})', detail: '문자열 집계 (MySQL)' },
        { label: 'LIMIT', kind: 14, insertText: 'LIMIT ${1:count} OFFSET ${2:start}', detail: '결과 제한 (MySQL)' }
      );
      break;
    case DialectType.POSTGRESQL:
      suggestions.push(
        { label: 'COALESCE', kind: 1, insertText: 'COALESCE(${1:expr1}, ${2:expr2})', detail: 'NULL 값 대체 (PostgreSQL)' },
        { label: 'STRING_AGG', kind: 1, insertText: 'STRING_AGG(${1:column}, ${2:\', \'})', detail: '문자열 집계 (PostgreSQL)' },
        { label: 'RETURNING', kind: 14, insertText: 'RETURNING ${1:*}', detail: '결과 반환 (PostgreSQL)' }
      );
      break;
  }

  return suggestions;
};