/**
 * SQL 관련 제한 및 설정 상수
 */
export const SQL_LIMITS = {
  /** 경고 표시 문자 수 (50,000자) */
  WARNING_LENGTH: 50000,
  /** 최대 허용 문자 수 (500,000자) */
  MAX_LENGTH: 500000,
  /** 최대 파일 크기 (5MB) */
  FILE_MAX_SIZE: 5 * 1024 * 1024,
} as const;

/**
 * 변환 요청 기본 옵션
 */
export const DEFAULT_CONVERSION_OPTIONS = {
  strictMode: false,
  enableComments: true,
  formatSql: true,
  replaceUnsupportedFunctions: false,
} as const;