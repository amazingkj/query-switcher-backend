// Google Analytics 4 및 커스텀 분석 시스템
export interface AnalyticsEvent {
  action: string;
  category: string;
  label?: string;
  value?: number;
  custom_parameters?: Record<string, any>;
}

export interface ConversionEvent {
  sourceDialect: string;
  targetDialect: string;
  sqlLength: number;
  hasWarnings: boolean;
  warningCount: number;
  executionTime: number;
  success: boolean;
}

export interface UserBehaviorEvent {
  eventType: 'page_view' | 'sql_input' | 'dialect_change' | 'button_click' | 'feature_use';
  details: Record<string, any>;
}

class AnalyticsService {
  private isInitialized = false;
  private measurementId: string | null = null;
  private customEvents: AnalyticsEvent[] = [];
  private conversionStats = {
    totalConversions: 0,
    successfulConversions: 0,
    failedConversions: 0,
    averageExecutionTime: 0,
    dialectUsage: {} as Record<string, number>,
    warningStats: {
      total: 0,
      byType: {} as Record<string, number>,
      bySeverity: {} as Record<string, number>
    }
  };

  constructor() {
    this.measurementId = import.meta.env.VITE_GA_MEASUREMENT_ID || null;
  }

  // Google Analytics 4 초기화
  initialize(measurementId?: string) {
    if (measurementId) {
      this.measurementId = measurementId;
    }

    if (!this.measurementId) {
      console.warn('Google Analytics Measurement ID not provided');
      return;
    }

    // Google Analytics 스크립트 로드
    const script = document.createElement('script');
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${this.measurementId}`;
    document.head.appendChild(script);

    // gtag 초기화
    window.dataLayer = window.dataLayer || [];
    function gtag(...args: any[]) {
      window.dataLayer.push(args);
    }
    window.gtag = gtag;

    gtag('js', new Date());
    gtag('config', this.measurementId, {
      page_title: 'SQL Query Switcher',
      page_location: window.location.href,
      custom_map: {
        'custom_parameter_1': 'source_dialect',
        'custom_parameter_2': 'target_dialect',
        'custom_parameter_3': 'sql_length',
        'custom_parameter_4': 'has_warnings'
      }
    });

    this.isInitialized = true;
    console.log('Google Analytics 4 initialized');
  }

  // 페이지 뷰 추적
  trackPageView(pageName: string, pagePath?: string) {
    if (this.isInitialized && window.gtag) {
      window.gtag('config', this.measurementId!, {
        page_title: pageName,
        page_location: pagePath || window.location.href
      });
    }

    this.trackCustomEvent({
      action: 'page_view',
      category: 'navigation',
      label: pageName,
      custom_parameters: {
        page_path: pagePath || window.location.pathname,
        timestamp: Date.now()
      }
    });
  }

  // SQL 변환 이벤트 추적
  trackConversion(event: ConversionEvent) {
    if (this.isInitialized && window.gtag) {
      window.gtag('event', 'sql_conversion', {
        source_dialect: event.sourceDialect,
        target_dialect: event.targetDialect,
        sql_length: event.sqlLength,
        has_warnings: event.hasWarnings,
        warning_count: event.warningCount,
        execution_time: event.executionTime,
        success: event.success,
        conversion_id: `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
      });
    }

    // 커스텀 통계 업데이트
    this.updateConversionStats(event);

    this.trackCustomEvent({
      action: 'sql_conversion',
      category: 'conversion',
      label: `${event.sourceDialect}_to_${event.targetDialect}`,
      value: event.executionTime,
      custom_parameters: event
    });
  }

  // 사용자 행동 이벤트 추적
  trackUserBehavior(event: UserBehaviorEvent) {
    if (this.isInitialized && window.gtag) {
      window.gtag('event', event.eventType, {
        event_category: 'user_behavior',
        event_label: event.eventType,
        ...event.details
      });
    }

    this.trackCustomEvent({
      action: event.eventType,
      category: 'user_behavior',
      custom_parameters: event.details
    });
  }

  // 커스텀 이벤트 추적
  trackCustomEvent(event: AnalyticsEvent) {
    this.customEvents.push({
      ...event,
      custom_parameters: {
        ...event.custom_parameters,
        timestamp: Date.now(),
        user_agent: navigator.userAgent,
        screen_resolution: `${screen.width}x${screen.height}`,
        viewport_size: `${window.innerWidth}x${window.innerHeight}`
      }
    });

    // 로컬 스토리지에 저장 (최대 100개)
    this.saveCustomEvents();
  }

  // 변환 통계 업데이트
  private updateConversionStats(event: ConversionEvent) {
    this.conversionStats.totalConversions++;
    
    if (event.success) {
      this.conversionStats.successfulConversions++;
    } else {
      this.conversionStats.failedConversions++;
    }

    // 평균 실행 시간 계산
    const totalTime = this.conversionStats.averageExecutionTime * (this.conversionStats.totalConversions - 1);
    this.conversionStats.averageExecutionTime = (totalTime + event.executionTime) / this.conversionStats.totalConversions;

    // 방언 사용 통계
    const dialectKey = `${event.sourceDialect}_to_${event.targetDialect}`;
    this.conversionStats.dialectUsage[dialectKey] = (this.conversionStats.dialectUsage[dialectKey] || 0) + 1;

    // 경고 통계
    if (event.hasWarnings) {
      this.conversionStats.warningStats.total += event.warningCount;
    }
  }

  // 커스텀 이벤트를 로컬 스토리지에 저장
  private saveCustomEvents() {
    const maxEvents = 100;
    const eventsToSave = this.customEvents.slice(-maxEvents);
    
    try {
      localStorage.setItem('sql_converter_analytics', JSON.stringify(eventsToSave));
    } catch (error) {
      console.warn('Failed to save analytics events to localStorage:', error);
    }
  }

  // 로컬 스토리지에서 커스텀 이벤트 로드
  loadCustomEvents(): AnalyticsEvent[] {
    try {
      const stored = localStorage.getItem('sql_converter_analytics');
      if (stored) {
        this.customEvents = JSON.parse(stored);
        return this.customEvents;
      }
    } catch (error) {
      console.warn('Failed to load analytics events from localStorage:', error);
    }
    return [];
  }

  // 분석 데이터 내보내기
  exportAnalyticsData() {
    const data = {
      customEvents: this.customEvents,
      conversionStats: this.conversionStats,
      exportTimestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      screenResolution: `${screen.width}x${screen.height}`,
      viewportSize: `${window.innerWidth}x${window.innerHeight}`
    };

    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `sql_converter_analytics_${new Date().toISOString().split('T')[0]}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  // 통계 데이터 가져오기
  getConversionStats() {
    return { ...this.conversionStats };
  }

  // 커스텀 이벤트 가져오기
  getCustomEvents() {
    return [...this.customEvents];
  }

  // 분석 데이터 초기화
  clearAnalyticsData() {
    this.customEvents = [];
    this.conversionStats = {
      totalConversions: 0,
      successfulConversions: 0,
      failedConversions: 0,
      averageExecutionTime: 0,
      dialectUsage: {},
      warningStats: {
        total: 0,
        byType: {},
        bySeverity: {}
      }
    };
    
    try {
      localStorage.removeItem('sql_converter_analytics');
    } catch (error) {
      console.warn('Failed to clear analytics data from localStorage:', error);
    }
  }
}

// 전역 타입 선언
declare global {
  interface Window {
    dataLayer: any[];
    gtag: (...args: any[]) => void;
  }
}

// 싱글톤 인스턴스 생성
export const analytics = new AnalyticsService();

// 자동 초기화 (환경 변수가 있는 경우)
if (import.meta.env.VITE_GA_MEASUREMENT_ID) {
  analytics.initialize();
}
