import { format } from 'sql-formatter';
import { DialectType } from '../types';

// SQL 포맷터 옵션
interface FormatOptions {
  language?: 'sql' | 'mysql' | 'postgresql' | 'plsql' | 'sqlite';
  tabWidth?: number;
  useTabs?: boolean;
  keywordCase?: 'upper' | 'lower' | 'preserve';
  functionCase?: 'upper' | 'lower' | 'preserve';
  dataTypeCase?: 'upper' | 'lower' | 'preserve';
  identifierCase?: 'upper' | 'lower' | 'preserve';
  linesBetweenQueries?: number;
}

// 데이터베이스 방언을 SQL 포맷터 언어로 매핑
const dialectToLanguage = (dialect: DialectType): FormatOptions['language'] => {
  switch (dialect) {
    case DialectType.MYSQL:
      return 'mysql';
    case DialectType.POSTGRESQL:
      return 'postgresql';
    case DialectType.ORACLE:
      return 'plsql'; // Oracle uses PL/SQL
    default:
      return 'sql';
  }
};

// SQL 포맷팅 함수
export const formatSql = (
  sql: string, 
  dialect: DialectType = DialectType.MYSQL,
  options: Partial<FormatOptions> = {}
): string => {
  try {
    const formatOptions: FormatOptions = {
      language: dialectToLanguage(dialect),
      tabWidth: 2,
      useTabs: false,
      keywordCase: 'upper',
      functionCase: 'upper',
      dataTypeCase: 'upper',
      identifierCase: 'preserve',
      linesBetweenQueries: 2,
      ...options
    };

    return format(sql, formatOptions);
  } catch (error) {
    console.error('SQL formatting error:', error);
    // 포맷팅 실패 시 원본 SQL 반환
    return sql;
  }
};

// SQL 검증 함수
export const validateSql = (sql: string): { isValid: boolean; error?: string } => {
  if (!sql.trim()) {
    return { isValid: false, error: 'SQL이 비어있습니다.' };
  }

  // 기본적인 SQL 키워드 검증
  const sqlKeywords = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER', 'WITH'];
  const upperSql = sql.toUpperCase();
  const hasKeyword = sqlKeywords.some(keyword => upperSql.includes(keyword));

  if (!hasKeyword) {
    return { isValid: false, error: '유효한 SQL 키워드를 찾을 수 없습니다.' };
  }

  return { isValid: true };
};

// SQL 길이 검증
export const validateSqlLength = (sql: string, maxLength: number = 50000): { isValid: boolean; error?: string } => {
  if (sql.length > maxLength) {
    return { 
      isValid: false, 
      error: `SQL이 너무 깁니다. 최대 ${maxLength.toLocaleString()}자까지 허용됩니다.` 
    };
  }
  return { isValid: true };
};

// SQL 압축 함수 (공백 제거)
export const minifySql = (sql: string): string => {
  return sql
    .replace(/\s+/g, ' ') // 여러 공백을 하나로
    .replace(/\s*([,;()])\s*/g, '$1') // 연산자 주변 공백 제거
    .trim();
};
