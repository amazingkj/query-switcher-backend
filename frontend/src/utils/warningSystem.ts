import {type ConversionWarning, WarningType, WarningSeverity, DialectType } from '../types';

// 경고 메시지 템플릿
export const WARNING_MESSAGES = {
  [WarningType.SYNTAX_DIFFERENCE]: {
    title: '문법 차이',
    description: '데이터베이스 간 문법이 다릅니다.',
    icon: '⚠️',
    color: 'yellow'
  },
  [WarningType.UNSUPPORTED_FUNCTION]: {
    title: '지원하지 않는 함수',
    description: '해당 데이터베이스에서 지원하지 않는 함수입니다.',
    icon: '❌',
    color: 'red'
  },
  [WarningType.UNSUPPORTED_STATEMENT]: {
    title: '지원하지 않는 구문',
    description: '해당 데이터베이스에서 지원하지 않는 SQL 구문입니다.',
    icon: '🚫',
    color: 'red'
  },
  [WarningType.PARTIAL_SUPPORT]: {
    title: '부분 지원',
    description: '제한적인 지원을 제공합니다.',
    icon: '⚠️',
    color: 'orange'
  },
  [WarningType.MANUAL_REVIEW_NEEDED]: {
    title: '수동 검토 필요',
    description: '변환 결과를 수동으로 검토해주세요.',
    icon: '👁️',
    color: 'blue'
  },
  [WarningType.PERFORMANCE_WARNING]: {
    title: '성능 경고',
    description: '성능에 영향을 줄 수 있습니다.',
    icon: '🐌',
    color: 'purple'
  },
  [WarningType.DATA_TYPE_MISMATCH]: {
    title: '데이터타입 불일치',
    description: '데이터타입 변환 시 정밀도나 범위가 달라질 수 있습니다.',
    icon: '🔄',
    color: 'orange'
  }
};

// 경고 해결 방법 가이드 타입
type SolutionsByDialect = Partial<Record<DialectType, Partial<Record<DialectType, string[]>>>>;
type WarningSolutionEntry = SolutionsByDialect & { general?: string[] };

// 경고 해결 방법 가이드
export const WARNING_SOLUTIONS: Partial<Record<WarningType, WarningSolutionEntry>> = {
  [WarningType.SYNTAX_DIFFERENCE]: {
    [DialectType.MYSQL]: {
      [DialectType.POSTGRESQL]: [
        'LIMIT → LIMIT OFFSET 구문으로 변경',
        'DATE_FORMAT → TO_CHAR 함수로 변경',
        'IFNULL → COALESCE 함수로 변경'
      ],
      [DialectType.ORACLE]: [
        'LIMIT → ROWNUM 또는 FETCH FIRST 구문으로 변경',
        'DATE_FORMAT → TO_CHAR 함수로 변경',
        'IFNULL → NVL 함수로 변경'
      ]
    },
    [DialectType.POSTGRESQL]: {
      [DialectType.MYSQL]: [
        'TO_CHAR → DATE_FORMAT 함수로 변경',
        'COALESCE → IFNULL 함수로 변경',
        'ILIKE → LIKE 함수로 변경'
      ],
      [DialectType.ORACLE]: [
        'TO_CHAR → TO_CHAR 함수 유지',
        'COALESCE → NVL 함수로 변경',
        'ILIKE → LIKE 함수로 변경'
      ]
    }
  },
  [WarningType.UNSUPPORTED_FUNCTION]: {
    general: [
      '대체 함수를 사용하세요',
      '애플리케이션 레벨에서 처리하세요',
      '사용자 정의 함수를 생성하세요'
    ]
  }
};

// 경고 심각도별 색상
export const SEVERITY_COLORS = {
  [WarningSeverity.INFO]: {
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    border: 'border-blue-200 dark:border-blue-800',
    text: 'text-blue-800 dark:text-blue-300',
    icon: 'text-blue-500 dark:text-blue-400'
  },
  [WarningSeverity.WARNING]: {
    bg: 'bg-yellow-50 dark:bg-yellow-900/20',
    border: 'border-yellow-200 dark:border-yellow-800',
    text: 'text-yellow-800 dark:text-yellow-300',
    icon: 'text-yellow-500 dark:text-yellow-400'
  },
  [WarningSeverity.ERROR]: {
    bg: 'bg-red-50 dark:bg-red-900/20',
    border: 'border-red-200 dark:border-red-800',
    text: 'text-red-800 dark:text-red-300',
    icon: 'text-red-500 dark:text-red-400'
  }
};

// 경고 분석 및 분류
export const analyzeWarnings = (warnings: ConversionWarning[]) => {
  const analysis = {
    total: warnings.length,
    bySeverity: {
      [WarningSeverity.INFO]: 0,
      [WarningSeverity.WARNING]: 0,
      [WarningSeverity.ERROR]: 0
    },
    byType: {} as Record<WarningType, number>,
    critical: [] as ConversionWarning[],
    suggestions: [] as string[]
  };

  warnings.forEach(warning => {
    // 심각도별 카운트
    analysis.bySeverity[warning.severity]++;
    
    // 타입별 카운트
    analysis.byType[warning.type] = (analysis.byType[warning.type] || 0) + 1;
    
    // 중요 경고 분류
    if (warning.severity === WarningSeverity.ERROR) {
      analysis.critical.push(warning);
    }
    
    // 제안사항 수집
    if (warning.suggestion) {
      analysis.suggestions.push(warning.suggestion);
    }
  });

  return analysis;
};

// 경고 우선순위 계산
export const calculateWarningPriority = (warning: ConversionWarning): number => {
  let priority = 0;
  
  // 심각도별 점수
  switch (warning.severity) {
    case WarningSeverity.ERROR:
      priority += 100;
      break;
    case WarningSeverity.WARNING:
      priority += 50;
      break;
    case WarningSeverity.INFO:
      priority += 10;
      break;
  }
  
  // 타입별 점수
  switch (warning.type) {
    case WarningType.UNSUPPORTED_STATEMENT:
      priority += 35;
      break;
    case WarningType.UNSUPPORTED_FUNCTION:
      priority += 30;
      break;
    case WarningType.DATA_TYPE_MISMATCH:
      priority += 25;
      break;
    case WarningType.MANUAL_REVIEW_NEEDED:
      priority += 20;
      break;
    case WarningType.PERFORMANCE_WARNING:
      priority += 15;
      break;
    case WarningType.SYNTAX_DIFFERENCE:
      priority += 10;
      break;
    case WarningType.PARTIAL_SUPPORT:
      priority += 5;
      break;
  }
  
  return priority;
};

// 경고 정렬
export const sortWarnings = (warnings: ConversionWarning[]): ConversionWarning[] => {
  return [...warnings].sort((a, b) => {
    const priorityA = calculateWarningPriority(a);
    const priorityB = calculateWarningPriority(b);
    return priorityB - priorityA;
  });
};
