import React from 'react';
import {
  StatCard,
  ChartIcon,
  CheckCircleIcon,
  ClockIcon,
  LightningIcon,
} from './StatCard';
import type { OverviewTabProps } from './types';

export const OverviewTab: React.FC<OverviewTabProps> = ({
  conversionStats,
  customEventsCount,
}) => {
  const successRate =
    conversionStats.totalConversions > 0
      ? (
          (conversionStats.successfulConversions /
            conversionStats.totalConversions) *
          100
        ).toFixed(1)
      : '0';

  const topDialectPair = Object.entries(conversionStats.dialectUsage).sort(
    ([, a], [, b]) => b - a
  )[0];

  return (
    <div className="space-y-6">
      {/* 주요 지표 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="총 변환"
          value={conversionStats.totalConversions}
          icon={<ChartIcon />}
          colorScheme="blue"
        />
        <StatCard
          title="성공률"
          value={`${successRate}%`}
          icon={<CheckCircleIcon />}
          colorScheme="green"
        />
        <StatCard
          title="평균 실행시간"
          value={`${conversionStats.averageExecutionTime.toFixed(0)}ms`}
          icon={<ClockIcon />}
          colorScheme="yellow"
        />
        <StatCard
          title="이벤트 수"
          value={customEventsCount}
          icon={<LightningIcon />}
          colorScheme="purple"
        />
      </div>

      {/* 인기 방언 조합 */}
      {topDialectPair && (
        <div className="bg-gray-50 p-4 rounded-lg">
          <h4 className="text-lg font-semibold text-gray-800 mb-2">
            가장 인기 있는 방언 조합
          </h4>
          <div className="flex items-center">
            <span className="text-2xl font-bold text-blue-600 mr-2">
              {topDialectPair[1]}
            </span>
            <span className="text-gray-600">번 사용된</span>
            <span className="ml-2 px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">
              {topDialectPair[0].replace('_to_', ' → ')}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};