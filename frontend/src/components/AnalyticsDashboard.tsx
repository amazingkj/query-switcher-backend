import React, { useState, useEffect } from 'react';
import { analytics, type AnalyticsEvent } from '../utils/analytics';

interface AnalyticsDashboardProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({
  isOpen,
  onClose
}) => {
  const [conversionStats, setConversionStats] = useState(analytics.getConversionStats());
  const [customEvents, setCustomEvents] = useState<AnalyticsEvent[]>([]);
  const [activeTab, setActiveTab] = useState<'overview' | 'conversions' | 'events' | 'export'>('overview');

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
    if (window.confirm('모든 분석 데이터를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) {
      analytics.clearAnalyticsData();
      setConversionStats(analytics.getConversionStats());
      setCustomEvents(analytics.getCustomEvents());
    }
  };

  if (!isOpen) return null;

  const successRate = conversionStats.totalConversions > 0 
    ? ((conversionStats.successfulConversions / conversionStats.totalConversions) * 100).toFixed(1)
    : '0';

  const topDialectPair = Object.entries(conversionStats.dialectUsage)
    .sort(([,a], [,b]) => b - a)[0];

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
          {[
            { id: 'overview', label: '개요', icon: '📊' },
            { id: 'conversions', label: '변환 통계', icon: '🔄' },
            { id: 'events', label: '이벤트 로그', icon: '📝' },
            { id: 'export', label: '데이터 관리', icon: '💾' }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
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
            <div className="space-y-6">
              {/* 주요 지표 */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
                  <div className="flex items-center">
                    <div className="p-2 bg-blue-100 rounded-lg">
                      <svg className="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                      </svg>
                    </div>
                    <div className="ml-4">
                      <p className="text-sm font-medium text-blue-600">총 변환</p>
                      <p className="text-2xl font-bold text-blue-900">{conversionStats.totalConversions}</p>
                    </div>
                  </div>
                </div>

                <div className="bg-green-50 p-4 rounded-lg border border-green-200">
                  <div className="flex items-center">
                    <div className="p-2 bg-green-100 rounded-lg">
                      <svg className="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                    </div>
                    <div className="ml-4">
                      <p className="text-sm font-medium text-green-600">성공률</p>
                      <p className="text-2xl font-bold text-green-900">{successRate}%</p>
                    </div>
                  </div>
                </div>

                <div className="bg-yellow-50 p-4 rounded-lg border border-yellow-200">
                  <div className="flex items-center">
                    <div className="p-2 bg-yellow-100 rounded-lg">
                      <svg className="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                      </svg>
                    </div>
                    <div className="ml-4">
                      <p className="text-sm font-medium text-yellow-600">평균 실행시간</p>
                      <p className="text-2xl font-bold text-yellow-900">{conversionStats.averageExecutionTime.toFixed(0)}ms</p>
                    </div>
                  </div>
                </div>

                <div className="bg-purple-50 p-4 rounded-lg border border-purple-200">
                  <div className="flex items-center">
                    <div className="p-2 bg-purple-100 rounded-lg">
                      <svg className="w-6 h-6 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                    </div>
                    <div className="ml-4">
                      <p className="text-sm font-medium text-purple-600">이벤트 수</p>
                      <p className="text-2xl font-bold text-purple-900">{customEvents.length}</p>
                    </div>
                  </div>
                </div>
              </div>

              {/* 인기 방언 조합 */}
              {topDialectPair && (
                <div className="bg-gray-50 p-4 rounded-lg">
                  <h4 className="text-lg font-semibold text-gray-800 mb-2">가장 인기 있는 방언 조합</h4>
                  <div className="flex items-center">
                    <span className="text-2xl font-bold text-blue-600 mr-2">{topDialectPair[1]}</span>
                    <span className="text-gray-600">번 사용된</span>
                    <span className="ml-2 px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">
                      {topDialectPair[0].replace('_to_', ' → ')}
                    </span>
                  </div>
                </div>
              )}
            </div>
          )}

          {activeTab === 'conversions' && (
            <div className="space-y-6">
              <h4 className="text-lg font-semibold text-gray-800">변환 통계</h4>
              
              {/* 방언 사용 통계 */}
              <div className="bg-white border border-gray-200 rounded-lg p-4">
                <h5 className="font-medium text-gray-700 mb-3">방언별 사용 통계</h5>
                <div className="space-y-2">
                  {Object.entries(conversionStats.dialectUsage)
                    .sort(([,a], [,b]) => b - a)
                    .map(([dialectPair, count]) => (
                      <div key={dialectPair} className="flex items-center justify-between">
                        <span className="text-sm text-gray-600">
                          {dialectPair.replace('_to_', ' → ')}
                        </span>
                        <div className="flex items-center">
                          <div className="w-32 bg-gray-200 rounded-full h-2 mr-3">
                            <div 
                              className="bg-blue-600 h-2 rounded-full" 
                              style={{ 
                                width: `${(count / Math.max(...Object.values(conversionStats.dialectUsage))) * 100}%` 
                              }}
                            ></div>
                          </div>
                          <span className="text-sm font-medium text-gray-800 w-8 text-right">{count}</span>
                        </div>
                      </div>
                    ))}
                </div>
              </div>

              {/* 경고 통계 */}
              <div className="bg-white border border-gray-200 rounded-lg p-4">
                <h5 className="font-medium text-gray-700 mb-3">경고 통계</h5>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-gray-600">총 경고 수</p>
                    <p className="text-2xl font-bold text-yellow-600">{conversionStats.warningStats.total}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">경고가 있는 변환</p>
                    <p className="text-2xl font-bold text-orange-600">
                      {conversionStats.totalConversions - conversionStats.successfulConversions}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'events' && (
            <div className="space-y-4">
              <h4 className="text-lg font-semibold text-gray-800">이벤트 로그</h4>
              <div className="bg-white border border-gray-200 rounded-lg max-h-96 overflow-y-auto">
                {customEvents.length === 0 ? (
                  <div className="p-8 text-center text-gray-500">
                    <svg className="w-12 h-12 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                    </svg>
                    <p>아직 기록된 이벤트가 없습니다.</p>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-200">
                    {customEvents.slice(-50).reverse().map((event, index) => (
                      <div key={index} className="p-4 hover:bg-gray-50">
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="text-sm font-medium text-gray-900">
                              {event.category} - {event.action}
                            </p>
                            {event.label && (
                              <p className="text-sm text-gray-600">{event.label}</p>
                            )}
                          </div>
                          <div className="text-right">
                            <p className="text-xs text-gray-500">
                              {new Date(event.custom_parameters?.timestamp || 0).toLocaleString()}
                            </p>
                            {event.value && (
                              <p className="text-sm font-medium text-blue-600">{event.value}ms</p>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === 'export' && (
            <div className="space-y-6">
              <h4 className="text-lg font-semibold text-gray-800">데이터 관리</h4>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-blue-50 p-6 rounded-lg border border-blue-200">
                  <h5 className="font-medium text-blue-800 mb-2">데이터 내보내기</h5>
                  <p className="text-sm text-blue-600 mb-4">
                    모든 분석 데이터를 JSON 파일로 내보냅니다.
                  </p>
                  <button
                    onClick={handleExportData}
                    className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                  >
                    데이터 내보내기
                  </button>
                </div>

                <div className="bg-red-50 p-6 rounded-lg border border-red-200">
                  <h5 className="font-medium text-red-800 mb-2">데이터 초기화</h5>
                  <p className="text-sm text-red-600 mb-4">
                    모든 분석 데이터를 삭제합니다. 이 작업은 되돌릴 수 없습니다.
                  </p>
                  <button
                    onClick={handleClearData}
                    className="w-full px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                  >
                    데이터 삭제
                  </button>
                </div>
              </div>

              <div className="bg-gray-50 p-4 rounded-lg">
                <h5 className="font-medium text-gray-700 mb-2">데이터 수집 정보</h5>
                <ul className="text-sm text-gray-600 space-y-1">
                  <li>• 사용자 행동 이벤트 (버튼 클릭, 페이지 뷰 등)</li>
                  <li>• SQL 변환 통계 (성공/실패, 실행 시간 등)</li>
                  <li>• 방언 사용 패턴</li>
                  <li>• 경고 및 오류 통계</li>
                  <li>• 브라우저 및 화면 해상도 정보</li>
                </ul>
                <p className="text-xs text-gray-500 mt-2">
                  모든 데이터는 로컬에 저장되며, Google Analytics가 설정된 경우에만 외부로 전송됩니다.
                </p>
              </div>
            </div>
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
