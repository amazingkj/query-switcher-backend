import { useEffect, useCallback } from 'react';
import { analytics, type ConversionEvent, type UserBehaviorEvent } from '../utils/analytics';

// Google Analytics 4 훅
export const useAnalytics = () => {
  // 페이지 뷰 추적
  const trackPageView = useCallback((pageName: string, pagePath?: string) => {
    analytics.trackPageView(pageName, pagePath);
  }, []);

  // SQL 변환 추적
  const trackConversion = useCallback((event: ConversionEvent) => {
    analytics.trackConversion(event);
  }, []);

  // 사용자 행동 추적
  const trackUserBehavior = useCallback((event: UserBehaviorEvent) => {
    analytics.trackUserBehavior(event);
  }, []);

  // SQL 입력 추적
  const trackSqlInput = useCallback((sqlLength: number, sourceDialect: string) => {
    trackUserBehavior({
      eventType: 'sql_input',
      details: {
        sql_length: sqlLength,
        source_dialect: sourceDialect,
        has_content: sqlLength > 0
      }
    });
  }, [trackUserBehavior]);

  // 방언 변경 추적
  const trackDialectChange = useCallback((sourceDialect: string, targetDialect: string) => {
    trackUserBehavior({
      eventType: 'dialect_change',
      details: {
        source_dialect: sourceDialect,
        target_dialect: targetDialect,
        change_type: 'dialect_selection'
      }
    });
  }, [trackUserBehavior]);

  // 버튼 클릭 추적
  const trackButtonClick = useCallback((buttonName: string, context?: Record<string, any>) => {
    trackUserBehavior({
      eventType: 'button_click',
      details: {
        button_name: buttonName,
        ...context
      }
    });
  }, [trackUserBehavior]);

  // 기능 사용 추적
  const trackFeatureUse = useCallback((featureName: string, details?: Record<string, any>) => {
    trackUserBehavior({
      eventType: 'feature_use',
      details: {
        feature_name: featureName,
        ...details
      }
    });
  }, [trackUserBehavior]);

  return {
    trackPageView,
    trackConversion,
    trackUserBehavior,
    trackSqlInput,
    trackDialectChange,
    trackButtonClick,
    trackFeatureUse
  };
};

// 페이지 뷰 자동 추적 훅
export const usePageTracking = (pageName: string, pagePath?: string) => {
  const { trackPageView } = useAnalytics();

  useEffect(() => {
    trackPageView(pageName, pagePath);
  }, [pageName, pagePath, trackPageView]);
};

// SQL 변환 추적 훅
export const useConversionTracking = () => {
  const { trackConversion } = useAnalytics();

  const trackSqlConversion = useCallback((
    sourceDialect: string,
    targetDialect: string,
    sqlLength: number,
    hasWarnings: boolean,
    warningCount: number,
    executionTime: number,
    success: boolean
  ) => {
    trackConversion({
      sourceDialect,
      targetDialect,
      sqlLength,
      hasWarnings,
      warningCount,
      executionTime,
      success
    });
  }, [trackConversion]);

  return { trackSqlConversion };
};

// 사용자 행동 추적 훅
export const useUserBehaviorTracking = () => {
  const {
    trackSqlInput,
    trackDialectChange,
    trackButtonClick,
    trackFeatureUse
  } = useAnalytics();

  return {
    trackSqlInput,
    trackDialectChange,
    trackButtonClick,
    trackFeatureUse
  };
};
