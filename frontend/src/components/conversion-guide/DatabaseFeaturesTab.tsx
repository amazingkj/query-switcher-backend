import React from 'react';
import type { DatabaseFeatureInfo } from './conversionGuideData';

interface DatabaseFeaturesTabProps {
  sourceFeatures: DatabaseFeatureInfo;
  targetFeatures: DatabaseFeatureInfo;
}

// 데이터베이스 특징 카드 컴포넌트
const DatabaseFeatureCard: React.FC<{
  features: DatabaseFeatureInfo;
  label?: string;
}> = ({ features }) => (
  <div className="border border-gray-200 dark:border-gray-600 rounded-lg p-4">
    <div className="flex items-center gap-2 mb-2">
      <div className={`w-3 h-3 rounded-full ${features.color}`}></div>
      <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100">
        {features.name}
      </h3>
      <span className="text-xs px-2 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 rounded">
        {features.version}
      </span>
    </div>
    <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
      {features.description}
    </p>
    <div className="space-y-4">
      <div>
        <h4 className="font-medium text-green-600 dark:text-green-400 mb-2 text-sm">
          ✅ 지원 기능
        </h4>
        <ul className="space-y-1">
          {features.features.map((feature, index) => (
            <li
              key={index}
              className="text-xs text-gray-600 dark:text-gray-300 flex items-center"
            >
              <span className="w-1.5 h-1.5 bg-green-500 rounded-full mr-2"></span>
              {feature}
            </li>
          ))}
        </ul>
      </div>
      <div>
        <h4 className="font-medium text-red-600 dark:text-red-400 mb-2 text-sm">
          ❌ 제한사항
        </h4>
        <ul className="space-y-1">
          {features.limitations.map((limitation, index) => (
            <li
              key={index}
              className="text-xs text-gray-600 dark:text-gray-300 flex items-center"
            >
              <span className="w-1.5 h-1.5 bg-red-500 rounded-full mr-2"></span>
              {limitation}
            </li>
          ))}
        </ul>
      </div>
    </div>
  </div>
);

export const DatabaseFeaturesTab: React.FC<DatabaseFeaturesTabProps> = ({
  sourceFeatures,
  targetFeatures,
}) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <DatabaseFeatureCard features={sourceFeatures} label="소스" />
      <DatabaseFeatureCard features={targetFeatures} label="타겟" />
    </div>
  );
};