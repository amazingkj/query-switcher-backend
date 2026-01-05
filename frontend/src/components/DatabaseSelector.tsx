import React from 'react';
import { DialectType } from '../types';

interface DatabaseSelectorProps {
  sourceDialect: DialectType;
  targetDialect: DialectType;
  onSourceChange: (dialect: DialectType) => void;
  onTargetChange: (dialect: DialectType) => void;
}

const dialectOptions = [
  { value: DialectType.MYSQL, label: 'MySQL', color: 'bg-warning-500' },
  { value: DialectType.POSTGRESQL, label: 'PostgreSQL', color: 'bg-primary-500' },
  { value: DialectType.ORACLE, label: 'Oracle', color: 'bg-error-500' }
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
    <div className="card p-5">
      <div className="flex flex-col sm:flex-row gap-4 sm:gap-6 sm:items-end">
        {/* 소스 데이터베이스 */}
        <div className="flex-1">
          <label className="block text-sm font-semibold text-dark dark:text-light mb-2">
            소스 데이터베이스
          </label>
          <div className="relative">
            <select
              value={sourceDialect}
              onChange={(e) => onSourceChange(e.target.value as DialectType)}
              className="select-field pr-10"
            >
              {dialectOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-0 flex items-center px-3 pointer-events-none">
              <svg className="w-5 h-5 text-dark/40 dark:text-light/40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div className="mt-2 flex items-center gap-2">
            <span className={`w-2.5 h-2.5 rounded-full ${getDialectInfo(sourceDialect)?.color}`} />
            <span className="text-xs font-medium text-dark/60 dark:text-light/60">
              {getDialectInfo(sourceDialect)?.label}
            </span>
          </div>
        </div>

        {/* 화살표 */}
        <div className="flex items-center justify-center sm:pb-6">
          <div className="bg-primary-500 p-3 rounded-full shadow-button hover:shadow-button-hover hover:bg-primary-600 transition-all duration-normal cursor-pointer">
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
          <label className="block text-sm font-semibold text-dark dark:text-light mb-2">
            타겟 데이터베이스
          </label>
          <div className="relative">
            <select
              value={targetDialect}
              onChange={(e) => onTargetChange(e.target.value as DialectType)}
              className="select-field pr-10"
            >
              {dialectOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-0 flex items-center px-3 pointer-events-none">
              <svg className="w-5 h-5 text-dark/40 dark:text-light/40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div className="mt-2 flex items-center gap-2">
            <span className={`w-2.5 h-2.5 rounded-full ${getDialectInfo(targetDialect)?.color}`} />
            <span className="text-xs font-medium text-dark/60 dark:text-light/60">
              {getDialectInfo(targetDialect)?.label}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
