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

// SQL 검증 요청
export interface ValidationRequest {
  sql: string;
  dialect: DialectType;
}

// SQL 테스트 요청
export interface TestRequest {
  sql: string;
  dialect: DialectType;
  dryRun?: boolean;
}

// 검증 에러 정보
export interface ValidationError {
  message: string;
  line?: number;
  column?: number;
  suggestion?: string;
}

// SQL 검증 응답
export interface ValidationResponse {
  isValid: boolean;
  dialect: DialectType;
  errors: ValidationError[];
  warnings: string[];
  parsedStatementType?: string;
}

// 테스트 에러 정보
export interface TestError {
  code?: string;
  message: string;
  sqlState?: string;
  suggestion?: string;
}

// SQL 테스트 응답
export interface TestResponse {
  success: boolean;
  dialect: DialectType;
  executionTimeMs: number;
  error?: TestError;
  rowsAffected?: number;
  message?: string;
}

// 컨테이너 상태 응답
export interface ContainerStatusResponse {
  containers: Record<DialectType, ContainerInfo>;
}

export interface ContainerInfo {
  running: boolean;
  dialect: DialectType;
}
