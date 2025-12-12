import React from 'react';
import type { ConversionsTabProps } from './types';

export const ConversionsTab: React.FC<ConversionsTabProps> = ({
  conversionStats,
}) => {
  const maxUsage = Math.max(...Object.values(conversionStats.dialectUsage), 1);

  return (
    <div className="space-y-6">
      <h4 className="text-lg font-semibold text-gray-800">변환 통계</h4>

      {/* 방언 사용 통계 */}
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <h5 className="font-medium text-gray-700 mb-3">방언별 사용 통계</h5>
        <div className="space-y-2">
          {Object.entries(conversionStats.dialectUsage)
            .sort(([, a], [, b]) => b - a)
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
                        width: `${(count / maxUsage) * 100}%`,
                      }}
                    ></div>
                  </div>
                  <span className="text-sm font-medium text-gray-800 w-8 text-right">
                    {count}
                  </span>
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
            <p className="text-2xl font-bold text-yellow-600">
              {conversionStats.warningStats.total}
            </p>
          </div>
          <div>
            <p className="text-sm text-gray-600">경고가 있는 변환</p>
            <p className="text-2xl font-bold text-orange-600">
              {conversionStats.totalConversions -
                conversionStats.successfulConversions}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};