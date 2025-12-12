import React, { useState } from 'react';
import { DialectType } from '../types';
import {
  DATABASE_FEATURES,
  CONVERSION_GUIDES,
  DatabaseFeaturesTab,
  ConversionGuideTab,
  ConversionTipsTab,
} from './conversion-guide';

interface ConversionGuidePanelProps {
  isOpen: boolean;
  onClose: () => void;
  sourceDialect: DialectType;
  targetDialect: DialectType;
}

type TabType = 'features' | 'guide' | 'tips';

// 탭 버튼 컴포넌트
const TabButton: React.FC<{
  label: string;
  isActive: boolean;
  onClick: () => void;
}> = ({ label, isActive, onClick }) => (
  <button
    onClick={onClick}
    className={`px-4 py-2 text-sm font-medium ${
      isActive
        ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600'
        : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
    }`}
  >
    {label}
  </button>
);

// 닫기 버튼 아이콘
const CloseIcon: React.FC = () => (
  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M6 18L18 6M6 6l12 12"
    />
  </svg>
);

export const ConversionGuidePanel: React.FC<ConversionGuidePanelProps> = ({
  isOpen,
  onClose,
  sourceDialect,
  targetDialect,
}) => {
  const [activeTab, setActiveTab] = useState<TabType>('features');

  if (!isOpen) return null;

  const sourceFeatures = DATABASE_FEATURES[sourceDialect];
  const targetFeatures = DATABASE_FEATURES[targetDialect];
  const conversionGuide = CONVERSION_GUIDES[`${sourceDialect}_${targetDialect}`];

  const tabs: { key: TabType; label: string }[] = [
    { key: 'features', label: '데이터베이스 특징' },
    { key: 'guide', label: '변환 가이드' },
    { key: 'tips', label: '변환 팁' },
  ];

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-4xl w-full mx-4 max-h-[80vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-xl font-semibold text-gray-800 dark:text-gray-100">
            {sourceFeatures.name} → {targetFeatures.name} 변환 가이드
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
          >
            <CloseIcon />
          </button>
        </div>

        {/* 탭 네비게이션 */}
        <div className="flex border-b border-gray-200 dark:border-gray-700">
          {tabs.map((tab) => (
            <TabButton
              key={tab.key}
              label={tab.label}
              isActive={activeTab === tab.key}
              onClick={() => setActiveTab(tab.key)}
            />
          ))}
        </div>

        {/* 탭 내용 */}
        <div className="flex-1 overflow-y-auto p-4">
          {activeTab === 'features' && (
            <DatabaseFeaturesTab
              sourceFeatures={sourceFeatures}
              targetFeatures={targetFeatures}
            />
          )}

          {activeTab === 'guide' && conversionGuide && (
            <ConversionGuideTab
              conversionGuide={conversionGuide}
              sourceDialect={sourceDialect}
              targetDialect={targetDialect}
              sourceFeatures={sourceFeatures}
              targetFeatures={targetFeatures}
            />
          )}

          {activeTab === 'tips' && <ConversionTipsTab />}
        </div>
      </div>
    </div>
  );
};