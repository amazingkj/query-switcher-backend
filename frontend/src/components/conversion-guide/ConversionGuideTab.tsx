import React from 'react';
import { DialectType } from '../../types';
import type {
  ConversionGuide,
  ConversionIssue,
  DatabaseFeatureInfo,
} from './conversionGuideData';
import { getCodeForDialect } from './conversionGuideData';

interface ConversionGuideTabProps {
  conversionGuide: ConversionGuide;
  sourceDialect: DialectType;
  targetDialect: DialectType;
  sourceFeatures: DatabaseFeatureInfo;
  targetFeatures: DatabaseFeatureInfo;
}

// 개별 변환 이슈 카드
const ConversionIssueCard: React.FC<{
  issue: ConversionIssue;
  sourceDialect: DialectType;
  targetDialect: DialectType;
  sourceName: string;
  targetName: string;
}> = ({ issue, sourceDialect, targetDialect, sourceName, targetName }) => {
  const sourceCode = getCodeForDialect(issue, sourceDialect);
  const targetCode = getCodeForDialect(issue, targetDialect);

  return (
    <div className="border border-gray-200 dark:border-gray-600 rounded-lg p-4">
      <h4 className="font-medium text-gray-800 dark:text-gray-100 mb-3">
        {issue.issue}
      </h4>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
        <div>
          <h5 className="text-sm font-medium text-red-600 dark:text-red-400 mb-2">
            원본 ({sourceName})
          </h5>
          <pre className="text-xs bg-red-50 dark:bg-red-900/30 dark:text-red-200 p-2 rounded border dark:border-red-800 overflow-x-auto">
            <code>{sourceCode}</code>
          </pre>
        </div>
        <div>
          <h5 className="text-sm font-medium text-green-600 dark:text-green-400 mb-2">
            변환 ({targetName})
          </h5>
          <pre className="text-xs bg-green-50 dark:bg-green-900/30 dark:text-green-200 p-2 rounded border dark:border-green-800 overflow-x-auto">
            <code>{targetCode}</code>
          </pre>
        </div>
      </div>
      <div className="text-sm text-gray-600 dark:text-gray-300 bg-blue-50 dark:bg-blue-900/30 p-2 rounded">
        <strong>💡 참고:</strong> {issue.note}
      </div>
    </div>
  );
};

export const ConversionGuideTab: React.FC<ConversionGuideTabProps> = ({
  conversionGuide,
  sourceDialect,
  targetDialect,
  sourceFeatures,
  targetFeatures,
}) => {
  return (
    <div>
      <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">
        {conversionGuide.title}
      </h3>
      <div className="space-y-4">
        {conversionGuide.commonIssues.map((issue, index) => (
          <ConversionIssueCard
            key={index}
            issue={issue}
            sourceDialect={sourceDialect}
            targetDialect={targetDialect}
            sourceName={sourceFeatures.name}
            targetName={targetFeatures.name}
          />
        ))}
      </div>
    </div>
  );
};