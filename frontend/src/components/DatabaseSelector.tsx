import React from 'react';
import { DialectType } from '../types';

interface DatabaseSelectorProps {
  sourceDialect: DialectType;
  targetDialect: DialectType;
  onSourceChange: (dialect: DialectType) => void;
  onTargetChange: (dialect: DialectType) => void;
}

const dialectOptions = [
  { value: DialectType.MYSQL, label: 'MySQL', color: 'bg-orange-500' },
  { value: DialectType.POSTGRESQL, label: 'PostgreSQL', color: 'bg-blue-600' },
  { value: DialectType.ORACLE, label: 'Oracle', color: 'bg-red-600' }
];

export const DatabaseSelector: React.FC<DatabaseSelectorProps> = ({
  sourceDialect,
  targetDialect,
  onSourceChange,
  onTargetChange
}) => {
  const getDialectInfo = (dialect: DialectType) =>
    dialectOptions.find(option => option.value === dialect);

  return (
    <div className="database-selector bg-gray-50 border border-gray-200 rounded-lg p-3 sm:p-4">
      {/* 모바일: 세로 배치, 데스크톱: 가로 배치 */}
      <div className="flex flex-col sm:flex-row gap-3 sm:gap-4 sm:items-center">
        {/* 소스 데이터베이스 */}
        <div className="flex-1">
          <label className="block text-xs font-semibold text-gray-800 mb-1.5 sm:mb-2">
            소스 데이터베이스
          </label>
          <div className="relative">
            <select
              value={sourceDialect}
              onChange={(e) => onSourceChange(e.target.value as DialectType)}
              className="w-full p-2.5 sm:p-3 text-sm font-medium border border-gray-300 rounded-lg bg-white hover:border-gray-400 focus:outline-none focus:border-blue-500 focus:bg-white transition-all duration-200 appearance-none cursor-pointer"
            >
              {dialectOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-0 flex items-center px-3 pointer-events-none">
              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div className="mt-1 flex items-center gap-1">
            <div className={`w-2 h-2 rounded-full ${getDialectInfo(sourceDialect)?.color} border border-gray-300`}></div>
            <span className="text-xs text-gray-600 font-medium">{getDialectInfo(sourceDialect)?.label}</span>
          </div>
        </div>

        {/* 화살표 - 모바일에서는 아래 방향, 데스크톱에서는 오른쪽 방향 */}
        <div className="flex items-center justify-center sm:mt-6">
          <div className="bg-blue-500 p-2 rounded-lg transition-all duration-200 hover:bg-blue-600">
            {/* 모바일: 아래 화살표 */}
            <svg
              className="w-5 h-5 text-white sm:hidden"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M17 13l-5 5m0 0l-5-5m5 5V6"
              />
            </svg>
            {/* 데스크톱: 오른쪽 화살표 */}
            <svg
              className="w-5 h-5 text-white hidden sm:block"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M13 7l5 5m0 0l-5 5m5-5H6"
              />
            </svg>
          </div>
        </div>

        {/* 타겟 데이터베이스 */}
        <div className="flex-1">
          <label className="block text-xs font-semibold text-gray-800 mb-1.5 sm:mb-2">
            타겟 데이터베이스
          </label>
          <div className="relative">
            <select
              value={targetDialect}
              onChange={(e) => onTargetChange(e.target.value as DialectType)}
              className="w-full p-2.5 sm:p-3 text-sm font-medium border border-gray-300 rounded-lg bg-white hover:border-gray-400 focus:outline-none focus:border-blue-500 focus:bg-white transition-all duration-200 appearance-none cursor-pointer"
            >
              {dialectOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-0 flex items-center px-3 pointer-events-none">
              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div className="mt-1 flex items-center gap-1">
            <div className={`w-2 h-2 rounded-full ${getDialectInfo(targetDialect)?.color} border border-gray-300`}></div>
            <span className="text-xs text-gray-600 font-medium">{getDialectInfo(targetDialect)?.label}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
