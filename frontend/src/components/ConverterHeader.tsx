import React from 'react';
import { ThemeToggle } from './ThemeToggle';

interface ConverterHeaderProps {
  isServerConnected: boolean;
}

/**
 * SQL 변환기 헤더 컴포넌트 - Modernize 스타일
 */
export const ConverterHeader: React.FC<ConverterHeaderProps> = ({ isServerConnected }) => {
  return (
    <div className="card p-6 mb-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="heading-2 mb-1">
            <span className="text-primary-500">SQL Query</span>{' '}
            <span className="text-dark dark:text-light">Switcher</span>
          </h1>
          <p className="subtitle">
            데이터베이스 간 SQL 쿼리를 쉽게 변환하세요
          </p>
        </div>
        <div className="flex items-center gap-3">
          <ThemeToggle />
          {isServerConnected && (
            <div className="badge badge-success">
              <span className="w-2 h-2 bg-success-500 rounded-full mr-2 animate-pulse" />
              서버 연결됨
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
