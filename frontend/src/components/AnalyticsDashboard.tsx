import React, { useState, useEffect } from 'react';
import { analytics, type AnalyticsEvent } from '../utils/analytics';
import {
  ANALYTICS_TABS,
  OverviewTab,
  ConversionsTab,
  EventsTab,
  ExportTab,
  type AnalyticsTabType,
  type ConversionStats,
} from './analytics-dashboard';

interface AnalyticsDashboardProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({
  isOpen,
  onClose,
}) => {
  const [conversionStats, setConversionStats] = useState<ConversionStats>(
    analytics.getConversionStats()
  );
  const [customEvents, setCustomEvents] = useState<AnalyticsEvent[]>([]);
  const [activeTab, setActiveTab] = useState<AnalyticsTabType>('overview');

  useEffect(() => {
    if (isOpen) {
      setConversionStats(analytics.getConversionStats());
      setCustomEvents(analytics.getCustomEvents());
    }
  }, [isOpen]);

  const handleExportData = () => {
    analytics.exportAnalyticsData();
  };

  const handleClearData = () => {
    if (
      window.confirm(
        '모든 분석 데이터를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.'
      )
    ) {
      analytics.clearAnalyticsData();
      setConversionStats(analytics.getConversionStats());
      setCustomEvents(analytics.getCustomEvents());
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50 flex justify-center items-center">
      <div className="bg-white rounded-lg shadow-xl p-6 w-11/12 max-w-6xl max-h-[90vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex justify-between items-center mb-6 border-b pb-4">
          <h3 className="text-2xl font-bold text-gray-800">분석 대시보드</h3>
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-gray-700 text-3xl leading-none"
          >
            &times;
          </button>
        </div>

        {/* 탭 네비게이션 */}
        <div className="flex space-x-1 mb-6 bg-gray-100 p-1 rounded-lg">
          {ANALYTICS_TABS.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 px-4 py-2 text-sm font-medium rounded-md transition-all ${
                activeTab === tab.id
                  ? 'bg-white text-blue-600 shadow-sm'
                  : 'text-gray-600 hover:text-gray-800'
              }`}
            >
              <span className="mr-2">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </div>

        {/* 탭 콘텐츠 */}
        <div className="flex-grow overflow-y-auto">
          {activeTab === 'overview' && (
            <OverviewTab
              conversionStats={conversionStats}
              customEventsCount={customEvents.length}
            />
          )}

          {activeTab === 'conversions' && (
            <ConversionsTab conversionStats={conversionStats} />
          )}

          {activeTab === 'events' && <EventsTab customEvents={customEvents} />}

          {activeTab === 'export' && (
            <ExportTab
              onExportData={handleExportData}
              onClearData={handleClearData}
            />
          )}
        </div>

        {/* 푸터 */}
        <div className="mt-6 flex justify-end">
          <button
            onClick={onClose}
            className="px-6 py-2 bg-gray-300 text-gray-800 rounded-lg hover:bg-gray-400 transition-colors"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};