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
  { value: DialectType.ORACLE, label: 'Oracle', color: 'bg-red-600' },
  { value: DialectType.TIBERO, label: 'Tibero', color: 'bg-purple-600' }
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
    <div className="database-selector bg-gray-50 border-2 border-gray-200 p-6">
      <div className="flex gap-6 items-center">
        {/* 소스 데이터베이스 */}
        <div className="flex-1">
          <label className="block text-sm font-semibold text-gray-800 mb-3">
            소스 데이터베이스
          </label>
          <div className="relative">
            <select
              value={sourceDialect}
              onChange={(e) => onSourceChange(e.target.value as DialectType)}
              className="w-full p-4 text-lg font-medium border-2 border-gray-300 bg-white hover:border-gray-400 focus:outline-none focus:border-blue-500 focus:bg-white transition-all duration-200 appearance-none cursor-pointer"
            >
              {dialectOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-0 flex items-center px-4 pointer-events-none">
              <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div className="mt-2 flex items-center gap-2">
            <div className={`w-3 h-3 ${getDialectInfo(sourceDialect)?.color} border border-gray-300`}></div>
            <span className="text-sm text-gray-600 font-medium">{getDialectInfo(sourceDialect)?.label}</span>
          </div>
        </div>

        {/* 화살표 - 더 크고 전문적으로 */}
        <div className="flex items-center justify-center mt-8">
          <div className="bg-blue-500 p-3 transition-all duration-200 hover:bg-blue-600">
            <svg
              className="w-8 h-8 text-white"
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
          <label className="block text-sm font-semibold text-gray-800 mb-3">
            타겟 데이터베이스
          </label>
          <div className="relative">
            <select
              value={targetDialect}
              onChange={(e) => onTargetChange(e.target.value as DialectType)}
              className="w-full p-4 text-lg font-medium border-2 border-gray-300 bg-white hover:border-gray-400 focus:outline-none focus:border-blue-500 focus:bg-white transition-all duration-200 appearance-none cursor-pointer"
            >
              {dialectOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-0 flex items-center px-4 pointer-events-none">
              <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div className="mt-2 flex items-center gap-2">
            <div className={`w-3 h-3 ${getDialectInfo(targetDialect)?.color} border border-gray-300`}></div>
            <span className="text-sm text-gray-600 font-medium">{getDialectInfo(targetDialect)?.label}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
