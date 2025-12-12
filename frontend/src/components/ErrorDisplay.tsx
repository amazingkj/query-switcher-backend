import React, { useState } from 'react';

/**
 * API 에러 응답 타입
 */
export interface ApiError {
  errorCode: string;
  message: string;
  timestamp: string;
  details?: string;
}

/**
 * 변환 에러 응답 타입 (상세 정보 포함)
 */
export interface ConversionApiError {
  errorCode: string;
  title: string;
  message: string;
  suggestions: string[];
  timestamp: string;
  technicalDetails?: Record<string, unknown>;
}

interface ErrorDisplayProps {
  error: ApiError | ConversionApiError | Error | string | null;
  onDismiss?: () => void;
  showDetails?: boolean;
}

/**
 * 에러 표시 컴포넌트
 * API 에러 또는 일반 에러를 사용자 친화적으로 표시
 */
export const ErrorDisplay: React.FC<ErrorDisplayProps> = ({
  error,
  onDismiss,
  showDetails = false
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  if (!error) return null;

  // 에러 타입에 따라 정보 추출
  const errorInfo = parseError(error);

  return (
    <div className="error-display bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 mb-4">
      {/* 헤더 */}
      <div className="flex items-start justify-between">
        <div className="flex items-start">
          <div className="flex-shrink-0">
            <svg
              className="w-5 h-5 text-red-500 dark:text-red-400"
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                clipRule="evenodd"
              />
            </svg>
          </div>
          <div className="ml-3">
            <h3 className="text-sm font-medium text-red-800 dark:text-red-300">
              {errorInfo.title}
            </h3>
            <p className="mt-1 text-sm text-red-700 dark:text-red-400">
              {errorInfo.message}
            </p>
          </div>
        </div>
        {onDismiss && (
          <button
            onClick={onDismiss}
            className="flex-shrink-0 ml-4 text-red-400 hover:text-red-600 dark:hover:text-red-300 transition-colors"
          >
            <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>

      {/* 제안 사항 */}
      {errorInfo.suggestions.length > 0 && (
        <div className="mt-3 ml-8">
          <h4 className="text-xs font-medium text-red-700 dark:text-red-400 mb-1">
            해결 방법:
          </h4>
          <ul className="text-sm text-red-600 dark:text-red-400 space-y-1">
            {errorInfo.suggestions.map((suggestion, idx) => (
              <li key={idx} className="flex items-start">
                <span className="text-red-400 dark:text-red-500 mr-2">•</span>
                <span>{suggestion}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 상세 정보 토글 */}
      {(showDetails || errorInfo.details) && (
        <div className="mt-3 ml-8">
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="text-xs text-red-500 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 flex items-center"
          >
            <span>{isExpanded ? '상세 정보 숨기기' : '상세 정보 보기'}</span>
            <svg
              className={`w-4 h-4 ml-1 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M19 9l-7 7-7-7"
              />
            </svg>
          </button>
          {isExpanded && errorInfo.details && (
            <div className="mt-2 p-2 bg-red-100 dark:bg-red-900/30 rounded text-xs font-mono text-red-700 dark:text-red-400 overflow-x-auto">
              <pre className="whitespace-pre-wrap">{errorInfo.details}</pre>
            </div>
          )}
        </div>
      )}

      {/* 에러 코드 및 타임스탬프 */}
      {errorInfo.code && (
        <div className="mt-3 ml-8 flex items-center gap-3 text-xs text-red-500 dark:text-red-500">
          <span>코드: {errorInfo.code}</span>
          {errorInfo.timestamp && <span>시간: {formatTimestamp(errorInfo.timestamp)}</span>}
        </div>
      )}
    </div>
  );
};

/**
 * 에러 파싱 헬퍼 함수
 */
function parseError(error: ApiError | ConversionApiError | Error | string): {
  title: string;
  message: string;
  suggestions: string[];
  code?: string;
  timestamp?: string;
  details?: string;
} {
  // 문자열 에러
  if (typeof error === 'string') {
    return {
      title: '오류 발생',
      message: error,
      suggestions: []
    };
  }

  // 일반 Error 객체
  if (error instanceof Error) {
    return {
      title: '오류 발생',
      message: error.message,
      suggestions: [],
      details: error.stack
    };
  }

  // ConversionApiError (상세 정보 포함)
  if ('suggestions' in error && Array.isArray(error.suggestions)) {
    const convError = error as ConversionApiError;
    return {
      title: convError.title,
      message: convError.message,
      suggestions: convError.suggestions,
      code: convError.errorCode,
      timestamp: convError.timestamp,
      details: convError.technicalDetails
        ? JSON.stringify(convError.technicalDetails, null, 2)
        : undefined
    };
  }

  // ApiError (기본)
  const apiError = error as ApiError;
  return {
    title: getErrorTitle(apiError.errorCode),
    message: apiError.message,
    suggestions: getDefaultSuggestions(apiError.errorCode),
    code: apiError.errorCode,
    timestamp: apiError.timestamp,
    details: apiError.details
  };
}

/**
 * 에러 코드에 따른 제목 반환
 */
function getErrorTitle(errorCode: string): string {
  const titles: Record<string, string> = {
    VALIDATION_ERROR: '입력값 검증 오류',
    SQL_PARSE_ERROR: 'SQL 구문 분석 오류',
    INVALID_ARGUMENT: '잘못된 인수',
    NOT_FOUND: '리소스를 찾을 수 없음',
    INTERNAL_SERVER_ERROR: '서버 오류',
    CONV_001: '지원되지 않는 데이터타입',
    CONV_010: '지원되지 않는 함수',
    CONV_020: '지원되지 않는 DDL 옵션',
    CONV_030: '계층적 쿼리 미지원',
    CONV_100: '빈 SQL 입력',
    CONV_101: '잘못된 데이터베이스 방언',
    CONV_102: '동일 방언 변환',
    CONV_999: '변환 오류'
  };
  return titles[errorCode] || '오류 발생';
}

/**
 * 에러 코드에 따른 기본 제안사항 반환
 */
function getDefaultSuggestions(errorCode: string): string[] {
  const suggestions: Record<string, string[]> = {
    VALIDATION_ERROR: [
      '입력값을 확인해주세요',
      '필수 항목이 모두 입력되었는지 확인하세요'
    ],
    SQL_PARSE_ERROR: [
      'SQL 구문을 다시 확인해주세요',
      '괄호나 따옴표가 올바르게 닫혔는지 확인하세요',
      '예약어 철자를 확인해주세요'
    ],
    INVALID_ARGUMENT: [
      '입력값의 형식을 확인해주세요'
    ],
    NOT_FOUND: [
      '요청한 리소스가 존재하는지 확인해주세요',
      'URL이 올바른지 확인하세요'
    ],
    INTERNAL_SERVER_ERROR: [
      '잠시 후 다시 시도해주세요',
      '문제가 지속되면 관리자에게 문의하세요'
    ]
  };
  return suggestions[errorCode] || [];
}

/**
 * 타임스탬프 포맷
 */
function formatTimestamp(timestamp: string): string {
  try {
    const date = new Date(timestamp);
    return date.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  } catch {
    return timestamp;
  }
}

export default ErrorDisplay;