import React from 'react';
import { ThemeToggle } from './ThemeToggle';

interface ConverterHeaderProps {
  isServerConnected: boolean;
}

/**
 * SQL 변환기 헤더 컴포넌트
 */
export const ConverterHeader: React.FC<ConverterHeaderProps> = ({ isServerConnected }) => {
  return (
    <div className="mb-6 text-center sm:text-left">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-gray-900 dark:text-white mb-2">
            <span className="bg-gradient-to-r from-blue-600 to-purple-600 dark:from-blue-400 dark:to-purple-400 bg-clip-text text-transparent">
              SQL Query Switcher
            </span>
          </h1>
          <p className="text-gray-600 dark:text-gray-400 text-sm sm:text-base">
            데이터베이스 간 SQL 쿼리를 쉽게 변환하세요
          </p>
        </div>
        <div className="mt-4 sm:mt-0 flex items-center justify-center sm:justify-end gap-3">
          <ThemeToggle />
          {isServerConnected && (
            <div className="flex items-center px-3 py-1 bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400 rounded-full text-sm">
              <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
              서버 연결됨
            </div>
          )}
        </div>
      </div>
    </div>
  );
};