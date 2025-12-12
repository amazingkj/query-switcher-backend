import { type AnalyticsEvent } from '../../utils/analytics';

// 탭 타입 정의
export type AnalyticsTabType = 'overview' | 'conversions' | 'events' | 'export';

// 탭 정보 인터페이스
export interface TabInfo {
  id: AnalyticsTabType;
  label: string;
  icon: string;
}

// 변환 통계 타입
export interface ConversionStats {
  totalConversions: number;
  successfulConversions: number;
  failedConversions: number;
  averageExecutionTime: number;
  dialectUsage: Record<string, number>;
  warningStats: {
    total: number;
    byType: Record<string, number>;
  };
}

// Props 타입들
export interface OverviewTabProps {
  conversionStats: ConversionStats;
  customEventsCount: number;
}

export interface ConversionsTabProps {
  conversionStats: ConversionStats;
}

export interface EventsTabProps {
  customEvents: AnalyticsEvent[];
}

export interface ExportTabProps {
  onExportData: () => void;
  onClearData: () => void;
}

// 탭 목록 상수
export const ANALYTICS_TABS: TabInfo[] = [
  { id: 'overview', label: '개요', icon: '📊' },
  { id: 'conversions', label: '변환 통계', icon: '🔄' },
  { id: 'events', label: '이벤트 로그', icon: '📝' },
  { id: 'export', label: '데이터 관리', icon: '💾' },
];