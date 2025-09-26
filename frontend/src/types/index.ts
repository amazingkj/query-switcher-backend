// 데이터베이스 방언 타입
export enum DialectType {
  MYSQL = 'MYSQL',
  POSTGRESQL = 'POSTGRESQL',
  ORACLE = 'ORACLE',
  TIBERO = 'TIBERO'
}

// 경고 타입
export enum WarningType {
  SYNTAX_DIFFERENCE = 'SYNTAX_DIFFERENCE',
  UNSUPPORTED_FUNCTION = 'UNSUPPORTED_FUNCTION',
  PARTIAL_SUPPORT = 'PARTIAL_SUPPORT',
  MANUAL_REVIEW_NEEDED = 'MANUAL_REVIEW_NEEDED',
  PERFORMANCE_WARNING = 'PERFORMANCE_WARNING'
}

// 경고 심각도
export enum WarningSeverity {
  INFO = 'INFO',
  WARNING = 'WARNING',
  ERROR = 'ERROR'
}

// 경고 정보
export interface ConversionWarning {
  type: WarningType;
  message: string;
  severity: WarningSeverity;
  suggestion?: string;
}

// 변환 요청
export interface ConversionRequest {
  sql: string;
  sourceDialect: DialectType;
  targetDialect: DialectType;
  options?: ConversionOptions;
}

// 변환 옵션
export interface ConversionOptions {
  strictMode?: boolean;
  enableComments?: boolean;
  formatSql?: boolean;
  replaceUnsupportedFunctions?: boolean;
}

// 변환 응답
export interface ConversionResponse {
  originalSql: string;
  convertedSql: string;
  sourceDialect: DialectType;
  targetDialect: DialectType;
  warnings: ConversionWarning[];
  appliedRules: string[];
  conversionTime: number;
  success: boolean;
  error?: string;
}

// 에러 응답
export interface ErrorResponse {
  errorCode: string;
  message: string;
  timestamp: string;
  details?: string;
}

// 변환 히스토리 아이템
export interface ConversionHistoryItem {
  id: string;
  originalSql: string;
  convertedSql: string;
  sourceDialect: DialectType;
  targetDialect: DialectType;
  warnings: ConversionWarning[];
  appliedRules: string[];
  timestamp: Date;
  conversionTime: number;
  success: boolean;
  error?: string;
}
