import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { sqlExecutionApi } from '../services/api';
import type { DialectType, ConnectionStatus } from '../types';

interface DbStatusIndicatorProps {
  compact?: boolean;
}

export const DbStatusIndicator: React.FC<DbStatusIndicatorProps> = ({ compact = false }) => {
  const { data: statuses, isLoading } = useQuery({
    queryKey: ['dbConnections'],
    queryFn: sqlExecutionApi.checkAllConnections,
    refetchInterval: 30000, // 30초마다 재확인
    retry: 1
  });

  const dialects: DialectType[] = ['MYSQL', 'POSTGRESQL', 'ORACLE'];

  const getDialectInfo = (dialect: DialectType) => {
    switch (dialect) {
      case 'MYSQL':
        return { name: 'MySQL', color: 'bg-orange-500', short: 'MY' };
      case 'POSTGRESQL':
        return { name: 'PostgreSQL', color: 'bg-blue-600', short: 'PG' };
      case 'ORACLE':
        return { name: 'Oracle', color: 'bg-red-600', short: 'OR' };
      default:
        return { name: dialect, color: 'bg-gray-500', short: '??' };
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <svg className="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
        DB 확인 중...
      </div>
    );
  }

  if (!statuses) {
    return null;
  }

  const connectedCount = Object.values(statuses).filter(s => s.connected).length;

  if (compact) {
    return (
      <div className="flex items-center gap-1.5" title={`${connectedCount}/${dialects.length} DB 연결됨`}>
        {dialects.map((dialect) => {
          const info = getDialectInfo(dialect);
          const status = statuses[dialect];
          return (
            <div
              key={dialect}
              className={`w-2 h-2 rounded-full ${status?.connected ? info.color : 'bg-gray-300 dark:bg-gray-600'}`}
              title={`${info.name}: ${status?.connected ? '연결됨' : '연결 안됨'}`}
            />
          );
        })}
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500 dark:text-gray-400">테스트 DB:</span>
      <div className="flex items-center gap-1.5">
        {dialects.map((dialect) => {
          const info = getDialectInfo(dialect);
          const status = statuses[dialect];
          return (
            <div
              key={dialect}
              className={`flex items-center gap-1 px-1.5 py-0.5 rounded text-xs ${
                status?.connected
                  ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
                  : 'bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
              }`}
              title={status?.connected ? `${info.name} ${status.version || ''}` : `${info.name} 연결 안됨`}
            >
              <span className={`w-1.5 h-1.5 rounded-full ${status?.connected ? 'bg-green-500' : 'bg-gray-400'}`} />
              <span>{info.short}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
};